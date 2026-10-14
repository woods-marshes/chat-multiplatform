package com.github.woodsmarshes.chat.websocket

import com.github.woodsmarshes.chat.core.network.dto.events.RealtimeEvent
import com.github.woodsmarshes.chat.repository.ConversationParticipantRepository
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.server.websocket.sendSerialized
import io.ktor.util.logging.Logger
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.uuid.Uuid

/**
 * Pushes realtime events to connected clients. Routing by conversation is
 * resolved against the database, not an in-memory room table, so a dropped
 * join/leave event can never corrupt who receives what — and any node with
 * database access derives the same recipients.
 */
interface RealtimeDelivery {
    /**
     * Registers a newly connected [session] and initializes its dedicated outbound
     * queue and writer. Idempotent: returns false if the session is already registered,
     * inactive, or if the delivery service is closed.
     */
    fun registerSession(session: WebSocketServerSession): Boolean

    /**
     * Enqueues an event to an already-registered session. Returns true if queued,
     * false if rejected (unregistered, queue full, closing, or delivery closed).
     * Does NOT create or resurrect writers on send.
     */
    suspend fun sendToSession(session: WebSocketServerSession, data: RealtimeEvent): Boolean

    suspend fun sendToUser(userId: Uuid, data: RealtimeEvent)
    suspend fun sendToUsers(userIds: List<Uuid>, data: RealtimeEvent)
    suspend fun sendToConversation(conversationId: Uuid, data: RealtimeEvent)

    /**
     * Closes the session's writer and unregisters it from the delivery service and session index.
     * Idempotent: safe to invoke repeatedly from disconnect/cleanup hooks.
     */
    fun closeSession(session: WebSocketServerSession)
}

