package com.github.woodsmarshes.chat

import com.github.woodsmarshes.chat.utils.extractUserId
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.openapi.OpenApiInfo
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.cachingheaders.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.plugins.openapi.*
import io.ktor.server.plugins.partialcontent.*
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.swagger.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.OpenApiDocSource
import kotlin.time.Duration.Companion.seconds

fun Application.configureHTTP() {
    install(CallLogging) {
        level = org.slf4j.event.Level.INFO
        filter { call -> call.request.path().startsWith("/v1") || call.request.path().startsWith("/ws") }
        format { call ->
            val status = call.response.status()
            "Status=$status, ${call.request.httpMethod.value} ${call.request.path()}"
        }
    }
    routing {
        openAPI(path = "openapi") {
            info = OpenApiInfo("Chat Multiplatform API", "1.0")
            source = OpenApiDocSource.Routing {
                routingRoot.descendants()
            }
            outputPath = System.getProperty("user.dir") + "/server/docs"
        }
        swaggerUI(path = "openapi")
    }
    install(PartialContent) {
            // Maximum number of ranges that will be accepted from a HTTP request.
            // If the HTTP request specifies more ranges, they will all be merged into a single range.
            maxRangeCount = 10
        }
    // Only trust forwarded headers when the deployment actually sits behind a
    // reverse proxy: on a directly exposed server clients could forge
    // X-Forwarded-For to rotate rate-limit keys. With the plugins absent,
    // request.origin falls back to the socket address.
    if (environment.config.propertyOrNull("http.behindProxy")?.getString() == "true") {
        install(ForwardedHeaders)
        install(XForwardedHeaders)
    }
    install(DefaultHeaders) {
        header("X-Engine", "Ktor") // will send this header with each response
    }
    install(CachingHeaders) {
        options { call, outgoingContent ->
            when (outgoingContent.contentType?.withoutParameters()) {
                ContentType.Text.CSS -> CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 24 * 60 * 60))
                else -> null
            }
        }
    }
    install(Compression) {
        gzip {
            // 只压缩文本类内容，图片/音频/视频格式本身已高度压缩，gzip 反而浪费 CPU
        }
    }
    install(RateLimit) {
        // Without a requestKey every caller shares one bucket, so a single
        // client exhausting its budget locks out everyone behind the same
        // instance. Buckets are keyed per client instead.
        register(RateLimitName("files")) {
            // Attachment downloads: chattier than uploads, still bounded.
            rateLimiter(
                limit = 120,
                refillPeriod = 60.seconds,
            )
            requestKey { call -> call.clientKey() }
        }

        register(RateLimitName("uploads")) {
            rateLimiter(
                limit = 60,                 // 每分钟允许60次上传
                refillPeriod = 60.seconds,
            )
            requestKey { call -> call.clientKey() }
        }

        register(RateLimitName("auth")) {
            // Credential endpoints: tight budget to blunt credential stuffing.
            rateLimiter(
                limit = 10,
                refillPeriod = 60.seconds,
            )
            requestKey { call -> call.clientKey() }
        }

        register(RateLimitName("search")) {
            // LIKE scans over user/group tables; one of them is anonymous.
            rateLimiter(
                limit = 30,
                refillPeriod = 60.seconds,
            )
            requestKey { call -> call.clientKey() }
        }
    }
}

private fun ApplicationCall.clientKey(): String {
    val userId = runCatching { extractUserId() }.getOrNull()
    // request.origin reflects the forwarded headers when the plugins are
    // installed and equals the socket address otherwise.
    return if (userId != null) "user:$userId" else "ip:${request.origin.remoteHost}"
}
