package com.github.woodsmarshes.chat.serialization

import com.github.woodsmarshes.chat.core.network.dto.events.ContactEventResponse
import com.github.woodsmarshes.chat.core.network.dto.events.RealtimeEvent
import com.github.woodsmarshes.chat.core.network.serialization.ProjectJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * The sync journal stores events as jsonb and reads them back by class
 * discriminator. Adding `@SerialName` to an event subclass rewrites that
 * discriminator and makes previously stored rows undecodable, so the wire
 * name is pinned here on purpose: changing it must be a deliberate,
 * migration-accompanied decision.
 */
class RealtimeEventWireFormatTest {

    private val event: RealtimeEvent = ContactEventResponse.FriendRequestSent(
        requestId = Uuid.random(),
        senderId = Uuid.random(),
        receiverId = Uuid.random(),
        timestamp = Instant.fromEpochMilliseconds(1_700_000_000_000),
    )

    @Test
    fun storedEventsKeepTheirClassDiscriminator() {
        val encoded = ProjectJson.encodeToString(event)
        val discriminator = ProjectJson.parseToJsonElement(encoded).jsonObject["type"]?.jsonPrimitive?.content

        assertEquals(
            "com.github.woodsmarshes.chat.core.network.dto.events.ContactEventResponse.FriendRequestSent",
            discriminator,
        )
        assertEquals(event, ProjectJson.decodeFromString<RealtimeEvent>(encoded))
    }
}
