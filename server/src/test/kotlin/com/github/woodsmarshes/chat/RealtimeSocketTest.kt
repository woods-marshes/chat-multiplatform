package com.github.woodsmarshes.chat

import com.github.woodsmarshes.chat.core.model.Conversation
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.contract.dto.admin.UpdateBanRequest
import com.github.woodsmarshes.chat.core.contract.dto.auth.AuthResponse
import com.github.woodsmarshes.chat.core.contract.dto.auth.RegisterRequest
import com.github.woodsmarshes.chat.core.contract.dto.conversation.CreateConversationRequest
import com.github.woodsmarshes.chat.core.contract.dto.conversation.CreatePrivateRequest
import com.github.woodsmarshes.chat.core.contract.dto.events.MessageEventResponse
import com.github.woodsmarshes.chat.core.contract.dto.events.MessageRequest
import com.github.woodsmarshes.chat.core.contract.dto.events.RealtimeEvent
import com.github.woodsmarshes.chat.core.contract.dto.events.SyncStateResponse
import com.github.woodsmarshes.chat.core.contract.serialization.ProjectJson
import com.github.woodsmarshes.chat.core.contract.serialization.ProjectProtobuf
import com.github.woodsmarshes.chat.core.model.UserRole
import com.github.woodsmarshes.chat.repository.database.schema.Users
import com.github.woodsmarshes.chat.utils.connectToH2Database
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
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
import io.ktor.websocket.CloseReason
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
        transaction(connectToH2Database()) {
            exec("SET MODE MYSQL")
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

        // The first Heads frame is sent after the session is indexed. Awaiting
        // it gives the test a deterministic registration signal without a sleep.
        val received = CompletableDeferred<MessageEventResponse.UserTyping>()
        val bobRegistered = CompletableDeferred<Unit>()

        coroutineScope {
            val bobJob = launch {
                client.webSocket("/ws?access_token=$tokenB") {
                    while (true) {
                        // withTimeoutOrNull instead of withTimeout: a plain
                        // timeout exception would cancel this coroutine
                        // silently and leave the awaiting test body hanging.
                        // The first SyncStateResponse.Heads frame is the
                        // deterministic signal that the session was registered.
                        val event = withTimeoutOrNull(15.seconds) {
                            receiveDeserialized<RealtimeEvent>()
                        }
                        if (event == null) {
                            received.completeExceptionally(
                                AssertionError("no realtime frame within 15s")
                            )
                            return@webSocket
                        }
                        when (event) {
                            is SyncStateResponse.Heads -> bobRegistered.complete(Unit)
                            is MessageEventResponse.UserTyping -> {
                                received.complete(event)
                                return@webSocket
                            }
                            else -> Unit
                        }
                    }
                }
            }

            bobRegistered.await()

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
                // Hold A's connection open: the typing broadcast goes through
                // a 1s server-side debounce, and disconnect cleanup cancels
                // that pending job. Closing the socket right after sending
                // races the cleanup against the debounce and intermittently
                // swallows the broadcast.
                received.await()
            }

            val typing = received.await()
            assertEquals(userA.id, typing.userId)
            assertEquals(conversation.id, typing.conversationId)
            assertTrue(typing.isTyping)

            bobJob.cancel()
        }
    }

    @Test
    fun bannedUserLosesSocketAndCannotReconnect() = testApplication {
        environment {
            config = ApplicationConfig("application-test.conf")
        }
        val client = wsClient()
        val (tokenA, userA) = registerUser(client, "ban-a")
        val (tokenB, userB) = registerUser(client, "ban-b")

        // Promote B through the shared test DB: the admin role is read fresh
        // from the repository on every admin call, so no cache needs busting.
        transaction(connectToH2Database()) {
            Users.update({ Users.id eq userB.id }) { it[role] = UserRole.ADMIN }
        }

        val aRegistered = CompletableDeferred<Unit>()
        val aClosed = CompletableDeferred<CloseReason?>()
        coroutineScope {
            val aJob = launch {
                client.webSocket("/ws?access_token=$tokenA") {
                    // The first server frame is sent after the session is
                    // indexed — the deterministic registration signal.
                    withTimeoutOrNull(15.seconds) { receiveDeserialized<RealtimeEvent>() }
                    aRegistered.complete(Unit)
                    aClosed.complete(closeReason.await())
                }
            }
            aRegistered.await()

            val banResponse = client.put("/v1/admin/users/${userA.id}/ban") {
                bearerAuth(tokenB)
                contentType(ContentType.Application.Json)
                setBody(UpdateBanRequest(banned = true))
            }
            assertEquals(HttpStatusCode.OK, banResponse.status)

            // The ban must drop the live socket, not just future handshakes.
            val reason = withTimeoutOrNull(10.seconds) { aClosed.await() }
            assertNotNull(reason, "banned user's websocket was not closed")

            // Reconnect must be rejected: the JWT validate hook re-checks the
            // ban state on every handshake.
            val reconnectRejected = try {
                client.webSocket("/ws?access_token=$tokenA") {
                    withTimeoutOrNull(5.seconds) { closeReason.await() }
                    false
                }
            } catch (e: Exception) {
                true
            }
            assertEquals(true, reconnectRejected, "banned user reconnected to /ws")

            aJob.cancel()
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
