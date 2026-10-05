package com.github.woodsmarshes.chat

import com.github.woodsmarshes.chat.repository.database.schema.AuthSessions
import com.github.woodsmarshes.chat.utils.dbQuery
import com.github.woodsmarshes.chat.websocket.WebSocketSessionManager
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.request.header
import io.ktor.server.request.path
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.binder.system.UptimeMetrics
import io.micrometer.core.instrument.config.MeterFilter
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.koin.ktor.ext.getKoin
import kotlin.uuid.Uuid

/** The single Prometheus registry for the whole server process. */
val prometheusRegistry = PrometheusMeterRegistry(io.micrometer.prometheusmetrics.PrometheusConfig.DEFAULT)

private const val METRICS_ROUTE = "/metrics"

/**
 * Observability: a Prometheus registry (HTTP request timers, JVM, WS
 * sessions, EventBus drops), a per-request CallId surfaced in the
 * access-log MDC, and two ops endpoints:
 *
 *  - /metrics — Prometheus scrape output. Open in development; gated by
 *    the `X-Metrics-Token` header once `metrics.token` is configured
 *    (reverse proxies should still keep it off the public internet).
 *  - /health  — liveness for docker-compose healthchecks; pings the DB.
 *
 * Hikari pool metrics are wired at pool creation time via
 * MicrometerMetricsTrackerFactory (see Database.kt).
 */
fun Application.configureObservability() {
    val sessionManager = getKoin().get<WebSocketSessionManager>()
    val metricsToken = environment.config.propertyOrNull("metrics.token")?.getString()

    // Cap label cardinality: scanners hitting unregistered paths would
    // otherwise create one request-timer series per URL.
    prometheusRegistry.config().meterFilter(
        MeterFilter.maximumAllowableTags("ktor.http.server.requests", "route", 100, MeterFilter.deny())
    )

    install(MicrometerMetrics) {
        registry = prometheusRegistry
        // Keep ops endpoints out of request timers. (Percentile histograms
        // can be added via distributionStatisticConfig once the target
        // Micrometer API stabilizes; the scrape already carries count/sum/max.)
        filter { call -> call.request.path() !in setOf("/health", METRICS_ROUTE) }
        meterBinders = listOf(
            JvmGcMetrics(),
            JvmMemoryMetrics(),
            JvmThreadMetrics(),
            ProcessorMetrics(),
            ClassLoaderMetrics(),
            UptimeMetrics(),
        )
    }

    install(CallId) {
        // Retrieve X-Request-Id from upstream (reverse proxy) and reply with
        // it, so clients can correlate responses with their requests.
        header(HttpHeaders.XRequestId)
        generate { Uuid.random().toString() }
    }

    // Live websocket sessions, as a gauge over the session index.
    prometheusRegistry.gauge("ws.active_users", sessionManager) { sm ->
        sm.getSessionStats()["activeUsers"]?.toDouble() ?: 0.0
    }

    routing {
        get(METRICS_ROUTE) {
            if (metricsToken != null && call.request.header("X-Metrics-Token") != metricsToken) {
                call.respondText("Forbidden", ContentType.Text.Plain, HttpStatusCode.Forbidden)
            } else {
                call.respondText(prometheusRegistry.scrape(), ContentType.Text.Plain)
            }
        }

        get("/health") {
            val dbUp = runCatching {
                dbQuery { AuthSessions.selectAll().limit(1).count() }
            }.isSuccess
            if (dbUp) {
                call.respondText("""{"status":"ok"}""", ContentType.Application.Json)
            } else {
                call.respondText(
                    """{"status":"degraded","db":"down"}""",
                    ContentType.Application.Json,
                    HttpStatusCode.ServiceUnavailable,
                )
            }
        }
    }
}
