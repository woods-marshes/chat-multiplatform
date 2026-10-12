package com.github.woodsmarshes.chat.core.data.repository

import com.github.woodsmarshes.chat.core.database.dao.ConversationDao
import com.github.woodsmarshes.chat.core.network.api.rest.ConversationApi
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlin.uuid.Uuid

/**
 * Coordinates server-authoritative unread count updates and per-conversation
 * detail refreshes across [OfflineFirstMessageRepositoryImpl] and
 * [ConversationRepositoryImpl].
 *
 * Every unread-affecting event (`Received`, `Withdrawn`, `Read`, or a sent
 * local `markAsRead` receipt) advances a monotonic generation counter for that
 * conversation (and a never-reset global epoch) and schedules a coalesced
 * authoritative detail refresh (`conversationApi.getDetail`).
 *
 * Full-list snapshots (`syncConversations`) capture a [ListSnapshotToken] tied
 * to both the active session fence and a newly allocated `++globalEpoch` when
 * the request starts. Failed list requests never advance any applied state;
 * successful list snapshots and detail refreshes record the applied generation
 * per conversation (and detail apply also advances `globalEpoch`) so an older
 * list response can never overwrite a newer list response or a newer detail
 * refresh.
 */
class UnreadRefreshCoordinator {
    private val log = KotlinLogging.logger {}
    private val mutex = Mutex()

    /** Limits concurrent detail REST calls across conversations to prevent request storms. */
    private val fetchConcurrency = Semaphore(MAX_CONCURRENT_DETAIL_FETCHES)

    private var sessionEpoch = 0L
    private var globalEpoch = 0L
    private var sessionFloorEpoch = 0L
    private val requestedGenerations = mutableMapOf<Uuid, Long>()
    private val appliedGenerations = mutableMapOf<Uuid, Long>()
    private val dirtyConversations = mutableSetOf<Uuid>()
    private val refreshJobs = mutableMapOf<Uuid, Job>()
    private var pendingListRetry = false

    private companion object {
        const val MAX_CONCURRENT_DETAIL_FETCHES = 4
    }

    data class ListSnapshotToken(
        val sessionEpoch: Long,
        val capturedGeneration: Long,
    )

    /**
     * Allocates a new monotonic generation token tied to the current session
     * fence right before issuing a full conversation list fetch
     * (`userApi.getMyConversations()`). If the network call fails,
     * [applyListSnapshot] is never called so no per-conversation applied
     * state is falsely advanced.
     */
    suspend fun captureListGeneration(): ListSnapshotToken = mutex.withLock {
        ListSnapshotToken(
            sessionEpoch = sessionEpoch,
            capturedGeneration = ++globalEpoch,
        )
    }

    /**
     * Applies unread counts from a conversation list snapshot only when the
     * [token] belongs to the current session and only for conversations whose
     * requested and applied generations are no newer than
     * [ListSnapshotToken.capturedGeneration] and that do not have a pending
     * detail refresh. Records [ListSnapshotToken.capturedGeneration] as the
     * conversation's applied generation on success.
     */
    suspend fun applyListSnapshot(
        token: ListSnapshotToken,
        entries: List<Pair<Uuid, Long>>,
        conversationDao: ConversationDao,
    ) = mutex.withLock {
        if (token.sessionEpoch != sessionEpoch || token.capturedGeneration < sessionFloorEpoch) {
            return@withLock
        }
        for ((conversationId, unreadCount) in entries) {
            val reqGen = requestedGenerations[conversationId] ?: sessionFloorEpoch
            val appGen = appliedGenerations[conversationId] ?: sessionFloorEpoch
            if (token.capturedGeneration >= reqGen &&
                token.capturedGeneration > appGen &&
                conversationId !in dirtyConversations
            ) {
                conversationDao.setUnreadCount(conversationId, unreadCount)
                appliedGenerations[conversationId] = token.capturedGeneration
            }
        }
        pendingListRetry = false
    }

    /**
     * Marks [conversationId] as needing an authoritative unread refresh from
     * `conversationApi.getDetail(conversationId)` and schedules a coalesced
     * worker in [sessionScope]. Network I/O runs outside any repository or
     * coordinator lock (bounded by [fetchConcurrency]) so realtime event
     * consumption is never blocked.
     */
    suspend fun requestRefresh(
        sessionScope: CoroutineScope,
        conversationId: Uuid,
        conversationApi: ConversationApi,
        conversationDao: ConversationDao,
    ) {
        mutex.withLock {
            val next = ++globalEpoch
            requestedGenerations[conversationId] = next
            dirtyConversations.add(conversationId)
            ensureWorkerLocked(sessionScope, conversationId, conversationApi, conversationDao)
        }
    }

