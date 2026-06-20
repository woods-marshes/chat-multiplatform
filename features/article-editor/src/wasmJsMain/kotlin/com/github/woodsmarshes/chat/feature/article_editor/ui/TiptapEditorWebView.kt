package com.github.woodsmarshes.chat.feature.article_editor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import com.github.woodsmarshes.chat.features.article_editor.resources.Res
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
actual fun TiptapEditorWebView(
    initialTitle: String,
    initialJsonStr: String,
    onTitleChanged: (String) -> Unit,
    onContentChanged: (String) -> Unit,
    collabUrl: String?,
    roomId: String?,
    token: String?,
    userInfoName: String?,
    userInfoColor: String?,
    modifier: Modifier,
) {
    var htmlContent by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            htmlContent = Res.readBytes("files/editor.html").decodeToString()
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

    val currentOnTitleChanged by rememberUpdatedState(onTitleChanged)
    val currentOnContentChanged by rememberUpdatedState(onContentChanged)

    val state = rememberWebViewStateWithHTMLData(data = content)
    val navigator = rememberWebViewNavigator()
    val jsBridge = rememberWebViewJsBridge(navigator)

    var isJsReady by remember { mutableStateOf(false) }

    DisposableEffect(jsBridge, state) {
        val titleHandler = object : IJsMessageHandler {
            override fun methodName(): String = "onTitleChanged"

            override fun handle(
                message: JsMessage,
                navigator: WebViewNavigator?,
                callback: (String) -> Unit,
            ) {
                val title = runCatching {
                    json.parseToJsonElement(message.params).jsonObject["title"]?.jsonPrimitive?.content
                }.getOrNull() ?: ""
                currentOnTitleChanged(title)
                callback("ok")
            }
        }

        val contentHandler = object : IJsMessageHandler {
            override fun methodName(): String = "onContentChanged"

            override fun handle(
                message: JsMessage,
                navigator: WebViewNavigator?,
                callback: (String) -> Unit,
            ) {
                val jsonStr = runCatching {
                    json.parseToJsonElement(message.params).jsonObject["json"]?.jsonPrimitive?.content
                }.getOrNull() ?: "{}"
                currentOnContentChanged(jsonStr)
                callback("ok")
            }
        }

        val readyHandler = object : IJsMessageHandler {
            override fun methodName(): String = "onEditorReady"

            override fun handle(
                message: JsMessage,
                navigator: WebViewNavigator?,
                callback: (String) -> Unit,
            ) {
                isJsReady = true
                callback("ok")
            }
        }

        jsBridge.register(titleHandler)
        jsBridge.register(contentHandler)
        jsBridge.register(readyHandler)

        onDispose {
            jsBridge.unregister(titleHandler)
            jsBridge.unregister(contentHandler)
            jsBridge.unregister(readyHandler)
            isJsReady = false
        }
    }

    // Only inject once when JS is ready — NEVER re-inject while user is typing
    LaunchedEffect(isJsReady) {
        if (isJsReady) {
            @OptIn(ExperimentalEncodingApi::class)
            val titleB64 = Base64.encode(initialTitle.encodeToByteArray())
            val jsonB64 = Base64.encode(initialJsonStr.encodeToByteArray())

            val collabJsonStr = if (collabUrl != null && roomId != null) {
                """
                {
                  "collabUrl": "$collabUrl",
                  "roomId": "$roomId",
                  "token": ${if (token != null) "\"$token\"" else "null"},
                  "userInfo": {
                    "name": "${userInfoName ?: "Anonymous"}",
                    "color": "${userInfoColor ?: "#ffcc00"}"
                  }
                }
                """.trimIndent()
            } else null

            val collabB64 = collabJsonStr?.let { Base64.encode(it.encodeToByteArray()) }

            val jsCall = if (collabB64 != null) {
                "window.__editorShell.initialize(" +
                        "decodeURIComponent(escape(window.atob(\"$titleB64\"))), " +
                        "decodeURIComponent(escape(window.atob(\"$jsonB64\"))), " +
                        "decodeURIComponent(escape(window.atob(\"$collabB64\"))));"
            } else {
                "window.__editorShell.initialize(" +
                        "decodeURIComponent(escape(window.atob(\"$titleB64\"))), " +
                        "decodeURIComponent(escape(window.atob(\"$jsonB64\")));"
            }

            navigator.evaluateJavaScript(jsCall)
        }
    }

    WebView(
        state = state,
        navigator = navigator,
        webViewJsBridge = jsBridge,
        modifier = modifier,
    )
}
