package com.github.woodsmarshes.chat.feature.article_editor.ui

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
import com.github.woodsmarshes.chat.features.article_editor.resources.Res
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
    var attempt by remember { mutableStateOf(0) }
    var htmlContent by remember(attempt) { mutableStateOf<String?>(null) }
    var preparationFailed by remember(attempt) { mutableStateOf(false) }

    LaunchedEffect(attempt) {
        preparationFailed = false
        htmlContent = null
        try {
            htmlContent = Res.readBytes("files/editor.html").decodeToString()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error(e) { "Failed to load editor.html asset" }
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
        val currentOnTitleChanged by rememberUpdatedState(onTitleChanged)
        val currentOnContentChanged by rememberUpdatedState(onContentChanged)

        val state = rememberWebViewStateWithHTMLData(data = content)
        val navigator = rememberWebViewNavigator()
        val jsBridge = rememberWebViewJsBridge(navigator)

        var isJsReady by remember { mutableStateOf(false) }
        var readyTimedOut by remember { mutableStateOf(false) }

        // The JS handshake retries forever on the JS side; give up visibly after
        // a timeout instead of showing a spinner forever.
        LaunchedEffect(Unit) {
            delay(EDITOR_READY_TIMEOUT_MS)
            if (!isJsReady) readyTimedOut = true
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
                val jsCall = buildEditorInitializeScript(
                    initialTitle = initialTitle,
                    initialJsonStr = initialJsonStr,
                    collabUrl = collabUrl,
                    roomId = roomId,
                    token = token,
                    userInfoName = userInfoName,
                    userInfoColor = userInfoColor,
                )
                navigator.evaluateJavaScript(jsCall)
            }
        }

        if (readyTimedOut && !isJsReady) {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = LocalStrings.current.articleLoadFailed,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = { attempt++ }) {
                        Text(LocalStrings.current.retry)
                    }
                }
            }
        } else {
            WebView(
                state = state,
                navigator = navigator,
                webViewJsBridge = jsBridge,
                modifier = modifier,
            )
        }
    }
}
