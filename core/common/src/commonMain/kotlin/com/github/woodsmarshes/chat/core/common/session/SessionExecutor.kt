package com.github.woodsmarshes.chat.core.common.session

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Thrown when an operation is offered to a session that no longer accepts work.
 * Deliberately NOT a [CancellationException]: callers must be able to tell
 * "refused because the session ended" apart from "this coroutine was cancelled",
 * which must keep propagating untouched.
 */
class SessionStoppedException(
    message: String = "Session no longer accepts operations",
) : IllegalStateException(message)

/**
 * Runs session-bound operations so that two independent lifecycles both hold.
 *
 *  - The CALLER stays in charge: cancelling the caller cancels the operation it
 *    was awaiting. This is why [execute] must not be implemented with
 *    `withContext(sessionJob + dispatcher)` — passing a Job to `withContext`
 *    replaces the caller's job and severs the caller's cancellation from the
 *    work. Instead the operation is launched into the session scope with
 *    `async` and awaited; an interrupted await cancels the launched work.
 *  - The SESSION stays in charge: [stop] refuses new operations and suspends
 *    until every in-flight operation has finished, including its `finally`
 *    blocks (structured concurrency: the session job only completes once all
 *    of its children have).
 *
 * Lifecycle: Stopped → Running → Stopping → Stopped. [start] is refused while
 * a stop is in progress, so a new session can never begin before the previous
 * one's operations have fully exited; concurrent [stop] calls share one stop
 * process; a [stop] from inside a managed operation is refused instead of
 * waiting for itself. Once claimed, a stop cannot be abandoned by its
 * initiator being cancelled: the cancellation, the wait for every child and
 * the final publication of Stopped all run under [NonCancellable].
 *
 * Cancellation arriving mid-operation always propagates as
 * [CancellationException]; only operations offered to a session that no longer
 * accepts work are refused with [SessionStoppedException]. A caller's
 * completion does NOT guarantee the session-side cleanup of its operation has
 * finished — releasing session resources must be awaited via [stop].
 *
 * A repository operation running under [execute] may call repository functions
 * that also use [execute]: the nested call detects its own session (by running
 * instance, not just by executor) and runs inline instead of registering more
 * session-scoped work. An element of an ALREADY STOPPED session is refused
 * outright — it is never upgraded into a newly started session.
 */
