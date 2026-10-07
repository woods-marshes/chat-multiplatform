package com.github.woodsmarshes.chat.routes

import com.github.woodsmarshes.chat.base.jwt.TokenClaim
import com.github.woodsmarshes.chat.base.jwt.TokenConfig
import com.github.woodsmarshes.chat.base.jwt.TokenServiceImpl
import com.github.woodsmarshes.chat.events.ContactEvent
import com.github.woodsmarshes.chat.events.ConversationEvent
import com.github.woodsmarshes.chat.events.EventBus
import com.github.woodsmarshes.chat.events.MessageEvent
import com.github.woodsmarshes.chat.repository.ConversationParticipantDataSourceImpl
import com.github.woodsmarshes.chat.repository.GroupProfileDataSourceImpl
import com.github.woodsmarshes.chat.repository.database.schema.GroupProfiles
import com.github.woodsmarshes.chat.service.ConversationSettingsService
import com.github.woodsmarshes.chat.service.UserService
import com.github.woodsmarshes.chat.support.TestDb
import com.github.woodsmarshes.chat.utils.Keys
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.dsl.module
import org.koin.ktor.ext.getKoin
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class FileRoutesTest {

    private val tokenConfig = TokenConfig(
        issuer = "chat-server-test",
        audience = "API-test",
        realm = "Ktor Server Test",
        expiresIn = 3_600_000,
        secret = "test-secret-key-for-unit-tests-only",
    )

    private fun tokenFor(userId: Uuid = Uuid.random()): String =
        TokenServiceImpl().generateToken(tokenConfig, TokenClaim(Keys.USER_ID, userId.toString()))

    private fun smallPng(): ByteArray =
        ByteArrayOutputStream().use { out ->
            ImageIO.write(BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", out)
            out.toByteArray()
        }

    @Test
    fun oversizedUploadIsRejectedWithPayloadTooLarge() = testApplication {
        environment { config = ApplicationConfig("application-test.conf") }

        val imageDir = File("uploads/image").apply { mkdirs() }
        val before = imageDir.listFiles()?.size ?: 0
        val oversizedImage = ByteArray(5 * 1024 * 1024 + 1)

        val response = client.post("/v1/files/upload?type=IMAGE") {
            bearerAuth(tokenFor())
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            key = "file",
                            value = oversizedImage,
                            headers = Headers.build {
                                append(HttpHeaders.ContentType, ContentType.Image.PNG.toString())
                                append(HttpHeaders.ContentDisposition, "filename=\"big.png\"")
                            },
                        )
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        // The cap trips after part of the body is already on disk; the
        // half-written file must not survive the rejected request.
        assertEquals(before, imageDir.listFiles()?.size ?: 0)
    }

    @Test
    fun secondFilePartIsRejectedWithBadRequest() = testApplication {
        environment { config = ApplicationConfig("application-test.conf") }

        val bytes = smallPng()
        val response = client.post("/v1/files/upload?type=FILE") {
            bearerAuth(tokenFor())
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", bytes, Headers.build {
                            append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                            append(HttpHeaders.ContentDisposition, "filename=\"one.bin\"")
                        })
                        append("file", bytes, Headers.build {
                            append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                            append(HttpHeaders.ContentDisposition, "filename=\"two.bin\"")
                        })
                    }
                )
            )
        }

        // The first part used to be fully processed and silently overwritten
        // by the second; now the request itself is refused.
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun groupAvatarForNonParticipantWritesNoFile() = testApplication {
        environment { config = ApplicationConfig("application-test.conf") }

        val avatarDir = File("uploads/avatar").apply { mkdirs() }
        val before = avatarDir.listFiles()?.size ?: 0

        val response = client.post("/v1/files/avatar?isGroup=true&targetId=${Uuid.random()}") {
            bearerAuth(tokenFor())
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", smallPng(), Headers.build {
                            append(HttpHeaders.ContentType, ContentType.Image.PNG.toString())
                            append(HttpHeaders.ContentDisposition, "filename=\"avatar.jpg\"")
                        })
                    }
                )
            )
        }

        // The permission precheck runs before any bytes are written, so a
        // refused upload leaves no orphaned avatar behind.
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(before, avatarDir.listFiles()?.size ?: 0)
    }

    @Test
    fun secondAvatarPartRemovesTheFirstAvatarFromDisk() = testApplication {
        environment { config = ApplicationConfig("application-test.conf") }

        val avatarDir = File("uploads/avatar").apply { mkdirs() }
        val before = avatarDir.listFiles()?.size ?: 0
        val bytes = smallPng()

        val response = client.post("/v1/files/avatar?isGroup=false") {
            bearerAuth(tokenFor())
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", bytes, Headers.build {
                            append(HttpHeaders.ContentType, ContentType.Image.PNG.toString())
                            append(HttpHeaders.ContentDisposition, "filename=\"avatar.jpg\"")
                        })
                        append("file", bytes, Headers.build {
                            append(HttpHeaders.ContentType, ContentType.Image.PNG.toString())
                            append(HttpHeaders.ContentDisposition, "filename=\"avatar.jpg\"")
                        })
                    }
                )
            )
        }

        // The first avatar was fully written before the second part was
        // refused; avatars are excluded from the temp store, so the route
        // must delete the orphan itself.
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(before, avatarDir.listFiles()?.size ?: 0)
    }

    @Test
    fun avatarReferenceUpdateErrRemovesTheFile() = testApplication {
        environment { config = ApplicationConfig("application-test.conf") }

        val avatarDir = File("uploads/avatar").apply { mkdirs() }
        val before = avatarDir.listFiles()?.size ?: 0

        // The token user does not exist, so updateProfile returns Err only
        // AFTER the avatar bytes are on disk. A returned Err means the
        // reference was definitely not written, so the file must go.
        val response = client.post("/v1/files/avatar?isGroup=false") {
            bearerAuth(tokenFor())
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", smallPng(), Headers.build {
                            append(HttpHeaders.ContentType, ContentType.Image.PNG.toString())
                            append(HttpHeaders.ContentDisposition, "filename=\"avatar.jpg\"")
                        })
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals(before, avatarDir.listFiles()?.size ?: 0)
    }

    @Test
    fun avatarReferenceUpdateCrashKeepsTheFileForReconciliation() = testApplication {
        environment { config = ApplicationConfig("application-test.conf") }

        // A thrown exception does NOT prove the reference failed to commit —
        // commit-phase failures are indeterminate — so the route keeps the
        // file for reconciliation instead of risking a dangling reference.
        val crashingUserService = mockk<UserService>()
        coEvery { crashingUserService.updateProfile(any(), any()) } throws IllegalStateException("repository exploded")
        application {
            // Swap UserService once the app's Koin is fully installed; the
            // route resolves its dependency lazily per call, so the avatar
            // request below sees the crashing fake.
            monitor.subscribe(ApplicationStarted) { app ->
                app.getKoin().loadModules(listOf(module { single { crashingUserService } }))
            }
        }

        val avatarDir = File("uploads/avatar").apply { mkdirs() }
        val before = avatarDir.listFiles()?.size ?: 0

        val response = client.post("/v1/files/avatar?isGroup=false") {
            bearerAuth(tokenFor())
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", smallPng(), Headers.build {
                            append(HttpHeaders.ContentType, ContentType.Image.PNG.toString())
                            append(HttpHeaders.ContentDisposition, "filename=\"avatar.jpg\"")
                        })
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals(before + 1, avatarDir.listFiles()?.size ?: 0)
    }

    @Test
    fun groupAvatarEventFailureAfterCommitKeepsFileAndReference() = testApplication {
        environment { config = ApplicationConfig("application-test.conf") }

        // Seed the owner and their group before boot; the app shares this
        // JVM's in-memory H2 instance.
        TestDb.reset()
        val owner = TestDb.user("group-owner")
        val conversationId = TestDb.groupConversation(owner, emptyList())

        // Real repositories + an event bus that explodes AFTER the profile
        // row has committed: the exception surfaces while the reference is
        // already in the database, so the file must NOT be cleaned up —
        // deleting it would leave a dangling reference.
        val crashingSettings = ConversationSettingsService(
            conversationParticipantRepository = ConversationParticipantDataSourceImpl(),
            groupProfileRepository = GroupProfileDataSourceImpl(),
            eventBus = ExplodingConversationEventBus(),
        )
        application {
            monitor.subscribe(ApplicationStarted) { app ->
                app.getKoin().loadModules(listOf(module { single { crashingSettings } }))
            }
        }

        val avatarDir = File("uploads/avatar").apply { mkdirs() }
        val before = avatarDir.listFiles()?.size ?: 0

        val response = client.post("/v1/files/avatar?isGroup=true&targetId=$conversationId") {
            bearerAuth(tokenFor(owner))
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", smallPng(), Headers.build {
                            append(HttpHeaders.ContentType, ContentType.Image.PNG.toString())
                            append(HttpHeaders.ContentDisposition, "filename=\"avatar.jpg\"")
                        })
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        // The file survives: the exception does not prove non-commit.
        assertEquals(before + 1, avatarDir.listFiles()?.size ?: 0)

        val avatarUrl = transaction(TestDb.database) {
            GroupProfiles.selectAll()
                .where { GroupProfiles.conversationId eq conversationId }
                .single()[GroupProfiles.avatarUrl]
        }
        assertNotNull(avatarUrl)
        assertTrue(File(avatarUrl.removePrefix("/")).isFile, "the referenced avatar file must survive")
    }
}

/**
 * Named class on purpose: anonymous `object : X by Y {}` declarations crash
 * the Ktor OpenAPI compiler extension.
 */
private class ExplodingConversationEventBus : EventBus {
    override val contactEvents: SharedFlow<ContactEvent> = MutableSharedFlow()
    override val conversationEvents: SharedFlow<ConversationEvent> = MutableSharedFlow()
    override val messageEvents: SharedFlow<MessageEvent> = MutableSharedFlow()

    override fun publishContactEvent(event: ContactEvent) {}
    override fun publishConversationEvent(event: ConversationEvent) = error("event bus exploded after commit")
    override fun publishMessageEvent(event: MessageEvent) {}
}
