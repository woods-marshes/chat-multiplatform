package com.github.woodsmarshes.chat.routes

import com.github.woodsmarshes.chat.core.network.dto.events.MessageRequest
import com.github.woodsmarshes.chat.core.network.serialization.ProjectProtobuf
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.WebsocketDeserializeException
import io.ktor.websocket.Frame
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * The WS read loop in RealtimeRoutes survives malformed frames because the
 * converter raises WebsocketDeserializeException for undecodable ones —
 * these tests pin that contract, which the loop's continue-on-failure
 * branch depends on.
 */
class RealtimeFrameDecodingTest {

    private val converter = KotlinxWebsocketSerializationConverter(ProjectProtobuf)

    @Test
    fun textFramesCannotDecodeIntoTheProtobufProtocol() {
        runBlocking {
            assertFailsWith<WebsocketDeserializeException> {
                converter.deserialize(
                    Charsets.UTF_8,
                    io.ktor.util.reflect.typeInfo<MessageRequest>(),
                    Frame.Text(fin = true, data = "not-protobuf".encodeToByteArray()),
                )
            }
            Unit
        }
    }

    @Test
    fun binaryGarbageCannotDecodeIntoTheProtobufProtocol() {
        // Validated separately (diagnostic): raw undecodable payloads escape
        // the converter as kotlinx SerializationException, which the read
        // loop also treats as skippable.
        runBlocking {
            assertFailsWith<kotlinx.serialization.SerializationException> {
                ProjectProtobuf.decodeFromByteArray(
                    MessageRequest.serializer(),
                    byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()),
                )
            }
            Unit
        }
    }
}
