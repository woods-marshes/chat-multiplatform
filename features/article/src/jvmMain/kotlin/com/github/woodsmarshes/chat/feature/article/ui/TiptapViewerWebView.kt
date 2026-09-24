package com.github.woodsmarshes.chat.feature.article.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.github.woodsmarshes.chat.core.common.webview.DesktopWebViewResources
import com.github.woodsmarshes.chat.core.common.webview.prepareDesktopWebViewResources
import com.github.woodsmarshes.chat.features.article.resources.Res
import dev.nucleusframework.webview.jsbridge.IJsMessageHandler
import dev.nucleusframework.webview.jsbridge.JsMessage
import dev.nucleusframework.webview.jsbridge.rememberWebViewJsBridge
import dev.nucleusframework.webview.web.rememberWebViewNavigator
import dev.nucleusframework.webview.web.rememberWebViewState
import dev.nucleusframework.webview.web.WebView
import dev.nucleusframework.webview.web.WebViewNavigator
import dev.nucleusframework.webview.web.windows.WindowsWebView2NativeWebView
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.UUID
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive


private val json = Json { ignoreUnknownKeys = true }
private val log = KotlinLogging.logger {}
private const val VIEWER_READY_TIMEOUT_MS = 10_000L

@Composable
actual fun TiptapViewerWebView(
    jsonContentStr: String,
    onScrollUp: () -> Unit,
    onScrollDown: () -> Unit,
    modifier: Modifier,
) {
    var attempt by remember { mutableStateOf(0) }
    var resources by remember(attempt) { mutableStateOf<DesktopWebViewResources?>(null) }
    var preparationFailed by remember(attempt) { mutableStateOf(false) }

    LaunchedEffect(attempt) {
        preparationFailed = false
        resources = null
        try {
            resources = prepareDesktopWebViewResources(
                shellName = "viewer",
                content = Res.readBytes("files/viewer.html"),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error(e) { "Failed to prepare viewer shell" }
            preparationFailed = true
        }
    }

    val preparedResources = resources
    if (preparedResources == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (preparationFailed) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Unable to prepare viewer resources")
                    TextButton(onClick = { attempt++ }) { Text("Retry") }
                }
            } else {
                CircularProgressIndicator()
            }
        }
        return
    }

    key(attempt) {
        TiptapViewerWebViewContent(
            resources = preparedResources,
            jsonContentStr = jsonContentStr,
            onScrollUp = onScrollUp,
            onScrollDown = onScrollDown,
            onRetry = { attempt++ },
            modifier = modifier,
        )
    }
}

