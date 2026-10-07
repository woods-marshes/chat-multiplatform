package com.github.woodsmarshes.chat.core.network.ktor

import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.CoroutineBindingScope
import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.github.michaelbull.result.throwIf
import com.github.woodsmarshes.chat.core.common.utils.debug
import com.github.woodsmarshes.chat.core.common.utils.error
import com.github.woodsmarshes.chat.core.common.utils.verbose
import com.github.woodsmarshes.chat.core.datastore.AuthTokenDataSource
import com.github.woodsmarshes.chat.core.datastore.AuthTokenSnapshot
import com.github.woodsmarshes.chat.core.model.error.DomainError
import com.github.woodsmarshes.chat.core.network.serialization.ProjectJson
import com.github.woodsmarshes.chat.core.network.serialization.ProjectProtobuf
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.AuthCircuitBreaker
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
import io.ktor.util.AttributeKey
import kotlinx.coroutines.CancellationException
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

private val RequestAuthSnapshotKey = AttributeKey<AuthTokenSnapshot>("RequestAuthSnapshot")

internal fun interface RequestAuthBoundHook {
    suspend fun onBound()
}

/**
 * Optional test hook invoked immediately after a request binds its
 * [AuthTokenSnapshot] in `RequestAuthBindingPlugin` and before the `Auth`
 * plugin or send guard selects its `Authorization` header.
 */
internal val OnRequestAuthBoundHookKey = AttributeKey<RequestAuthBoundHook>("OnRequestAuthBoundHook")

/**
 * Thrown before a request is transmitted when the active credential
 * generation changed after the request bound its session identity, so a
 * request created for one session is never sent with another session's
 * `Authorization` header.
 */
class StaleRequestGenerationException(
    val requestGeneration: Long,
    val currentGeneration: Long,
) : IllegalStateException(
    "Request bound to credential generation $requestGeneration cannot be sent under generation $currentGeneration"
)

expect fun httpEngine(): HttpClientEngineFactory<*>
fun createHttpClient(
    httpClientEngine: HttpClientEngine,
    config: NetworkConfig,
    authTokenDataSource: AuthTokenDataSource,
    tokenRefresher: TokenRefresher,
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

    install(
        createClientPlugin("RequestAuthBindingPlugin") {
            onRequest { request, _ ->
                if (!request.attributes.contains(AuthCircuitBreaker) &&
                    !request.attributes.contains(RequestAuthSnapshotKey)
                ) {
                    request.attributes.put(RequestAuthSnapshotKey, authTokenDataSource.currentSnapshot())
                    request.attributes.getOrNull(OnRequestAuthBoundHookKey)?.onBound()
                }
            }
        }
    )

    install(Auth) {
        bearer {
            // The token holder caches the first non-null token for the whole client lifetime
            // when caching is on, so a later login as a different account would keep sending
            // the previous JWT. Read the token from the data source on every request instead.
            cacheTokens = false
            sendWithoutRequest { request ->
                !request.attributes.contains(AuthCircuitBreaker)
            }
            loadTokens {
                val snapshot = authTokenDataSource.currentSnapshot()
                val jwt = snapshot.jwtToken
                if (!jwt.isNullOrEmpty()) {
                    BearerTokens(jwt, snapshot.refreshToken)
                } else {
                    null
                }
            }
            // refreshTokens: on a 401 the bearer plugin re-runs the request
            // with whatever this returns; a null result (credentials dead or
            // request belonged to an older session generation) lets the
            // request fail without refreshing or replaying as a new account.
            // The request's generation is strictly its bound snapshot's
            // generation — never re-derived from the token string.
            refreshTokens {
                val boundSnapshot = response.call.request.attributes.getOrNull(RequestAuthSnapshotKey)
                    ?: return@refreshTokens null
                val sentAccessToken = response.call.request.headers[HttpHeaders.Authorization]
                    ?.removePrefix("Bearer ")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                tokenRefresher.refreshAfter401(
                    client = client,
                    requestGeneration = boundSnapshot.generation,
                    requestAccessToken = sentAccessToken ?: boundSnapshot.jwtToken,
                )?.let {
                    io.ktor.client.plugins.auth.providers.BearerTokens(it.accessToken, it.refreshToken)
                }
            }
        }
    }

    // Installed after Auth so its Send hook runs inside Auth's Send hook,
    // right before the engine transmits both initial requests and 401 replays.
    // Ensures a request bound to session A is refused if the active credential
    // generation changed before transmission (instead of going out with
    // session B's Authorization header from loadTokens), while still allowing
    // same-generation token rotation.
    install(
        createClientPlugin("RequestAuthSendGuardPlugin") {
            on(Send) { request ->
                val bound = request.attributes.getOrNull(RequestAuthSnapshotKey)
                if (bound != null) {
                    val latest = authTokenDataSource.currentSnapshot()
                    if (latest.generation != bound.generation) {
                        throw StaleRequestGenerationException(
                            requestGeneration = bound.generation,
                            currentGeneration = latest.generation,
                        )
                    }
                    val tokenToUse = latest.jwtToken ?: bound.jwtToken
                    request.headers.remove(HttpHeaders.Authorization)
                    if (!tokenToUse.isNullOrEmpty()) {
                        request.headers.append(HttpHeaders.Authorization, "Bearer $tokenToUse")
                    }
                }
                proceed(request)
            }
        }
    )

    expectSuccess = true
    HttpResponseValidator {
        handleResponseExceptionWithRequest { exception, request ->
            // 5xx (ServerRequestException) must reach the error bus too, not
            // only 4xx — consumers otherwise never learn about server faults.
            val serverException = exception as? ServerResponseException
            val clientException = exception as? ClientRequestException
            val exceptionResponse = serverException?.response ?: clientException?.response
            if (exceptionResponse == null) return@handleResponseExceptionWithRequest
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