package com.github.woodsmarshes.chat.feature.article_editor.ui

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import com.github.woodsmarshes.chat.core.common.webview.DesktopWebViewResources
import com.github.woodsmarshes.chat.core.common.webview.prepareDesktopWebViewResources
import kotlinx.coroutines.CancellationException
import java.util.UUID
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.github.woodsmarshes.chat.features.article_editor.resources.Res
import dev.nucleusframework.webview.jsbridge.IJsMessageHandler
import dev.nucleusframework.webview.jsbridge.JsMessage
import dev.nucleusframework.webview.jsbridge.rememberWebViewJsBridge
import dev.nucleusframework.webview.web.WebView
import dev.nucleusframework.webview.web.WebViewNavigator
import dev.nucleusframework.webview.web.rememberWebViewNavigator
import dev.nucleusframework.webview.web.rememberWebViewState
import dev.nucleusframework.webview.web.windows.WindowsWebView2NativeWebView
import com.github.woodsmarshes.chat.core.network.serialization.ProjectJson
import com.github.woodsmarshes.chat.feature.article_editor.model.CollabConfig
import com.github.woodsmarshes.chat.feature.article_editor.model.CollabUserInfo
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import androidx.compose.material3.Text
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private val json = Json { ignoreUnknownKeys = true }
private val log = KotlinLogging.logger {}
private const val EDITOR_READY_TIMEOUT_MS = 10_000L

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
    var resources by remember { mutableStateOf<DesktopWebViewResources?>(null) }
    var preparationFailed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            resources = prepareDesktopWebViewResources(
                shellName = "editor",
                content = Res.readBytes("files/editor.html"),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error(e) { "Failed to prepare editor shell" }
            preparationFailed = true
        }
    }

    val preparedResources = resources
    if (preparedResources == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (preparationFailed) {
                Text("Unable to prepare editor resources")
            } else {
                CircularProgressIndicator()
            }
        }
        return
    }

    val currentOnTitleChanged by rememberUpdatedState(onTitleChanged)
    val currentOnContentChanged by rememberUpdatedState(onContentChanged)

    val sessionId = remember { UUID.randomUUID().toString() }
    val state = rememberWebViewState(url = preparedResources.shellUrl)
    state.webSettings.desktopWebSettings.dataDirectory = preparedResources.dataDirectory
    val navigator = rememberWebViewNavigator()
    val jsBridge = rememberWebViewJsBridge(navigator)

    var isJsReady by remember { mutableStateOf(false) }
    var readyTimedOut by remember { mutableStateOf(false) }

    // Start the handshake deadline only after the native instance is available.
    LaunchedEffect(state.webView, isJsReady) {
        readyTimedOut = false
        if (state.webView != null && !isJsReady) {
            delay(EDITOR_READY_TIMEOUT_MS)
            readyTimedOut = true
            log.warn { "[editor:$sessionId] bridge handshake timed out" }
        }
    }

    DisposableEffect(jsBridge, state) {
        val titleHandler = object : IJsMessageHandler {
            override fun methodName(): String = "onTitleChanged"

            override fun handle(
                message: JsMessage,
                navigator: WebViewNavigator?,
                callback: (String) -> Unit,
            ) {
                // A malformed payload must be dropped, not turned into an empty title:
                // the fallback would be persisted as the article title on the next save.
                val title = runCatching {
                    json.parseToJsonElement(message.params).jsonObject["title"]?.jsonPrimitive?.content
                }.getOrNull()
                if (title == null) {
                    log.warn { "[editor] ignoring malformed onTitleChanged payload" }
                } else {
                    currentOnTitleChanged(title)
                }
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
                // A malformed payload must be dropped, not replaced with "{}": the fallback
                // would become the article body on the next save and wipe the real content.
                val jsonStr = runCatching {
                    json.parseToJsonElement(message.params).jsonObject["json"]?.jsonPrimitive?.content
                }.getOrNull()
                if (jsonStr == null) {
                    log.warn { "[editor] ignoring malformed onContentChanged payload" }
                } else {
                    currentOnContentChanged(jsonStr)
                }
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
                log.info { "[editor:$sessionId] bridge ready" }
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
                // Serialized, not interpolated: user-controlled display names
                // may contain quotes or backslashes.
                ProjectJson.encodeToString(
                    CollabConfig.serializer(),
                    CollabConfig(
                        collabUrl = collabUrl,
                        roomId = roomId,
                        token = token,
                        userInfo = CollabUserInfo(
                            name = userInfoName ?: "Anonymous",
                            color = userInfoColor ?: "#ffcc00",
                        ),
                    )
                )
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

    // Retained 1.0.3 mitigation; two frames do not guarantee transition completion.
    var nativeViewReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        nativeViewReady = true
    }
    if (!nativeViewReady) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        WebView(
            state = state,
            navigator = navigator,
            webViewJsBridge = jsBridge,
            modifier = Modifier.fillMaxSize(),
        )
        // Keep the instance alive so a late handshake can recover after a timeout.
        if (!isJsReady) {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                if (readyTimedOut) {
                    Text("Editor initialization timed out")
                } else {
                    CircularProgressIndicator()
                }
            }
        }
    }
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
