package com.github.woodsmarshes.chat

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.openapi.OpenApiInfo
import io.ktor.resources.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.html.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.cachingheaders.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.plugins.openapi.*
import io.ktor.server.plugins.partialcontent.*
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.requestvalidation.RequestValidation
import io.ktor.server.plugins.requestvalidation.ValidationResult
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.OpenApiDocSource
import kotlin.time.Duration.Companion.seconds
import kotlinx.css.*
import kotlinx.html.*
import kotlinx.serialization.Serializable
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

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
    install(ForwardedHeaders) // WARNING: for security, do not include this if not behind a reverse proxy
    install(XForwardedHeaders) // WARNING: for security, do not include this if not behind a reverse proxy
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
        register(RateLimitName("api")) {
            // 配置限流规则
            rateLimiter(
                limit = 1000,               // 允许的请求数
                refillPeriod = 60.seconds,  // 重置周期
            )
        }

        register(RateLimitName("uploads")) {
            rateLimiter(
                limit = 60,                 // 每分钟允许60次上传
                refillPeriod = 60.seconds,
            )
        }
    }
}