@Composable
private fun TiptapViewerWebViewContent(
    resources: DesktopWebViewResources,
    onRetry: () -> Unit,
    jsonContentStr: String,
    onScrollUp: () -> Unit,
    onScrollDown: () -> Unit,
    modifier: Modifier,
) {
    val currentOnScrollUp by rememberUpdatedState(onScrollUp)
    val currentOnScrollDown by rememberUpdatedState(onScrollDown)

    val sessionId = remember { UUID.randomUUID().toString() }
    val state = rememberWebViewState(url = resources.shellUrl)
    state.webSettings.desktopWebSettings.dataDirectory = resources.dataDirectory
    val navigator = rememberWebViewNavigator()
    val jsBridge = rememberWebViewJsBridge(navigator)

    val requestId = remember(jsonContentStr) { UUID.randomUUID().toString() }
    val currentRequestId by rememberUpdatedState(requestId)
    var completedRequestId by remember { mutableStateOf<String?>(null) }
    var failedRequestId by remember { mutableStateOf<String?>(null) }
    var timedOutRequestId by remember { mutableStateOf<String?>(null) }
    var isJsReady by remember { mutableStateOf(false) }
    var readyTimedOut by remember { mutableStateOf(false) }

    // TEMP diagnostics for the first-open handshake stall (upstream report).
    LaunchedEffect(state) {
        snapshotFlow { state.loadingState }.collect { loading ->
            log.info { "[viewer:$sessionId] loadingState=$loading" }
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.webView }.collect { wv ->
            if (wv != null) log.info { "[viewer:$sessionId] native instance available" }
        }
    }

    // Start the handshake deadline only after the native instance is available.
    LaunchedEffect(state.webView, isJsReady) {
        readyTimedOut = false
        if (state.webView != null && !isJsReady) {
            delay(VIEWER_READY_TIMEOUT_MS)
            readyTimedOut = true
            log.warn { "[viewer:$sessionId] bridge handshake timed out" }
        }
    }

    DisposableEffect(jsBridge, state) {
        var active = true
        val scrollHandler = object : IJsMessageHandler {
            override fun methodName(): String = "onScrollDirectionChanged"

            override fun handle(
                message: JsMessage,
                navigator: WebViewNavigator?,
                callback: (String) -> Unit,
            ) {
                val direction = runCatching {
                    json.parseToJsonElement(message.params).jsonObject["direction"]?.jsonPrimitive?.content
                }.getOrNull() ?: "up"

                if (active) {
                    if (direction == "up") currentOnScrollUp() else currentOnScrollDown()
                }
                callback("ok")
            }
        }

        val readyHandler = object : IJsMessageHandler {
            override fun methodName(): String = "onViewerReady"

            override fun handle(
                message: JsMessage,
                navigator: WebViewNavigator?,
                callback: (String) -> Unit,
            ) {
                log.info { "[viewer:$sessionId] bridge ready" }
                if (active) isJsReady = true
                callback("ok")
            }
        }

        val contentResultHandler = object : IJsMessageHandler {
            override fun methodName(): String = "onViewerContentResult"

            override fun handle(
                message: JsMessage,
                navigator: WebViewNavigator?,
                callback: (String) -> Unit,
            ) {
                val result = runCatching {
                    val payload = json.parseToJsonElement(message.params).jsonObject
                    payload["requestId"]?.jsonPrimitive?.content to
                        payload["status"]?.jsonPrimitive?.content
                }.getOrNull()
                val id = result?.first
                val status = result?.second
                if (active && id == currentRequestId) {
                    when (status) {
                        "committed" -> completedRequestId = id
                        "failed" -> failedRequestId = id
                    }
                    log.info { "[viewer:$sessionId] content request=$id status=$status" }
                }
                callback("ok")
            }
        }
        jsBridge.register(contentResultHandler)
        jsBridge.register(scrollHandler)
        jsBridge.register(readyHandler)

        onDispose {
            active = false
            jsBridge.unregister(contentResultHandler)
            jsBridge.unregister(scrollHandler)
            jsBridge.unregister(readyHandler)
            isJsReady = false
        }
    }

    LaunchedEffect(requestId, isJsReady) {
        if (isJsReady) {
            @OptIn(ExperimentalEncodingApi::class)
            val base64Str = Base64.encode(jsonContentStr.encodeToByteArray())
            navigator.evaluateJavaScript(
                "window.__viewerShell.renderContent(decodeURIComponent(escape(window.atob(\"$base64Str\"))), \"$requestId\");"
            )
        }
    }

    LaunchedEffect(requestId, isJsReady, completedRequestId, failedRequestId) {
        if (isJsReady && completedRequestId != requestId && failedRequestId != requestId) {
            delay(VIEWER_READY_TIMEOUT_MS)
            timedOutRequestId = requestId
            log.warn { "[viewer:$sessionId] content request=$requestId timed out" }
        }
    }

    Box(modifier = modifier) {
        // Retained 1.0.3 mitigation; two frames do not guarantee transition completion.
        var nativeViewReady by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            withFrameNanos { }
            nativeViewReady = true
        }

        if (nativeViewReady) {
            WebView(
                state = state,
                navigator = navigator,
                webViewJsBridge = jsBridge,
                // Avoid intentionally tiny native surfaces during initialization.
                modifier = Modifier.fillMaxSize(),
            )
            // Temporary 1.0.3 mitigation for observed Windows residual frames.
            // Disposal ordering and compositor cleanup are not guaranteed by this workaround.
            // Remove after an upstream cleanup fix passes create/dispose regression tests.
            // Capture this instance rather than reading mutable state during disposal.
            val createdWebView = state.webView
            DisposableEffect(createdWebView) {
                onDispose {
                    (createdWebView?.nativeWebView as? WindowsWebView2NativeWebView)
                        ?.asPlatformView()
                        ?.setBounds(30000, 30000, 1, 1)
                }
            }
        }
        if (!isJsReady || completedRequestId != requestId) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                val error = when {
                    !isJsReady && readyTimedOut -> "Viewer initialization timed out"
                    failedRequestId == requestId -> "Unable to process article content"
                    timedOutRequestId == requestId -> "Article content confirmation timed out"
                    else -> null
                }
                if (error != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error)
                        TextButton(onClick = onRetry) { Text("Retry") }
                    }
                } else {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
