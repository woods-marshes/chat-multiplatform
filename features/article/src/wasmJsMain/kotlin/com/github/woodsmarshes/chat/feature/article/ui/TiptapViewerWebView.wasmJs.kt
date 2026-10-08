package com.github.woodsmarshes.chat.feature.article.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import com.github.woodsmarshes.chat.features.article.resources.Res
import dev.nucleusframework.webview.jsbridge.IJsMessageHandler
import dev.nucleusframework.webview.jsbridge.JsMessage
import dev.nucleusframework.webview.jsbridge.rememberWebViewJsBridge
import dev.nucleusframework.webview.web.WebView
import dev.nucleusframework.webview.web.WebViewNavigator
import dev.nucleusframework.webview.web.rememberWebViewNavigator
import dev.nucleusframework.webview.web.rememberWebViewStateWithHTMLData
import io.github.oshai.kotlinlogging.KotlinLogging
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
    var htmlContent by remember(attempt) { mutableStateOf<String?>(null) }
    var preparationFailed by remember(attempt) { mutableStateOf(false) }

    LaunchedEffect(attempt) {
        preparationFailed = false
        htmlContent = null
        try {
            htmlContent = Res.readBytes("files/viewer.html").decodeToString()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error(e) { "Failed to load viewer.html asset" }
            preparationFailed = true
        }
    }

    val content = htmlContent
    if (content == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (preparationFailed) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = LocalStrings.current.articleLoadFailed,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = { attempt++ }) {
                        Text(LocalStrings.current.retry)
                    }
                }
            } else {
                CircularProgressIndicator()
            }
        }
        return
    }

    key(attempt) {
        TiptapViewerWebViewContent(
            content = content,
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
    content: String,
    jsonContentStr: String,
    onScrollUp: () -> Unit,
    onScrollDown: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    val currentOnScrollUp by rememberUpdatedState(onScrollUp)
    val currentOnScrollDown by rememberUpdatedState(onScrollDown)

    val state = rememberWebViewStateWithHTMLData(data = content)
    val navigator = rememberWebViewNavigator()
    val jsBridge = rememberWebViewJsBridge(navigator)

    var isJsReady by remember { mutableStateOf(false) }
    var readyTimedOut by remember { mutableStateOf(false) }

    // The JS handshake retries forever on the JS side; give up visibly after
    // a timeout instead of showing a spinner forever.
    LaunchedEffect(Unit) {
        delay(VIEWER_READY_TIMEOUT_MS)
        if (!isJsReady) readyTimedOut = true
    }

    DisposableEffect(jsBridge, state) {
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

                if (direction == "up") currentOnScrollUp() else currentOnScrollDown()
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
                isJsReady = true
                callback("ok")
            }
        }

        jsBridge.register(scrollHandler)
        jsBridge.register(readyHandler)

        onDispose {
            jsBridge.unregister(scrollHandler)
            jsBridge.unregister(readyHandler)
            isJsReady = false
        }
    }

    LaunchedEffect(jsonContentStr, isJsReady) {
        if (isJsReady) {
            navigator.evaluateJavaScript(
                buildViewerRenderScript(jsonContentStr),
            )
        }
    }

    if (readyTimedOut && !isJsReady) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = LocalStrings.current.articleLoadFailed,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onRetry) {
                    Text(LocalStrings.current.retry)
                }
            }
        }
        return
    }

    WebView(
        state = state,
        navigator = navigator,
        webViewJsBridge = jsBridge,
        modifier = modifier,
    )
}
