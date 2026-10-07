package com.github.woodsmarshes.chat.core.common.session

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Contract of the session operation executor: the caller's cancellation and the
 * session's cancellation both reach the operation, an already stopped session
 * refuses new work with a dedicated error (never a translated
 * CancellationException), stop() waits even for a SUSPENDING cleanup, start()
 * is refused while stopping, concurrent stops share one stop process, and
 * nested repository calls reuse the running session instead of registering
 * again.
 *
 * The queued dispatcher is on purpose: an eager (unconfined) one runs
 * cancellation and cleanup synchronously inside cancel(), which would let
 * stop() skip its join and still satisfy the tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionExecutorTest {

    private fun TestScope.newExecutor() = SessionExecutor(StandardTestDispatcher(testScheduler))

    @Test
    fun resultPassesThroughToTheCaller() = runTest {
        val executor = newExecutor()
        executor.start()

        assertEquals(42, executor.execute { 42 })

        executor.stop()
    }

    @Test
    fun callerCancellationStopsTheOperationItWasAwaiting() = runTest {
        val executor = newExecutor()
        executor.start()
        val operationStarted = CompletableDeferred<Unit>()
        var finallyRan = false

        val caller = launch {
            try {
                executor.execute {
                    operationStarted.complete(Unit)
                    try {
                        awaitCancellation()
                    } finally {
                        finallyRan = true
                    }
                }
            } catch (_: CancellationException) {
                // The expected path for a cancelled caller.
            }
        }
        operationStarted.await()
        caller.cancel()
        caller.join()
        testScheduler.advanceUntilIdle()

        assertTrue(finallyRan, "a cancelled caller must cancel the operation it was awaiting, running its finally")
        executor.stop()
    }

    @Test
    fun sessionStopWaitsForASuspendingCleanupBeforeReturning() = runTest {
        val executor = newExecutor()
        executor.start()
        val barrier = CleanupBarrier()
        launchHangingOperation(executor, barrier)
        barrier.operationStarted.await()

        val stopCaller = launch { executor.stop() }
        barrier.cleanupStarted.await()

        assertFalse(
            stopCaller.isCompleted,
            "stop() must not return while a suspending cleanup is still in progress",
        )

        barrier.allowCleanupFinish.complete(Unit)
        stopCaller.join()
        assertTrue(barrier.cleanupFinished, "stop() returns only after the cleanup finished")
        testScheduler.advanceUntilIdle()
        assertNotNull(
            barrier.callerCancellation,
            "the caller must observe the session stopping as CancellationException, never a domain error",
        )
    }

    @Test
    fun startIsRefusedWhileStoppingAndWorksAgainAfterwards() = runTest {
        val executor = newExecutor()
        executor.start()
        val barrier = CleanupBarrier()
        launchHangingOperation(executor, barrier)
        barrier.operationStarted.await()

        val stopCaller = launch { executor.stop() }
        barrier.cleanupStarted.await()

        assertFailsWith<IllegalStateException>("start must be refused while a stop is in progress") {
            executor.start()
        }
        assertFailsWith<SessionStoppedException>("new operations must be refused while stopping") {
            executor.execute { 1 }
        }

        barrier.allowCleanupFinish.complete(Unit)
        stopCaller.join()
        assertTrue(barrier.cleanupFinished)

        executor.start()
        assertEquals("again", executor.execute { "again" })
        executor.stop()
    }

    @Test
    fun concurrentStopsAwaitTheSameStopProcess() = runTest {
        val executor = newExecutor()
        executor.start()
        val barrier = CleanupBarrier()
        launchHangingOperation(executor, barrier)
        barrier.operationStarted.await()

        val firstStop = launch { executor.stop() }
        barrier.cleanupStarted.await()
        var secondStopReturned = false
        val secondStop = launch {
            executor.stop()
            secondStopReturned = true
        }

        testScheduler.advanceUntilIdle()
        assertFalse(firstStop.isCompleted, "stop() must not return while cleanup is in progress")
        assertFalse(secondStopReturned, "a concurrent stop must wait for the same stop process")

        barrier.allowCleanupFinish.complete(Unit)
        firstStop.join()
        secondStop.join()
        assertTrue(barrier.cleanupFinished)
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun stopProcessSurvivesItsInitiatorBeingCancelled() = runTest {
        val executor = newExecutor()
        executor.start()
        val barrier = CleanupBarrier()
        launchHangingOperation(executor, barrier)
        barrier.operationStarted.await()

        val initiator = launch { executor.stop() }
        barrier.cleanupStarted.await() // the initiator is now inside its join()

        var joinerReturned = false
        val joiner = launch {
            executor.stop()
            joinerReturned = true
        }
        testScheduler.advanceUntilIdle() // the joiner reaches its shared await

        initiator.cancel() // the stop initiator dies mid-process

        // The shared stop must still be in progress: no new session, joiner waiting.
        testScheduler.advanceUntilIdle()
        assertFailsWith<IllegalStateException>("the abandoned initiator must not publish Stopped early") {
            executor.start()
        }
        assertFalse(
            joinerReturned,
            "the shared stop process must not complete while the cleanup barrier is held",
        )

        barrier.allowCleanupFinish.complete(Unit)
        joiner.join()
        assertTrue(barrier.cleanupFinished, "the shared stop still waits for the cleanup to finish")
        assertTrue(joinerReturned)

        // With the stop fully published, a new session works.
        executor.start()
        assertEquals("after", executor.execute { "after" })
        executor.stop()
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun staleSessionElementIsRefusedInsteadOfJoiningTheNewSession() = runTest {
        val executor = newExecutor()
        executor.start()

        var capturedElement: SessionExecutor.Element? = null
        executor.execute {
            currentCoroutineContext()[SessionExecutor.Element].also { capturedElement = it }
        }
        val oldElement = assertNotNull(capturedElement, "the operation context carries the session element")

        executor.stop()
        executor.start() // a NEW session is now running

        var bodyRan = false
        assertFailsWith<SessionStoppedException> {
            withContext(oldElement) {
                executor.execute {
                    bodyRan = true
                }
            }
        }
        assertFalse(
            bodyRan,
            "a stale session element must not run as an operation of the new session",
        )

        executor.stop()
    }

    @Test
    fun stopFromInsideTheSessionIsRefusedInsteadOfSelfDeadlock() = runTest {
        val executor = newExecutor()
        executor.start()

        assertFailsWith<IllegalStateException> {
            executor.execute { executor.stop() }
        }
        assertTrue(executor.isRunning, "the refused stop must leave the session running")

        executor.stop()
    }

    @Test
    fun nestedCallsRunInlineInTheSameSession() = runTest {
        val executor = newExecutor()
        executor.start()

        var nestedElement: SessionExecutor.Element? = null
        val result = executor.execute {
            val inner = executor.execute {
                currentCoroutineContext()[SessionExecutor.Element].also { nestedElement = it }
                "inner"
            }
            "$inner-outer"
        }

        assertEquals("inner-outer", result)
        assertSame(executor, nestedElement?.executor, "nested calls must reuse the running session")
        assertEquals(1L, nestedElement?.sessionId, "the element must carry the running instance's id")
        executor.stop()
    }

    @Test
    fun newOperationsAreRefusedAfterStopWithADedicatedError() = runTest {
        val executor = newExecutor()
        executor.start()
        executor.stop()

        // A distinct refusal, not a CancellationException: callers must be able
        // to tell "session ended" apart from "this coroutine was cancelled".
        assertFailsWith<SessionStoppedException> { executor.execute { 1 } }
    }

    @Test
    fun restartAfterStopAcceptsNewOperationsAndStopIsIdempotent() = runTest {
        val executor = newExecutor()
        executor.start()
        executor.stop()
        executor.stop() // idempotent

        executor.start()
        assertEquals("again", executor.execute { "again" })
        executor.stop()
    }

    /**
     * An operation whose cleanup suspends on a controlled barrier: stop() must
     * wait for it, and the executor must refuse start() while it runs.
     */
    private class CleanupBarrier {
        val operationStarted = CompletableDeferred<Unit>()
        val cleanupStarted = CompletableDeferred<Unit>()
        val allowCleanupFinish = CompletableDeferred<Unit>()
        var cleanupFinished = false
        var callerCancellation: CancellationException? = null
    }

    private fun TestScope.launchHangingOperation(
        executor: SessionExecutor,
        barrier: CleanupBarrier,
    ): Job = launch {
        try {
            executor.execute {
                barrier.operationStarted.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) {
                        barrier.cleanupStarted.complete(Unit)
                        barrier.allowCleanupFinish.await()
                        barrier.cleanupFinished = true
                    }
                }
            }
        } catch (e: CancellationException) {
            barrier.callerCancellation = e
        }
    }
}
