package com.github.woodsmarshes.chat.core.network.ktor

import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.CoroutineBindingScope
import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.github.michaelbull.result.throwIf
import com.github.woodsmarshes.chat.core.common.utils.debug
import com.github.woodsmarshes.chat.core.common.utils.error
import com.github.woodsmarshes.chat.core.common.utils.verbose
import com.github.woodsmarshes.chat.core.datastore.AuthTokenDataSource
import com.github.woodsmarshes.chat.core.model.error.DomainError
import com.github.woodsmarshes.chat.core.network.serialization.ProjectJson
import com.github.woodsmarshes.chat.core.network.serialization.ProjectProtobuf
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.observer.ResponseObserver
import io.ktor.client.plugins.resources.Resources
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.serialization.kotlinx.protobuf.protobuf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

expect fun httpEngine(): HttpClientEngineFactory<*>
fun createHttpClient(
    httpClientEngine: HttpClientEngine,
    config: NetworkConfig,
    authTokenDataSource: AuthTokenDataSource,
    httpEventBus: HttpEventBus,
) = HttpClient(httpClientEngine) {
    val log = KotlinLogging.logger {}

    install(HttpTimeout) {
        requestTimeoutMillis = 10000
        connectTimeoutMillis = 5000
        socketTimeoutMillis = 15000
    }

    install(ContentNegotiation) {
        json(ProjectJson)
        protobuf(ProjectProtobuf)
    }

    install(Logging) {
        logger = object : Logger {
            override fun log(message: String) {
                log.verbose(tag = "Logger Ktor =>", message = message)
            }
        }
        level = LogLevel.INFO
        sanitizeHeader { header -> header == HttpHeaders.Authorization }
    }

    install(ResponseObserver) {
        onResponse { response ->
            log.debug(tag = "HTTP status:", message = "${response.status.value}")
        }
    }

    install(Resources)

    defaultRequest {
        url {
            host = config.host
            port = config.port
            protocol = config.protocol
        }
        header(HttpHeaders.ContentType, ContentType.Application.ProtoBuf)
    }

    install(UserAgent) {
        agent = "Ktor client"
    }

    install(WebSockets) {
        contentConverter = KotlinxWebsocketSerializationConverter(ProjectProtobuf)
        pingIntervalMillis = 30_000
    }

    install(Auth) {
        bearer {
            loadTokens {
                val jwt = authTokenDataSource.jwtToken.first()
                if (!jwt.isNullOrEmpty()) {
                    BearerTokens(jwt, null)
                } else {
                    null
                }
            }
            // refreshTokens { ... } // (可选) 令牌刷新逻辑
        }
    }

    expectSuccess = true
    HttpResponseValidator {
        handleResponseExceptionWithRequest { exception, request ->
            val clientException = exception as? ClientRequestException ?: return@handleResponseExceptionWithRequest
            val exceptionResponse = clientException.response
            val requestUrl = exceptionResponse.call.request.url
            val event: HttpErrorEvent = when (val statusCode = exceptionResponse.status) {
                HttpStatusCode.Unauthorized ->  {
                    log.error(tag = "HttpResponseValidator", message = "Unauthorized: $requestUrl")
                    HttpErrorEvent.Unauthorized(
                        requestUrl = requestUrl.toString()
                    )
                }
                else -> {
                    HttpErrorEvent.GeneralHttpError(
                        statusCode = statusCode,
                        responseBody = exceptionResponse.bodyAsText(),
                        requestUrl = requestUrl.toString()
                    )
                }
            }
            httpEventBus.sendError(event)
        }
    }
}

suspend inline fun <reified E : DomainError> Throwable.toDomainError(
    fallback: (String?) -> E
): Result<Nothing, E> {
    return if (this is ResponseException) {
        try {
            val errorBody = response.body<E>()
            Err(errorBody)
        } catch (e: Exception) {
            Err(fallback("Serialization Error: ${e.message}"))
        }
    } else {
        Err(fallback(this.message))
    }
}
val log = KotlinLogging.logger {}
suspend inline fun <T, reified E : DomainError> CoroutineBindingScope<E>.bindApi(
    noinline fallback: (String?) -> E,
    crossinline block: suspend () -> T
): T {
    return runSuspendCatching {
        block()
    }.onErr { throwable ->
        log.info { "[bindApi] => ${throwable.message}" }
        throwable.toDomainError(fallback).bind()
    }.getOrThrow { IllegalStateException("bindApi: unreachable error state") }
}