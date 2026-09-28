package com.github.woodsmarshes.chat.events

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

class EventBusTest {

    @Test
    fun dropsAreCountedWhenTheBufferSaturates() = runBlocking {
        val bus = EventBusImpl()
        val gate = CompletableDeferred<Unit>()
        val collector = launch(Dispatchers.Default) {
            bus.messageEvents.collect { gate.await() }
        }
        withTimeout(5_000) {
            while (bus.messageSubscriberCount() == 0) {
                kotlinx.coroutines.delay(10)
            }
        }

        // One subscriber parks on the gate after taking the first event; the
        // 1 000 emissions against a 256 buffer must surface the overflow in
        // the counter instead of discarding silently.
        repeat(1_000) {
            bus.publishMessageEvent(
                MessageEvent.UserTyping(
                    conversationId = Uuid.random(),
                    userId = Uuid.random(),
                    isTyping = true,
                    timestamp = Clock.System.now(),
                )
            )
        }

        assertTrue(bus.droppedCount() > 0, "expected buffer saturation to count drops")
        collector.cancel()
    }
}
