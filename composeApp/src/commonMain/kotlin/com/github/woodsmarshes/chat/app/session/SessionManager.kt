package com.github.woodsmarshes.chat.app.session

import com.github.woodsmarshes.chat.core.data.model.toUserEntity
import com.github.woodsmarshes.chat.core.data.repository.AuthRepository
import com.github.woodsmarshes.chat.core.data.repository.ContactRepository
import com.github.woodsmarshes.chat.core.data.repository.MessageRepository
import com.github.woodsmarshes.chat.core.data.repository.UserRepository
import com.github.woodsmarshes.chat.core.database.di.DatabaseHolder
import com.github.woodsmarshes.chat.core.datastore.UserSettingDataSource
import com.github.woodsmarshes.chat.core.model.AuthSessionSnapshot
import com.github.woodsmarshes.chat.core.model.ConnectionState
import com.github.woodsmarshes.chat.core.model.Contact
import com.github.woodsmarshes.chat.core.model.ContactRequest
import com.github.woodsmarshes.chat.core.model.PrivacySetting
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.UserPreference
import com.github.woodsmarshes.chat.core.model.UserSetting
import com.github.woodsmarshes.chat.core.model.error.ContactError
import com.github.woodsmarshes.chat.core.model.error.UserError
import com.github.woodsmarshes.chat.core.network.api.websocket.RealtimeApi
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.dsl.module
import kotlin.uuid.Uuid

val sessionModule = module {
    single(createdAtStart = true) { SessionManager(get(), get(), get(), get(), get(), get(), get()) }
}

/**
 * Explicit lifecycle state of the authenticated application session.
 *
 * Persisted credentials alone do not make the session [Active]: the per-user
 * database and the message repository's session workers must be ready, and the
 * realtime connection loop must be started. (Actual WebSocket handshake and
 * online/offline status are tracked independently via [SessionManager.connectionState]
 * so the app remains usable offline.) If any startup or teardown step fails,
 * the state enters [StartFailed] rather than leaving the UI on a
 * half-initialized screen or switching databases while old workers may still run.
 */
sealed interface SessionState {
    /** Initial persisted auth/user state has not been evaluated yet. */
    data object Initializing : SessionState

    /** No active session and no startup in progress. */
    data object Idle : SessionState

    /** Opening DB, starting message workers, and starting the realtime loop for [userId] at [generation]. */
    data class Starting(val userId: Uuid, val generation: Long) : SessionState

    /**
     * Local session environment for [userId] at [generation] is ready (DB open,
     * message consumers/executor active, realtime connection loop started); UI
     * may enter authenticated screens.
     */
    data class Active(val userId: Uuid, val generation: Long) : SessionState

    /**
     * Startup or prior-session teardown for [userId] at [generation] failed;
     * recoverable via [SessionManager.retrySessionStart] or re-auth.
     */
    data class StartFailed(val userId: Uuid, val generation: Long, val cause: Throwable) : SessionState

    /** Tearing down producer, session workers, and database for [userId] at transition [generation]. */
    data class Stopping(val userId: Uuid?, val generation: Long) : SessionState
}

