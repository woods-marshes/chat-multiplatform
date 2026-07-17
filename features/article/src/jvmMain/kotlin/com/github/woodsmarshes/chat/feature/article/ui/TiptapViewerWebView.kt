package com.github.woodsmarshes.chat.feature.article.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.woodsmarshes.chat.features.article.resources.Res
import io.github.kdroidfilter.webview.jsbridge.IJsMessageHandler
import io.github.kdroidfilter.webview.jsbridge.JsMessage
import io.github.kdroidfilter.webview.jsbridge.rememberWebViewJsBridge
import io.github.kdroidfilter.webview.web.WebView
import io.github.kdroidfilter.webview.web.WebViewNavigator
import io.github.kdroidfilter.webview.web.rememberWebViewNavigator
import io.github.kdroidfilter.webview.web.rememberWebViewStateWithHTMLData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private val json = Json { ignoreUnknownKeys = true }

@Composable
actual fun TiptapViewerWebView(
    jsonContentStr: String,
    onScrollUp: () -> Unit,
    onScrollDown: () -> Unit,
    modifier: Modifier,
) {
    var htmlContent by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            htmlContent = Res.readBytes("files/viewer.html").decodeToString()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val content = htmlContent
    if (content == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    TiptapViewerWebViewContent(
        content = content,
        jsonContentStr = jsonContentStr,
        onScrollUp = onScrollUp,
        onScrollDown = onScrollDown,
        modifier = modifier,
    )
}

@Composable
private fun TiptapViewerWebViewContent(
    content: String,
    jsonContentStr: String,
    onScrollUp: () -> Unit,
    onScrollDown: () -> Unit,
    modifier: Modifier,
) {
    val currentOnScrollUp by rememberUpdatedState(onScrollUp)
    val currentOnScrollDown by rememberUpdatedState(onScrollDown)

    val state = rememberWebViewStateWithHTMLData(data = content)
    val navigator = rememberWebViewNavigator()
    val jsBridge = rememberWebViewJsBridge(navigator)

    var isJsReady by remember { mutableStateOf(false) }

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
            @OptIn(ExperimentalEncodingApi::class)
            val base64Str = Base64.encode(jsonContentStr.encodeToByteArray())
            navigator.evaluateJavaScript(
                "window.__viewerShell.renderContent(decodeURIComponent(escape(window.atob(\"$base64Str\"))));"
            )
        }
    }

    Box(modifier = modifier) {
        WebView(
            state = state,
            navigator = navigator,
            webViewJsBridge = jsBridge,
            modifier = if (isJsReady) Modifier.fillMaxSize() else Modifier.size(1.dp),
        )
        if (!isJsReady) {
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}
