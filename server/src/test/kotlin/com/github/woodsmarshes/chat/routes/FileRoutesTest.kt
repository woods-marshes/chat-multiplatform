package com.github.woodsmarshes.chat.routes

import com.github.woodsmarshes.chat.base.jwt.TokenClaim
import com.github.woodsmarshes.chat.base.jwt.TokenConfig
import com.github.woodsmarshes.chat.base.jwt.TokenServiceImpl
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
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class FileRoutesTest {

    @Test
    fun oversizedUploadIsRejectedWithPayloadTooLarge() = testApplication {
        environment { config = ApplicationConfig("application-test.conf") }

        val tokenConfig = TokenConfig(
            issuer = "chat-server-test",
            audience = "API-test",
            realm = "Ktor Server Test",
            expiresIn = 3_600_000,
            secret = "test-secret-key-for-unit-tests-only",
        )
        val token = TokenServiceImpl().generateToken(
            tokenConfig,
            TokenClaim(Keys.USER_ID, Uuid.random().toString()),
        )
        val oversizedImage = ByteArray(5 * 1024 * 1024 + 1)

        val response = client.post("/v1/files/upload?type=IMAGE") {
            bearerAuth(token)
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
    }
}
