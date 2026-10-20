package com.github.woodsmarshes.chat.feature.chat.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatArrivalScrollTest {
    @Test
    fun followsOnlyAtExactLatestPosition() {
        assertTrue(shouldFollowChatArrival(0, 0))
        assertFalse(shouldFollowChatArrival(0, 1))
        assertFalse(shouldFollowChatArrival(1, 0))
        assertFalse(shouldFollowChatArrival(4, 80))
        assertFalse(shouldFollowChatArrival(40, 120))
    }
}
