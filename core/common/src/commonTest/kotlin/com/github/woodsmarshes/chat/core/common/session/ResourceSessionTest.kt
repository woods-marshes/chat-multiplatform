package com.github.woodsmarshes.chat.core.common.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ResourceSessionTest {
    @Test
    fun repeatedStartPreservesResourcesAndRestartCreatesNewIdentity() = runTest {
        var creates = 0
        val session = ResourceSession(StandardTestDispatcher(testScheduler), create = { ++creates })
        session.start()
        session.start()
        assertEquals(1, session.execute { it })
        assertEquals(2, session.execute { outer -> session.execute { outer + it } })
        session.stop()
        assertFailsWith<SessionStoppedException> { session.execute { it } }
        session.start()
        assertEquals(2, session.execute { it })
        session.stop()
    }

    @Test
    fun cancelledStopInitiatorStillWaitsForOperationsAndDisposal() = runTest {
        val entered = CompletableDeferred<Unit>()
        val cleaning = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var disposed = false
        val session = ResourceSession(StandardTestDispatcher(testScheduler), create = { Any() }, dispose = { disposed = true })
        session.start()
        val operation = launch {
            session.execute {
                entered.complete(Unit)
                try { awaitCancellation() } finally {
                    withContext(NonCancellable) { cleaning.complete(Unit); release.await() }
                }
            }
        }
        entered.await()
        val first = launch { session.stop() }
        cleaning.await()
        first.cancel()
        var secondFinished = false
        val second = launch { session.stop(); secondFinished = true }
        assertFalse(disposed)
        assertFalse(secondFinished)
        release.complete(Unit)
        first.join()
        second.join()
        operation.join()
        assertTrue(disposed)
        assertTrue(secondFinished)
        session.start()
        session.stop()
    }

    @Test
    fun failedPreparationDisposesResourcesAndCanRetry() = runTest {
        var fail = true
        var disposals = 0
        val session = ResourceSession(StandardTestDispatcher(testScheduler), create = { Any() },
            prepare = { check(!fail) }, dispose = { disposals++ })
        assertFailsWith<IllegalStateException> { session.start() }
        assertEquals(1, disposals)
        fail = false
        session.start()
        session.stop()
        assertEquals(2, disposals)
    }

    @Test
    fun failedDisposalPreventsUnsafeRestartAndIsSharedWithLaterStops() = runTest {
        val failure = IllegalStateException("cleanup failed")
        val session = ResourceSession(StandardTestDispatcher(testScheduler), create = { Any() }, dispose = { throw failure })
        session.start()
        assertFailsWith<IllegalStateException> { session.stop() }
        assertFailsWith<IllegalStateException> { session.stop() }
        assertFailsWith<IllegalStateException> { session.start() }
        assertFailsWith<SessionStoppedException> { session.execute { it } }
    }

    @Test
    fun managedOperationCannotStopItsOwnSession() = runTest {
        val session = ResourceSession(StandardTestDispatcher(testScheduler), create = { Any() })
        session.start()
        session.execute { assertFailsWith<IllegalStateException> { session.stop() } }
        session.stop()
    }
}
