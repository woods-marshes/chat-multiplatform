package com.github.woodsmarshes.chat.core.common.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile

/**
 * Owns a resource and its operations for one start-to-stop lifecycle.
 * Resource creation and readiness are serialized with stop. Once stop claims
 * ownership, neither caller cancellation nor concurrent stop calls can skip
 * operation cancellation, resource disposal, or the completion barrier.
 *
 * Resource-specific jobs must be cancelled by [beforeStop] and awaited by
 * [dispose]. Both callbacks run even when an earlier cleanup step fails.
 */
class ResourceSession<S : Any>(
    dispatcher: CoroutineDispatcher,
    private val create: suspend () -> S,
    private val prepare: suspend (S) -> Unit = {},
    private val validateExisting: suspend (S) -> Unit = {},
    private val beforeStop: suspend (S) -> Unit = {},
    private val dispose: suspend (S) -> Unit = {},
) {
    private val mutex = Mutex()
    private val executor = SessionExecutor(dispatcher)

    @Volatile
    private var phase: Phase<S> = Phase.Stopped

    val current: S?
        get() = (phase as? Phase.Running<S>)?.resource

    fun isCurrent(resource: S): Boolean = current === resource

    suspend fun start() {
        while (true) {
            val pending = mutex.withLock {
                when (val state = phase) {
                    is Phase.Running -> {
                        validateExisting(state.resource)
                        return
                    }
                    is Phase.Stopping -> state.done
                    Phase.Stopped -> {
                        val resource = create()
                        try {
                            executor.start()
                            phase = Phase.Running(resource)
                            prepare(resource)
                        } catch (failure: Throwable) {
                            withContext(NonCancellable) {
                                try {
                                    cleanup(resource)
                                    phase = Phase.Stopped
                                } catch (cleanupFailure: Throwable) {
                                    if (cleanupFailure !== failure) failure.addSuppressed(cleanupFailure)
                                    val failed = CompletableDeferred<Unit>()
                                    failed.completeExceptionally(cleanupFailure)
                                    phase = Phase.Stopping(failed)
                                }
                            }
                            throw failure
                        }
                        return
                    }
                }
            }
            pending.await()
        }
    }

    suspend fun stop() {
        check(!executor.isExecutingInCurrentContext()) {
            "A resource session cannot stop itself from a managed operation"
        }
        var resource: S? = null
        val completion = mutex.withLock {
            when (val state = phase) {
                is Phase.Running -> {
                    resource = state.resource
                    CompletableDeferred<Unit>().also { phase = Phase.Stopping(it) }
                }
                is Phase.Stopping -> state.done
                Phase.Stopped -> return
            }
        }
        val claimed = resource
        if (claimed == null) {
            completion.await()
            return
        }
        withContext(NonCancellable) {
            try {
                cleanup(claimed)
                mutex.withLock {
                    val state = phase
                    if (state is Phase.Stopping && state.done === completion) phase = Phase.Stopped
                }
                completion.complete(Unit)
            } catch (failure: Throwable) {
                // Retain the failed barrier: unsafe resources cannot be restarted.
                completion.completeExceptionally(failure)
                throw failure
            }
        }
    }

    private suspend fun cleanup(resource: S) {
        var failure: Throwable? = null
        suspend fun attempt(block: suspend () -> Unit) {
            try {
                block()
            } catch (error: Throwable) {
                val first = failure
                if (first == null) failure = error else if (first !== error) first.addSuppressed(error)
            }
        }
        attempt { beforeStop(resource) }
        attempt { executor.stop() }
        attempt { dispose(resource) }
        failure?.let { throw it }
    }

    suspend fun <T> execute(block: suspend (S) -> T): T = executor.execute {
        val resource = current ?: throw SessionStoppedException()
        val result = block(resource)
        currentCoroutineContext().ensureActive()
        result
    }

    private sealed interface Phase<out S : Any> {
        data object Stopped : Phase<Nothing>
        data class Running<S : Any>(val resource: S) : Phase<S>
        data class Stopping(val done: CompletableDeferred<Unit>) : Phase<Nothing>
    }
}