/**
 * Coordinates the session lifecycle across observed auth state, the per-user
 * database, the message repository's session workers, and the realtime socket.
 *
 * Startup order on login:
 *  1. Open the per-user database (`databaseHolder.getOrCreateDatabase(userId, sessionGen)`)
 *     and seed the authenticated user's record into that explicitly bound
 *     database (`db.usersQueries.upsertUser(user.toUserEntity())`).
 *  2. Start the message session (`messageRepository.startSession`), which
 *     suspends until its realtime event and connection-state subscribers are
 *     registered and its `SessionExecutor` accepts public message operations.
 *  3. Start the realtime connection loop (`realtimeApi.connect`).
 *  4. Re-verify that the authoritative target user and credential generation
 *     have not logged out or switched while startup was suspended; if still
 *     valid, publish [SessionState.Active].
 *
 * Teardown order on logout / session loss:
 *  1. Publish [SessionState.Stopping] so [isLoggedIn] flips to `false` immediately.
 *  2. Stop the realtime producer (`realtimeApi.disconnect`) and wait for its
 *     reader and socket cleanup to finish.
 *  3. Stop the message session (`messageRepository.stopSession`) and wait for
 *     in-flight public message operations and background workers to exit and
 *     clear in-memory session state.
 *  4. Only if both producer and consumer teardown succeeded, release or switch
 *     the database (using the database's own `resourceGeneration`, while
 *     checking `stopTransitionGen` to verify no newer transition superseded the
 *     delayed close). Both explicit [logout] and passive auth-loss teardown use
 *     the same generation-guarded delayed close (the database stop barrier).
 *     The database session barrier cancels and awaits query observers, paging loads,
 *     and remaining repository operations before the driver is closed.
 *
 * Note on scope boundaries: [transitionMutex] and [currentGeneration] serialize
 * session transitions within [SessionManager] and guard against stale boolean
 * emissions or a previous logout's delayed database close clobbering a
 * same-account fast re-login. They do NOT by themselves serialize
 * `AuthRepository` login/refresh writes or bind non-message repository /
 * Paging observers, which are tracked separately.
 */
