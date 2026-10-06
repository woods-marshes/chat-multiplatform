package com.github.woodsmarshes.chat

import com.github.woodsmarshes.chat.core.model.Conversation
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.network.dto.auth.AuthResponse
import com.github.woodsmarshes.chat.core.network.dto.auth.RegisterRequest
import com.github.woodsmarshes.chat.core.network.dto.conversation.CreateConversationRequest
import com.github.woodsmarshes.chat.core.network.dto.conversation.CreatePrivateRequest
import com.github.woodsmarshes.chat.core.network.dto.events.MessageEventResponse
import com.github.woodsmarshes.chat.core.network.dto.events.MessageRequest
import com.github.woodsmarshes.chat.core.network.serialization.ProjectJson
import com.github.woodsmarshes.chat.core.network.serialization.ProjectProtobuf
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.serialization.kotlinx.protobuf.protobuf
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * Regression suite for the realtime socket path: the production symptom
 * this guards against is a websocket that upgrades (101) and then drops
 * within milliseconds, which is what a Koin resolution failure inside the
 * ws handler looks like from the outside.
 */
class RealtimeSocketTest {

    @Test
    fun websocketHoldsConnectionWithValidToken() = testApplication {
        environment {
            config = ApplicationConfig("application-test.conf")
        }
        val client = wsClient()
        val (token, _) = registerUser(client, "hold")

        client.webSocket("/ws?access_token=$token") {
            // A broken realtime graph tears the socket down within
            // milliseconds of the upgrade. If it is still open after an
            // idle 2s, the server-side read loop actually started; if not,
            // report the close reason the server sent.
            delay(2.seconds)
            if (closeReason.isCompleted) {
                fail("websocket closed within 2s of connecting: ${closeReason.getCompleted()}")
            }
        }
    }

    @Test
    fun websocketDeliversTypingEventAcrossUsers() = testApplication {
        environment {
            config = ApplicationConfig("application-test.conf")
        }
        val client = wsClient()

        val (tokenA, userA) = registerUser(client, "alice")
        val (tokenB, userB) = registerUser(client, "bob")

        val conversation = client.post("/v1/conversations") {
            bearerAuth(tokenA)
            // Encode explicitly: the sealed request types are protobuf-first
            // (the polymorphic discriminator rides the wire as protobuf
            // field 65535), and routing the object through the client's
            // content negotiation can silently fall back to JSON, which the
            // server's polymorphic protobuf decoder then chokes on.
            contentType(ContentType.Application.ProtoBuf)
            setBody(
                ProjectProtobuf.encodeToByteArray(
                    CreateConversationRequest.serializer(),
                    CreatePrivateRequest(targetUserId = userB.id) as CreateConversationRequest,
                )
            )
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }.body<Conversation>()

        val received = CompletableDeferred<MessageEventResponse.UserTyping>()
        val bConnected = CompletableDeferred<Unit>()

        coroutineScope {
            val bobJob = launch {
                client.webSocket("/ws?access_token=$tokenB") {
                    bConnected.complete(Unit)
                    while (true) {
                        // withTimeoutOrNull instead of withTimeout: a plain
                        // timeout exception would cancel this coroutine
                        // silently and leave the awaiting test body hanging.
                        val event = withTimeoutOrNull(15.seconds) {
                            receiveDeserialized<MessageEventResponse>()
                        }
                        if (event == null) {
                            received.completeExceptionally(
                                AssertionError("no MessageEventResponse within 15s")
                            )
                            return@webSocket
                        }
                        if (event is MessageEventResponse.UserTyping) {
                            received.complete(event)
                            return@webSocket
                        }
                    }
                }
            }

            bConnected.await()
            // The client-side handshake returning does not guarantee the
            // server has registered the session in the shared index yet;
            // give that registration a beat before triggering a lookup.
            delay(500)

            client.webSocket("/ws?access_token=$tokenA") {
                // Explicit sealed type: serializing the concrete Typing
                // class would omit the polymorphic discriminator the
                // server's MessageRequest decoder requires, and the frame
                // would be silently dropped as malformed.
                sendSerialized<MessageRequest>(
                    MessageRequest.Typing(
                        senderId = userA.id,
                        conversationId = conversation.id,
                        isTyping = true,
                    )
                )
            }

            val typing = received.await()
            assertEquals(userA.id, typing.userId)
            assertEquals(conversation.id, typing.conversationId)
            assertTrue(typing.isTyping)

            bobJob.cancel()
        }
    }

    private fun ClientProvider.wsClient(): HttpClient = createClient {
        install(ContentNegotiation) {
            json(ProjectJson)
            protobuf(ProjectProtobuf)
        }
        install(WebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(ProjectProtobuf)
        }
    }

    private suspend fun registerUser(client: HttpClient, tag: String): Pair<String, User> {
        val suffix = Uuid.random().toString().take(8)
        val response = client.post("/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("ws-$tag-$suffix", "ws-$tag-$suffix@test.local", "password-123"))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<AuthResponse>()
        return body.accessToken to body.user
    }
}
