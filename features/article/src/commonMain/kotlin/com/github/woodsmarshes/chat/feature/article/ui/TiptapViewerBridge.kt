package com.github.woodsmarshes.chat.feature.article.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Composable
expect fun TiptapViewerWebView(
    jsonContentStr: String,
    onScrollUp: () -> Unit,
    onScrollDown: () -> Unit,
    modifier: Modifier = Modifier,
)

/**
 * Builds the JavaScript expression that invokes `window.__viewerShell.renderContent(...)`
 * inside the viewer WebView using the UTF-8 Base64 bridge protocol.
 */
@OptIn(ExperimentalEncodingApi::class)
internal fun buildViewerRenderScript(
    jsonContentStr: String,
    requestId: String? = null,
): String {
    val encoded = Base64.encode(jsonContentStr.encodeToByteArray())
    val contentExpr = "decodeURIComponent(escape(window.atob(\"$encoded\")))"
    return if (requestId != null) {
        val escapedRequestId = requestId
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        "window.__viewerShell.renderContent($contentExpr, \"$escapedRequestId\");"
    } else {
        "window.__viewerShell.renderContent($contentExpr);"
    }
}