class WebSocketRealtimeDelivery(
    private val sessions: SessionIndex,
    private val participants: ConversationParticipantRepository,
    private val logger: Logger,
    private val scope: CoroutineScope,
    private val queueCapacity: Int = DEFAULT_QUEUE_CAPACITY,
    private val sender: suspend (WebSocketServerSession, RealtimeEvent) -> Unit =
        { session, event -> session.sendSerialized<RealtimeEvent>(event) },
) : RealtimeDelivery, AutoCloseable {

    companion object {
        const val DEFAULT_QUEUE_CAPACITY = 64
        private const val SOCKET_CLOSE_TIMEOUT_MS = 2000L
    }

    init {
        require(queueCapacity > 0 && queueCapacity != Channel.UNLIMITED) {
            "queueCapacity must be a positive finite integer, got $queueCapacity"
        }
    }

    private val lock = Any()
    private var isClosed = false
    private val writers = HashMap<WebSocketServerSession, ConnectionWriter>()

    private class CloseWorkItem(
        val session: WebSocketServerSession,
        val writer: ConnectionWriter,
        val reason: CloseReason,
    )

    /**
     * Outbound writer dedicated to a single [WebSocketServerSession].
     *
     * Guarantee boundaries:
     * 1. Events enqueued onto this connection are transmitted in the exact order of
     *    their arrival in [channel], with maximum sender concurrency = 1.
     * 2. This does NOT guarantee ordering across multiple concurrent producers
     *    submitting to different streams or sessions; business correctness is owned
     *    by the sync journal protocol, not by in-memory queueing.
     * 3. When the channel capacity is exceeded (slow consumer), the writer marks the
     *    connection as closing, ceases accepting new frames, cancels its writer job,
     *    and triggers connection teardown.
     * 4. The underlying coroutine job is created lazily at construction time so it is
     *    always non-null and immediately cancellable even if close() or overflow races
     *    with start().
     */
    internal class ConnectionWriter(
        val session: WebSocketServerSession,
        val capacity: Int,
        val scope: CoroutineScope,
        val logger: Logger,
        val sender: suspend (WebSocketServerSession, RealtimeEvent) -> Unit,
        val onTeardown: (ConnectionWriter, CloseReason) -> Unit,
    ) {
        val channel = Channel<RealtimeEvent>(capacity)
        val isClosing = AtomicBoolean(false)
        val job: Job = scope.launch(start = CoroutineStart.LAZY) {
            var exitReason = CloseReason(CloseReason.Codes.NORMAL, "Session completed")
            try {
                for (event in channel) {
                    if (!isActive || !session.isActive) {
                        exitReason = CloseReason(CloseReason.Codes.NORMAL, "Session inactive")
                        break
                    }
                    sender(session, event)
                }
            } catch (e: CancellationException) {
                exitReason = CloseReason(CloseReason.Codes.NORMAL, "Cancelled")
                throw e
            } catch (e: Exception) {
                logger.error("Failed to deliver realtime event: ${e.message}", e)
                exitReason = CloseReason(CloseReason.Codes.GOING_AWAY, "Delivery failed")
            } finally {
                channel.cancel()
                if (isClosing.compareAndSet(false, true)) {
                    onTeardown(this@ConnectionWriter, exitReason)
                }
            }
        }

        fun installCompletionHandler() {
            job.invokeOnCompletion { cause ->
                channel.cancel()
                if (isClosing.compareAndSet(false, true)) {
                    val reason = when (cause) {
                        null -> CloseReason(CloseReason.Codes.NORMAL, "Job completed")
                        is CancellationException -> CloseReason(CloseReason.Codes.NORMAL, "Cancelled")
                        else -> CloseReason(CloseReason.Codes.GOING_AWAY, "Delivery failed")
                    }
                    onTeardown(this@ConnectionWriter, reason)
                }
            }
        }

        fun start(): ConnectionWriter {
            if (!isClosing.get()) {
                job.start()
            }
            return this
        }

        fun enqueue(event: RealtimeEvent): Boolean {
            if (isClosing.get() || !session.isActive) return false
            val result = channel.trySend(event)
            if (result.isSuccess) return true

            // Overflow: channel full or already closed
            if (isClosing.compareAndSet(false, true)) {
                logger.warn("WebSocket outbound queue overflow for session; closing connection")
                channel.cancel()
                job.cancel()
                onTeardown(this, CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Queue overflow"))
            }
            return false
        }

        fun cancelAndDiscard(reason: CloseReason) {
            if (isClosing.compareAndSet(false, true)) {
                channel.cancel()
                job.cancel()
            }
        }
    }

    override fun registerSession(session: WebSocketServerSession): Boolean {
        val writerToStart: ConnectionWriter = synchronized(lock) {
            if (isClosed || !scope.isActive || !session.isActive || writers.containsKey(session)) {
                return false
            }
            val writer = ConnectionWriter(
                session = session,
                capacity = queueCapacity,
                scope = scope,
                logger = logger,
                sender = sender,
                onTeardown = ::teardownFromWriter,
            )
            writers[session] = writer
            writer
        }
        // Install completion handler after writers[session] is established.
        // If the job was already cancelled (e.g. parent scope cancelled in flight),
        // invokeOnCompletion fires immediately and cleans up without lingering.
        writerToStart.installCompletionHandler()
        writerToStart.start()
        return true
    }

    private fun teardownFromWriter(writer: ConnectionWriter, reason: CloseReason) {
        val closeWork = synchronized(lock) {
            if (writers[writer.session] === writer) {
                writers.remove(writer.session)
                sessions.remove(writer.session)
                CloseWorkItem(writer.session, writer, reason)
            } else null
        }
        if (closeWork != null) {
            executeCloseWork(closeWork)
        }
    }

    override fun closeSession(session: WebSocketServerSession) {
        val closeWork = synchronized(lock) {
            val writer = writers.remove(session)
            sessions.remove(session)
            if (writer != null) {
                CloseWorkItem(session, writer, CloseReason(CloseReason.Codes.NORMAL, "Session closed"))
            } else null
        }
        if (closeWork != null) {
            executeCloseWork(closeWork)
        }
    }

    override suspend fun sendToSession(session: WebSocketServerSession, data: RealtimeEvent): Boolean {
        val writer = synchronized(lock) {
            if (isClosed) null else writers[session]
        } ?: return false
        return writer.enqueue(data)
    }

    override suspend fun sendToUser(userId: Uuid, data: RealtimeEvent) {
        val userSessions = sessions.getSessions(userId)
        userSessions.forEach { session ->
            sendToSession(session, data)
        }
    }

    override suspend fun sendToUsers(userIds: List<Uuid>, data: RealtimeEvent) {
        userIds.forEach { userId ->
            sendToUser(userId, data)
        }
    }

    override suspend fun sendToConversation(conversationId: Uuid, data: RealtimeEvent) {
        val members = participants.getConversationParticipants(conversationId)
        members.forEach { member ->
            val userSessions = sessions.getSessions(member.userId)
            userSessions.forEach { session ->
                sendToSession(session, data)
            }
        }
    }

    override fun close() {
        val itemsToClose = synchronized(lock) {
            if (isClosed) return
            isClosed = true
            val list = writers.map { (session, writer) ->
                sessions.remove(session)
                CloseWorkItem(session, writer, CloseReason(CloseReason.Codes.GOING_AWAY, "Server shutdown"))
            }
            writers.clear()
            list
        }
        itemsToClose.forEach { executeCloseWork(it) }
    }

    private fun executeCloseWork(item: CloseWorkItem) {
        item.writer.cancelAndDiscard(item.reason)
        if (scope.isActive) {
            val closeJob = scope.launch {
                try {
                    val closedGracefully = withTimeoutOrNull(SOCKET_CLOSE_TIMEOUT_MS) {
                        if (item.session.isActive) {
                            item.session.close(item.reason)
                            true
                        } else false
                    } ?: false

                    if (!closedGracefully && item.session.isActive) {
                        // Close call timed out or was unacknowledged: cancel the session
                        // scope to guarantee no lingering connection remains.
                        item.session.cancel()
                    }
                } catch (_: Exception) {
                    runCatching { item.session.cancel() }
                }
            }
            closeJob.invokeOnCompletion { cause ->
                // If closeJob is cancelled before or during execution (e.g. parent
                // scope cancelled while closeJob is in the dispatcher queue),
                // ensure the session scope is terminated as a fallback.
                if (cause != null && item.session.isActive) {
                    runCatching { item.session.cancel() }
                }
            }
        } else {
            // Parent scope is already inactive: cancel session scope immediately
            runCatching { item.session.cancel() }
        }
    }

    internal fun getActiveWritersCount(): Int = synchronized(lock) { writers.size }
    internal fun getWriterForTest(session: WebSocketServerSession): ConnectionWriter? = synchronized(lock) { writers[session] }
}