class SessionManager(
    private val authRepository: AuthRepository,
    private val userSettingDataSource: UserSettingDataSource,
    private val realtimeApi: RealtimeApi,
    private val databaseHolder: DatabaseHolder,
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository = InertUserRepository,
    private val contactRepository: ContactRepository = InertContactRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val log = KotlinLogging.logger {}
    private val transitionMutex = Mutex()
    private var currentGeneration = 0L

    /** The `(userId, sessionGen)` currently bound to [databaseHolder], if any. */
    private var boundDatabaseIdentity: Pair<Uuid, Long>? = null

    /**
     * The persisted credential generation (`AuthSessionSnapshot.generation`)
     * for which the currently [SessionState.Active] session was started, so a
     * same-account re-login (which advances `credentialGeneration` while
     * keeping `userId` unchanged) is recognized and reconciled into a fresh
     * session generation.
     */
    private var activeCredentialGeneration: Long? = null

    /**
     * Tracks whether a previous startup rollback, worker teardown, or database
     * close failed so recovery from [SessionState.StartFailed] must complete
     * cleanup before starting a new session.
     */
    private var pendingCleanupRequired: Boolean = false

    /**
     * Records the authoritative target (`null` when tearing down into
     * logged-out state) for which the current [SessionState.StartFailed] was
     * entered, so duplicate emissions for the same target do not auto-retry
     * while target changes (including `null -> userId` re-login or a
     * same-account new `credentialGeneration`) do trigger reconciliation.
     */
    private var hasFailedTarget: Boolean = false
    private var failedTarget: DesiredSession? = null
    private var pendingDelayedCloseJob: Job? = null

    private class DesiredSession(
        val user: User,
        val credentialGeneration: Long,
    ) {
        val userId: Uuid
            get() = user.id

        override fun equals(other: Any?): Boolean =
            other is DesiredSession &&
                userId == other.userId &&
                credentialGeneration == other.credentialGeneration

        override fun hashCode(): Int =
            31 * userId.hashCode() + credentialGeneration.hashCode()

        override fun toString(): String =
            "DesiredSession(userId=$userId, credentialGeneration=$credentialGeneration)"
    }

    private val authSessionFlow: Flow<AuthSessionSnapshot> =
        runCatching { authRepository.observeAuthSession() }.getOrNull()
            ?: combine(
                authRepository.observeIsLoggedIn(),
                userSettingDataSource.user,
            ) { loggedIn, user ->
                AuthSessionSnapshot(
                    generation = 0L,
                    user = if (loggedIn) user else null,
                    jwtToken = if (loggedIn && user != null) "<legacy-mock>" else null,
                )
            }

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Initializing)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    /**
     * Driven by [sessionState] readiness rather than raw token presence:
     * `null` while [SessionState.Initializing] or [SessionState.Starting],
     * `true` only when [SessionState.Active], and `false` when [SessionState.Idle],
     * [SessionState.Stopping], or [SessionState.StartFailed].
     */
    val isLoggedIn: StateFlow<Boolean?> = _sessionState
        .map { state ->
            when (state) {
                SessionState.Initializing,
                is SessionState.Starting -> null
                is SessionState.Active -> true
                SessionState.Idle,
                is SessionState.Stopping,
                is SessionState.StartFailed -> false
            }
        }
        .stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = null)

    /** Cached user of the current session (for the navigation rail avatar). */
    val currentUser: StateFlow<User?> = userSettingDataSource.user
        .stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = null)

    val connectionState: StateFlow<ConnectionState> = realtimeApi.connectionState

    init {
        scope.launch {
            // Observe the unified auth session snapshot as a trigger, and
            // always read the authoritative target inside transitionMutex so a
            // stale emission queued before a new login cannot tear down the
            // newly active session.
            authSessionFlow.collect {
                try {
                    reconcileNow(forceRetryAfterFailure = false)
                } catch (e: CancellationException) {
                    currentCoroutineContext().ensureActive()
                }
            }
        }
    }

    /**
     * Retries session initialization or failed teardown cleanup after a
     * [SessionState.StartFailed] without requiring the user to clear and
     * re-enter credentials.
     */
    fun retrySessionStart() {
        scope.launch {
            reconcileNow(forceRetryAfterFailure = true)
        }
    }

    internal suspend fun reconcileNow(forceRetryAfterFailure: Boolean = false) {
        transitionMutex.withLock {
            reconcileLocked(forceRetryAfterFailure = forceRetryAfterFailure)
        }
    }

    fun logout() {
        scope.launch {
            transitionMutex.withLock {
                val dbIdentity = boundDatabaseIdentity
                val fallbackUserId = dbIdentity?.first ?: authSessionFlow.first().user?.id ?: userSettingDataSource.user.first()?.id
                val stopTransitionGen = ++currentGeneration
                activeCredentialGeneration = null
                _sessionState.value = SessionState.Stopping(fallbackUserId, stopTransitionGen)

                val teardownError = stopWorkersSafely()
                // Best-effort credential revocation/clear: server logout failure
                // must not trap the user in a logged-in local state.
                withContext(NonCancellable) {
                    runCatching { authRepository.logout() }
                        .onFailure { log.warn(it) { "[SessionManager] auth logout failed" } }
                }

                if (teardownError != null) {
                    // Old workers could not be confirmed stopped: do NOT close
                    // the database or start a new session over it.
                    pendingCleanupRequired = true
                    val failedUser = fallbackUserId ?: Uuid.NIL
                    recordStartFailedLocked(
                        userId = failedUser,
                        generation = stopTransitionGen,
                        cause = teardownError,
                        targetSession = desiredSession(),
                    )
                    return@withLock
                }

                val desiredNow = desiredSession()
                if (desiredNow != null) {
                    if (dbIdentity != null && dbIdentity.first != desiredNow.userId) {
                        try {
                            closeBoundDatabaseLocked(dbIdentity.first, dbIdentity.second)
                        } catch (t: Throwable) {
                            if (t is CancellationException) throw t
                            log.error(t) { "[SessionManager] closing previous user DB failed during logout" }
                            pendingCleanupRequired = true
                            recordStartFailedLocked(
                                userId = desiredNow.userId,
                                generation = stopTransitionGen,
                                cause = t,
                                targetSession = desiredNow,
                            )
                            return@withLock
                        }
                    }
                    pendingCleanupRequired = false
                    clearFailedTargetLocked()
                    _sessionState.value = SessionState.Idle
                    reconcileLocked(forceRetryAfterFailure = false)
                } else {
                    pendingCleanupRequired = false
                    clearFailedTargetLocked()
                    _sessionState.value = SessionState.Idle
                    if (dbIdentity != null) {
                        scheduleDelayedDatabaseCloseLocked(
                            DelayedDatabaseClose(
                                userId = dbIdentity.first,
                                resourceGeneration = dbIdentity.second,
                                stopTransitionGeneration = stopTransitionGen,
                            )
                        )
                    } else if (fallbackUserId != null) {
                        scheduleDelayedDatabaseCloseLocked(
                            DelayedDatabaseClose(
                                userId = fallbackUserId,
                                resourceGeneration = null,
                                stopTransitionGeneration = stopTransitionGen,
                            )
                        )
                    }
                }
            }
        }
    }

    private data class DelayedDatabaseClose(
        val userId: Uuid,
        val resourceGeneration: Long?,
        val stopTransitionGeneration: Long,
    )

    private fun recordStartFailedLocked(
        userId: Uuid,
        generation: Long,
        cause: Throwable,
        targetSession: DesiredSession?,
    ) {
        activeCredentialGeneration = null
        hasFailedTarget = true
        failedTarget = targetSession
        _sessionState.value = SessionState.StartFailed(userId, generation, cause)
    }

    private fun clearFailedTargetLocked() {
        hasFailedTarget = false
        failedTarget = null
    }

    private fun hasFailedForTarget(targetSession: DesiredSession?): Boolean =
        hasFailedTarget && failedTarget == targetSession

    /**
     * Queues the final identity-checked close after the database stop barrier.
     * No wall-clock delay or UI disposal timing is used as a safety condition.
     * Must be called while holding [transitionMutex].
     */
    private fun scheduleDelayedDatabaseCloseLocked(closeReq: DelayedDatabaseClose) {
        pendingDelayedCloseJob?.cancel()
        pendingDelayedCloseJob = scope.launch {
            transitionMutex.withLock {
                if (currentGeneration == closeReq.stopTransitionGeneration &&
                    _sessionState.value is SessionState.Idle
                ) {
                    try {
                        closeBoundDatabaseLocked(closeReq.userId, closeReq.resourceGeneration)
                        pendingCleanupRequired = false
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                        log.error(t) {
                            "[SessionManager] delayed database close failed for user=${closeReq.userId} gen=${closeReq.resourceGeneration}"
                        }
                        pendingCleanupRequired = true
                        recordStartFailedLocked(
                            userId = closeReq.userId,
                            generation = closeReq.stopTransitionGeneration,
                            cause = t,
                            targetSession = null,
                        )
                    }
                }
            }
        }
    }

    private suspend fun desiredSession(): DesiredSession? {
        val snapshot = authSessionFlow.first()
        if (!snapshot.isLoggedIn) return null
        val user = snapshot.user ?: return null
        return DesiredSession(user = user, credentialGeneration = snapshot.generation)
    }

    /**
     * Stops the realtime producer and then the session-bound repositories under
     * [NonCancellable]. All steps are attempted even if an earlier one throws;
     * returns the first failure (with any subsequent failures attached as
     * suppressed), or `null` when all confirmed stopped.
     */
    private suspend fun stopWorkersSafely(): Throwable? = withContext(NonCancellable) {
        val disconnectErr = runCatching { realtimeApi.disconnect() }
            .onFailure { log.error(it) { "[SessionManager] realtime disconnect failed" } }
            .exceptionOrNull()
        val stopMsgErr = runCatching { messageRepository.stopSession() }
            .onFailure { log.error(it) { "[SessionManager] message stopSession failed" } }
            .exceptionOrNull()
        val stopContactErr = runCatching { contactRepository.stopSession() }
            .onFailure { log.error(it) { "[SessionManager] contact stopSession failed" } }
            .exceptionOrNull()
        val stopUserErr = runCatching { userRepository.stopSession() }
            .onFailure { log.error(it) { "[SessionManager] user stopSession failed" } }
            .exceptionOrNull()
        val stopDatabaseErr = runCatching { databaseHolder.stopDatabaseSession() }
            .onFailure { log.error(it) { "[SessionManager] database readers and operations failed to stop" } }
            .exceptionOrNull()
        combineErrors(disconnectErr, stopMsgErr, stopContactErr, stopUserErr, stopDatabaseErr)
    }

    /** Must be called while holding [transitionMutex]. */
    private suspend fun closeBoundDatabaseLocked(userId: Uuid, resourceGeneration: Long?) {
        databaseHolder.closeDatabaseIfCurrent(userId, resourceGeneration)
        val currentBound = boundDatabaseIdentity
        if (currentBound != null &&
            currentBound.first == userId &&
            (resourceGeneration == null || currentBound.second == resourceGeneration)
        ) {
            boundDatabaseIdentity = null
        }
    }

    /** Must be called while holding [transitionMutex]. */
    private suspend fun reconcileLocked(forceRetryAfterFailure: Boolean) {
        while (true) {
            val desired = desiredSession()
            val current = _sessionState.value

            if (desired == null) {
                when (current) {
                    is SessionState.Active -> {
                        val dbIdentity = boundDatabaseIdentity
                        val resourceGen = dbIdentity?.takeIf { it.first == current.userId }?.second ?: current.generation
                        val stopGen = ++currentGeneration
                        activeCredentialGeneration = null
                        _sessionState.value = SessionState.Stopping(current.userId, stopGen)
                        val teardownError = stopWorkersSafely()
                        if (teardownError != null) {
                            pendingCleanupRequired = true
                            recordStartFailedLocked(
                                userId = current.userId,
                                generation = stopGen,
                                cause = teardownError,
                                targetSession = null,
                            )
                            return
                        }
                        pendingCleanupRequired = false
                        clearFailedTargetLocked()
                        _sessionState.value = SessionState.Idle
                        scheduleDelayedDatabaseCloseLocked(
                            DelayedDatabaseClose(
                                userId = current.userId,
                                resourceGeneration = resourceGen,
                                stopTransitionGeneration = stopGen,
                            )
                        )
                    }
                    SessionState.Initializing,
                    is SessionState.StartFailed,
                    is SessionState.Stopping -> {
                        if (current is SessionState.StartFailed &&
                            !forceRetryAfterFailure &&
                            hasFailedForTarget(null)
                        ) {
                            return
                        }
                        activeCredentialGeneration = null
                        val teardownError = stopWorkersSafely()
                        if (teardownError != null) {
                            pendingCleanupRequired = true
                            val uid = (current as? SessionState.StartFailed)?.userId ?: boundDatabaseIdentity?.first ?: Uuid.NIL
                            recordStartFailedLocked(
                                userId = uid,
                                generation = ++currentGeneration,
                                cause = teardownError,
                                targetSession = null,
                            )
                            return
                        }
                        val dbIdentity = boundDatabaseIdentity
                        if (dbIdentity != null) {
                            try {
                                closeBoundDatabaseLocked(dbIdentity.first, dbIdentity.second)
                            } catch (t: Throwable) {
                                if (t is CancellationException) throw t
                                log.error(t) {
                                    "[SessionManager] closing bound database failed while reconciling logged-out state for user=${dbIdentity.first} gen=${dbIdentity.second}"
                                }
                                pendingCleanupRequired = true
                                recordStartFailedLocked(
                                    userId = dbIdentity.first,
                                    generation = ++currentGeneration,
                                    cause = t,
                                    targetSession = null,
                                )
                                return
                            }
                        }
                        pendingCleanupRequired = false
                        clearFailedTargetLocked()
                        _sessionState.value = SessionState.Idle
                    }
                    SessionState.Idle,
                    is SessionState.Starting -> Unit
                }
                return
            }

            // desired != null
            when (current) {
                is SessionState.Active -> {
                    if (current.userId == desired.userId &&
                        activeCredentialGeneration == desired.credentialGeneration
                    ) {
                        // Already active for the authoritative user and credential
                        // generation: a stale `false` emission that waited on the
                        // lock before a fast re-login is ignored instead of tearing
                        // down the live session.
                        return
                    }
                    // Switching from one user to another OR re-logging in as the
                    // same user with a new credential generation: tear down old first.
                    val dbIdentity = boundDatabaseIdentity
                    val resourceGen = dbIdentity?.takeIf { it.first == current.userId }?.second ?: current.generation
                    val stopGen = ++currentGeneration
                    activeCredentialGeneration = null
                    _sessionState.value = SessionState.Stopping(current.userId, stopGen)
                    val teardownError = stopWorkersSafely()
                    if (teardownError != null) {
                        // Do NOT close the old database or start the new session
                        // while old workers cannot be confirmed stopped.
                        pendingCleanupRequired = true
                        recordStartFailedLocked(
                            userId = desired.userId,
                            generation = stopGen,
                            cause = teardownError,
                            targetSession = desired,
                        )
                        return
                    }
                    if (current.userId != desired.userId) {
                        try {
                            closeBoundDatabaseLocked(current.userId, resourceGen)
                        } catch (t: Throwable) {
                            if (t is CancellationException) throw t
                            log.error(t) { "[SessionManager] closing previous user DB failed on account switch" }
                            pendingCleanupRequired = true
                            recordStartFailedLocked(
                                userId = desired.userId,
                                generation = stopGen,
                                cause = t,
                                targetSession = desired,
                            )
                            return
                        }
                    }
                    pendingCleanupRequired = false
                    if (!startForUserLocked(desired)) continue
                    return
                }
                is SessionState.StartFailed -> {
                    if (!hasFailedForTarget(desired) || forceRetryAfterFailure) {
                        if (pendingCleanupRequired || boundDatabaseIdentity != null) {
                            // A previous teardown or rollback failed and left workers or
                            // the database uncleaned: confirm workers stop and close the
                            // bound database before starting a new session.
                            val teardownError = stopWorkersSafely()
                            if (teardownError != null) {
                                pendingCleanupRequired = true
                                recordStartFailedLocked(
                                    userId = desired.userId,
                                    generation = ++currentGeneration,
                                    cause = teardownError,
                                    targetSession = desired,
                                )
                                return
                            }
                            val dbIdentity = boundDatabaseIdentity
                            if (dbIdentity != null) {
                                try {
                                    closeBoundDatabaseLocked(dbIdentity.first, dbIdentity.second)
                                } catch (t: Throwable) {
                                    if (t is CancellationException) throw t
                                    log.error(t) {
                                        "[SessionManager] closing bound database failed during StartFailed recovery for user=${dbIdentity.first} gen=${dbIdentity.second}"
                                    }
                                    pendingCleanupRequired = true
                                    recordStartFailedLocked(
                                        userId = desired.userId,
                                        generation = ++currentGeneration,
                                        cause = t,
                                        targetSession = desired,
                                    )
                                    return
                                }
                            }
                            pendingCleanupRequired = false
                        }
                        if (!startForUserLocked(desired)) continue
                    }
                    return
                }
                SessionState.Initializing,
                SessionState.Idle,
                is SessionState.Stopping -> {
                    val dbIdentity = boundDatabaseIdentity
                    if (dbIdentity != null && dbIdentity.first != desired.userId) {
                        try {
                            closeBoundDatabaseLocked(dbIdentity.first, dbIdentity.second)
                        } catch (t: Throwable) {
                            if (t is CancellationException) throw t
                            log.error(t) { "[SessionManager] closing previous user DB failed before starting new user" }
                            pendingCleanupRequired = true
                            recordStartFailedLocked(
                                userId = desired.userId,
                                generation = ++currentGeneration,
                                cause = t,
                                targetSession = desired,
                            )
                            return
                        }
                    }
                    if (!startForUserLocked(desired)) continue
                    return
                }
                is SessionState.Starting -> return
            }
        }
    }

    /**
     * Runs the startup sequence for [desired]. Returns `true` if the state has
     * settled (either into [SessionState.Active] or [SessionState.StartFailed]),
     * or `false` if the authoritative target changed mid-startup and the caller
     * should re-run reconciliation after the completed rollback.
     *
     * Must be called while holding [transitionMutex].
     */
    private suspend fun startForUserLocked(desired: DesiredSession): Boolean {
        val userId = desired.userId
        val sessionGen = ++currentGeneration
        activeCredentialGeneration = null
        _sessionState.value = SessionState.Starting(userId, sessionGen)
        var dbOpened = false
        var userSessionStarted = false
        var contactSessionStarted = false
        var messageSessionStarted = false
        try {
            val db = databaseHolder.getOrCreateDatabase(userId, sessionGen)
            boundDatabaseIdentity = userId to sessionGen
            dbOpened = true
            db.usersQueries.upsertUser(desired.user.toUserEntity())
            userRepository.startSession()
            userSessionStarted = true
            contactRepository.startSession()
            contactSessionStarted = true
            messageRepository.startSession()
            messageSessionStarted = true
            realtimeApi.connect()

            // Re-verify before committing Active: if credentials were cleared,
            // switched, or re-issued for a newer credential generation while
            // startup was suspended, roll back this attempt instead of
            // publishing a stale Active session.
            val latestDesired = desiredSession()
            if (latestDesired != desired) {
                log.info { "[SessionManager] target changed ($desired -> $latestDesired) during startup; rolling back gen=$sessionGen" }
                val rollbackErr = withContext(NonCancellable) {
                    rollbackPartialStart(
                        userId = userId,
                        sessionGen = sessionGen,
                        dbOpened = true,
                        userSessionStarted = true,
                        contactSessionStarted = true,
                        messageSessionStarted = true,
                    )
                }
                if (rollbackErr != null) {
                    pendingCleanupRequired = true
                    recordStartFailedLocked(
                        userId = latestDesired?.userId ?: userId,
                        generation = sessionGen,
                        cause = rollbackErr,
                        targetSession = latestDesired,
                    )
                    return true
                }
                pendingCleanupRequired = false
                clearFailedTargetLocked()
                _sessionState.value = SessionState.Idle
                return false
            }

            pendingCleanupRequired = false
            clearFailedTargetLocked()
            activeCredentialGeneration = desired.credentialGeneration
            _sessionState.value = SessionState.Active(userId, sessionGen)
            return true
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                val rollbackErr = rollbackPartialStart(
                    userId = userId,
                    sessionGen = sessionGen,
                    dbOpened = dbOpened,
                    userSessionStarted = userSessionStarted,
                    contactSessionStarted = contactSessionStarted,
                    messageSessionStarted = messageSessionStarted,
                )
                if (rollbackErr != null) {
                    pendingCleanupRequired = true
                    if (rollbackErr !== e) {
                        e.addSuppressed(rollbackErr)
                    }
                    recordStartFailedLocked(
                        userId = userId,
                        generation = sessionGen,
                        cause = rollbackErr,
                        targetSession = desired,
                    )
                } else {
                    pendingCleanupRequired = false
                    clearFailedTargetLocked()
                    _sessionState.value = SessionState.Idle
                }
            }
            throw e
        } catch (t: Throwable) {
            log.error(t) { "[SessionManager] session startup failed for user=$userId gen=$sessionGen; rolling back" }
            withContext(NonCancellable) {
                val rollbackErr = rollbackPartialStart(
                    userId = userId,
                    sessionGen = sessionGen,
                    dbOpened = dbOpened,
                    userSessionStarted = userSessionStarted,
                    contactSessionStarted = contactSessionStarted,
                    messageSessionStarted = messageSessionStarted,
                )
                if (rollbackErr != null) {
                    pendingCleanupRequired = true
                    if (rollbackErr !== t) {
                        t.addSuppressed(rollbackErr)
                    }
                } else {
                    pendingCleanupRequired = false
                }
                recordStartFailedLocked(
                    userId = userId,
                    generation = sessionGen,
                    cause = t,
                    targetSession = desired,
                )
            }
            return true
        }
    }

    /**
     * Rolls back partial resources in reverse order. Only closes the database
     * if worker teardown succeeded.
     */
    private suspend fun rollbackPartialStart(
        userId: Uuid,
        sessionGen: Long,
        dbOpened: Boolean,
        userSessionStarted: Boolean,
        contactSessionStarted: Boolean,
        messageSessionStarted: Boolean,
    ): Throwable? {
        val disconnectErr = runCatching { realtimeApi.disconnect() }
            .onFailure { log.warn(it) { "[SessionManager] rollback disconnect failed" } }
            .exceptionOrNull()
        val stopMsgErr = if (messageSessionStarted) {
            runCatching { messageRepository.stopSession() }
                .onFailure { log.warn(it) { "[SessionManager] rollback stopSession failed" } }
                .exceptionOrNull()
        } else null
        val stopContactErr = if (contactSessionStarted) {
            runCatching { contactRepository.stopSession() }
                .onFailure { log.warn(it) { "[SessionManager] rollback contact stopSession failed" } }
                .exceptionOrNull()
        } else null
        val stopUserErr = if (userSessionStarted) {
            runCatching { userRepository.stopSession() }
                .onFailure { log.warn(it) { "[SessionManager] rollback user stopSession failed" } }
                .exceptionOrNull()
        } else null

        val workerErr = combineErrors(disconnectErr, stopMsgErr, stopContactErr, stopUserErr)
        if (workerErr != null) {
            return workerErr
        }

        if (dbOpened) {
            return runCatching { closeBoundDatabaseLocked(userId, sessionGen) }
                .onFailure { log.warn(it) { "[SessionManager] rollback closeDatabase failed" } }
                .exceptionOrNull()
        }
        return null
    }

    private fun combineErrors(vararg errors: Throwable?): Throwable? {
        var primary: Throwable? = null
        for (err in errors) {
            if (err == null) continue
            if (primary == null) {
                primary = err
            } else if (primary !== err) {
                primary.addSuppressed(err)
            }
        }
        return primary
    }

    private object InertUserRepository : UserRepository {
        override fun getMeFlow(): Flow<User?> = emptyFlow()
        override suspend fun syncMe(): Result<User, UserError> = Err(UserError.PermissionDenied)
        override suspend fun updateMyProfile(
            displayName: String?,
            avatarUrl: String?,
            bio: String?,
        ): Result<User, UserError> = Err(UserError.PermissionDenied)
        override fun getGlobalSettingsFlow(): Flow<UserSetting?> = emptyFlow()
        override suspend fun syncGlobalSettings(): Result<UserSetting, UserError> = Err(UserError.PermissionDenied)
        override suspend fun updateGlobalSettings(
            privacy: PrivacySetting?,
            preferences: UserPreference?,
        ): Result<Boolean, UserError> = Err(UserError.PermissionDenied)
        override suspend fun fetchUserDetail(userId: Uuid): Result<User, UserError> = Err(UserError.PermissionDenied)
        override fun getUserFlow(userId: Uuid): Flow<User?> = emptyFlow()
        override suspend fun searchUsers(keyword: String): Result<List<User>, UserError> = Err(UserError.PermissionDenied)
    }

    private object InertContactRepository : ContactRepository {
        override fun getFriendsFlow(): Flow<List<Pair<Contact, User>>> = emptyFlow()
        override suspend fun syncFriends(): Result<Unit, ContactError> = Err(ContactError.PermissionDenied)
        override suspend fun searchContacts(query: String): Result<List<Pair<Contact, User>>, ContactError> =
            Err(ContactError.PermissionDenied)
        override suspend fun sendFriendRequest(targetId: Uuid, message: String?): Result<Boolean, ContactError> =
            Err(ContactError.PermissionDenied)
        override fun observeIncomingRequests(): Flow<List<ContactRequest>> = emptyFlow()
        override suspend fun syncContactRequests(): Result<List<ContactRequest>, ContactError> =
            Err(ContactError.PermissionDenied)
        override suspend fun handleFriendRequest(
            requestId: Uuid,
            accept: Boolean,
            remark: String?,
        ): Result<Boolean, ContactError> = Err(ContactError.PermissionDenied)
        override suspend fun blockUser(userId: Uuid): Result<Boolean, ContactError> = Err(ContactError.PermissionDenied)
        override suspend fun unblockUser(userId: Uuid): Result<Boolean, ContactError> = Err(ContactError.PermissionDenied)
        override suspend fun removeFriend(userId: Uuid): Result<Boolean, ContactError> = Err(ContactError.PermissionDenied)
    }


}