    /**
     * Marks that a reconnect anchor's `syncConversations()` call failed so
     * periodic/reconnect retry paths know the list snapshot is still owed.
     */
    suspend fun markListSyncFailed() = mutex.withLock {
        pendingListRetry = true
    }

    /**
     * Retries any failed list sync and any conversation detail refreshes that
     * failed on an earlier attempt.
     */
    suspend fun retryPending(
        sessionScope: CoroutineScope,
        conversationRepository: ConversationRepository,
        conversationApi: ConversationApi,
        conversationDao: ConversationDao,
    ) {
        val shouldRetryList = mutex.withLock { pendingListRetry }
        if (shouldRetryList) {
            val res = conversationRepository.syncConversations()
            if (res.isOk) {
                mutex.withLock { pendingListRetry = false }
            }
        }
        mutex.withLock {
            for (conversationId in dirtyConversations.toList()) {
                ensureWorkerLocked(sessionScope, conversationId, conversationApi, conversationDao)
            }
        }
    }

    /**
     * Advances the session fence and clears per-conversation state when the
     * message session stops (called after `session.job.join()`). Never resets
     * [globalEpoch] to zero so late list tokens from a previous session are
     * deterministically rejected.
     */
    suspend fun clearSessionState() = mutex.withLock {
        sessionEpoch++
        sessionFloorEpoch = ++globalEpoch
        refreshJobs.clear()
        dirtyConversations.clear()
        requestedGenerations.clear()
        appliedGenerations.clear()
        pendingListRetry = false
    }

    private fun ensureWorkerLocked(
        sessionScope: CoroutineScope,
        conversationId: Uuid,
        conversationApi: ConversationApi,
        conversationDao: ConversationDao,
    ) {
        if (refreshJobs[conversationId]?.isActive == true) return
        val job = sessionScope.launch {
            runRefreshLoop(conversationId, conversationApi, conversationDao)
        }
        refreshJobs[conversationId] = job
    }

    private suspend fun runRefreshLoop(
        conversationId: Uuid,
        conversationApi: ConversationApi,
        conversationDao: ConversationDao,
    ) {
        while (true) {
            currentCoroutineContext().ensureActive()
            val (targetSession, targetGen) = mutex.withLock {
                if (conversationId !in dirtyConversations) {
                    refreshJobs.remove(conversationId)
                    return
                }
                sessionEpoch to (requestedGenerations[conversationId] ?: sessionFloorEpoch)
            }

            val detail = try {
                fetchConcurrency.withPermit {
                    // Re-read the latest generation right before the HTTP call so
                    // events that arrived while waiting for a concurrency permit
                    // are covered by this single request instead of forcing an
                    // extra round-trip.
                    val latestGenBeforeFetch = mutex.withLock {
                        if (sessionEpoch != targetSession || conversationId !in dirtyConversations) {
                            refreshJobs.remove(conversationId)
                            return
                        }
                        requestedGenerations[conversationId] ?: targetGen
                    }
                    latestGenBeforeFetch to conversationApi.getDetail(conversationId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn(e) { "[unread-refresh] failed to fetch detail for $conversationId; will retry" }
                mutex.withLock {
                    // Leave conversationId in dirtyConversations so retryPending or the next event retries it.
                    refreshJobs.remove(conversationId)
                }
                return
            }

            val (fetchedGen, response) = detail
            val done = mutex.withLock {
                if (sessionEpoch != targetSession) {
                    refreshJobs.remove(conversationId)
                    return@withLock true
                }
                val currentReqGen = requestedGenerations[conversationId] ?: sessionFloorEpoch
                if (currentReqGen == fetchedGen && conversationId in dirtyConversations) {
                    conversationDao.setUnreadCount(conversationId, response.unreadCount.toLong())
                    val appliedGen = ++globalEpoch
                    requestedGenerations[conversationId] = appliedGen
                    appliedGenerations[conversationId] = appliedGen
                    dirtyConversations.remove(conversationId)
                    refreshJobs.remove(conversationId)
                    true
                } else if (conversationId !in dirtyConversations) {
                    refreshJobs.remove(conversationId)
                    true
                } else {
                    // Another unread-affecting event arrived while getDetail was in flight; re-fetch.
                    false
                }
            }
            if (done) return
        }
    }
}