class SessionExecutor(
    private val dispatcher: CoroutineDispatcher,
) {
    @Volatile
    private var phase: Phase = Phase.Stopped

    private val mutex = Mutex()
    private var nextSessionId = 0L

    val isRunning: Boolean
        get() = phase is Phase.Running

    /** Begins accepting operations. Refused unless the executor is fully stopped. */
    suspend fun start() {
        mutex.withLock {
            check(phase is Phase.Stopped) {
                "SessionExecutor is ${phase::class.simpleName}; start is refused"
            }
            phase = Phase.Running(RunningSession(++nextSessionId, dispatcher))
        }
    }

    /**
     * Refuses new operations, cancels every in-flight one, and suspends until
     * all of them have finished — a caller of [execute] observes this as
     * [CancellationException], while the operations' own `finally` blocks are
     * awaited before this returns. Idempotent; concurrent callers share one
     * stop; refused when called from inside the session it manages, where it
     * would wait for itself. The initiator's own cancellation does not
     * interrupt the stop: whoever claims it, the process runs to completion.
     */
    suspend fun stop() {
        check(currentCoroutineContext()[Element]?.executor !== this) {
            "stop() must not be called from inside the session it manages"
        }
        val claimed = mutex.withLock {
            when (val p = phase) {
                is Phase.Running -> {
                    val done = CompletableDeferred<Unit>()
                    // Running → Stopping under the lock: start() is now refused
                    // and the instance being stopped is captured, so no later
                    // field read can cancel a freshly started session.
                    phase = Phase.Stopping(p.session, done)
                    StopClaim.Initiator(p.session, done)
                }
                is Phase.Stopping -> StopClaim.Joiner(p.done)
                Phase.Stopped -> StopClaim.Joiner(null)
            }
        }
        when (claimed) {
            is StopClaim.Initiator ->
                // NonCancellable: the initiator being cancelled mid-wait must
                // not strand the session in Stopping, flip it to Stopped while
                // old operations still run, or leave concurrent stop callers
                // waiting on a done that is never completed.
                withContext(NonCancellable) {
                    try {
                        claimed.session.job.cancel()
                        claimed.session.job.join()
                    } finally {
                        mutex.withLock {
                            val p = phase
                            if (p is Phase.Stopping &&
                                p.session === claimed.session &&
                                p.done === claimed.done
                            ) {
                                phase = Phase.Stopped
                            }
                        }
                        claimed.done.complete(Unit)
                    }
                }
            is StopClaim.Joiner -> claimed.done?.await()
        }
    }

    internal suspend fun isExecutingInCurrentContext(): Boolean =
        currentCoroutineContext()[Element]?.executor === this

    suspend fun <T> execute(block: suspend () -> T): T {
        val element = currentCoroutineContext()[Element]
        if (element != null && element.executor === this) {
            // The call comes from one of this executor's own operations. Reuse
            // it only while that session is still the active one (running or
            // stopping): the nested work then shares the outer operation's fate
            // instead of registering more session-scoped work. A stale session
            // element is refused — never upgraded into a new session.
            val reusesActiveSession = mutex.withLock {
                element.sessionId == activeSessionIdLocked()
            }
            if (reusesActiveSession) return block()
            throw SessionStoppedException(
                "operation belongs to session ${element.sessionId}, which is no longer active",
            )
        }

        // A new top-level operation: register into the running session, if any.
        val deferred: Deferred<T> = mutex.withLock {
            val session = when (val p = phase) {
                is Phase.Running -> p.session
                else -> throw SessionStoppedException()
            }
            session.scope.async {
                withContext(Element(this@SessionExecutor, session.id)) { block() }
            }
        }
        try {
            return deferred.await()
        } catch (e: CancellationException) {
            // Either the caller or the session cancelled the operation. Stop the
            // work — its finally blocks run as part of this cancellation — and
            // propagate the cancellation untouched: translating it into a domain
            // error would turn normal teardown into "send failed" style UI noise
            // and mask operations that never exited.
            deferred.cancel()
            throw e
        }
    }

    /** Only meaningful while holding [mutex]. */
    private fun activeSessionIdLocked(): Long? = when (val p = phase) {
        is Phase.Running -> p.session.id
        is Phase.Stopping -> p.session.id
        Phase.Stopped -> null
    }

    /** One start()-to-stop() lifecycle; [id] is shared with [Element] for nesting decisions. */
    private class RunningSession(
        val id: Long,
        dispatcher: CoroutineDispatcher,
    ) {
        val job: CompletableJob = SupervisorJob()
        val scope = CoroutineScope(job + dispatcher)
    }

    private sealed interface Phase {
        data object Stopped : Phase
        data class Running(val session: RunningSession) : Phase
        data class Stopping(val session: RunningSession, val done: CompletableDeferred<Unit>) : Phase
    }

    private sealed interface StopClaim {
        data class Initiator(val session: RunningSession, val done: CompletableDeferred<Unit>) : StopClaim
        data class Joiner(val done: CompletableDeferred<Unit>?) : StopClaim
    }

    /**
     * Marks coroutines launched by this executor so nested [execute] calls reuse
     * their own session — identified by [sessionId], i.e. by the running
     * instance, not merely by the executor object. Internal so external callers
     * cannot construct or copy it to bypass executor registration.
     */
    internal class Element internal constructor(
        val executor: SessionExecutor,
        val sessionId: Long,
    ) : AbstractCoroutineContextElement(Element) {
        companion object Key : CoroutineContext.Key<Element>
    }
}
