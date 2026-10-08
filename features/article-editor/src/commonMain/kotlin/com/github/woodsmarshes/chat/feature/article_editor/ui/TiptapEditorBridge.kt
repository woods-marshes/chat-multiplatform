package com.github.woodsmarshes.chat.feature.article_editor.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.woodsmarshes.chat.core.network.serialization.ProjectJson
import com.github.woodsmarshes.chat.feature.article_editor.model.CollabConfig
import com.github.woodsmarshes.chat.feature.article_editor.model.CollabUserInfo
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Composable
expect fun TiptapEditorWebView(
    initialTitle: String,
    initialJsonStr: String,
    onTitleChanged: (String) -> Unit,
    onContentChanged: (String) -> Unit,
    collabUrl: String? = null,
    roomId: String? = null,
    token: String? = null,
    userInfoName: String? = null,
    userInfoColor: String? = null,
    modifier: Modifier = Modifier,
)

/**
 * Builds the JavaScript expression that initializes `window.__editorShell` inside the WebView.
 *
 * Uses UTF-8 + Base64 + `decodeURIComponent(escape(window.atob(...)))` so arbitrary
 * titles, Tiptap JSON documents, and collaboration configs (containing CJK, emoji,
 * quotes, backslashes, or newlines) cross the JS bridge without escaping hazards.
 */
@OptIn(ExperimentalEncodingApi::class)
internal fun buildEditorInitializeScript(
    initialTitle: String,
    initialJsonStr: String,
    collabUrl: String? = null,
    roomId: String? = null,
    token: String? = null,
    userInfoName: String? = null,
    userInfoColor: String? = null,
): String {
    val titleExpr = decodeBase64Utf8JsExpr(initialTitle)
    val jsonExpr = decodeBase64Utf8JsExpr(initialJsonStr)

    val collabJsonStr = if (collabUrl != null && roomId != null) {
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
            ),
        )
    } else {
        null
    }

    val args = if (collabJsonStr != null) {
        "$titleExpr, $jsonExpr, ${decodeBase64Utf8JsExpr(collabJsonStr)}"
    } else {
        "$titleExpr, $jsonExpr"
    }
    return "window.__editorShell.initialize($args);"
}

@OptIn(ExperimentalEncodingApi::class)
private fun decodeBase64Utf8JsExpr(value: String): String {
    val encoded = Base64.encode(value.encodeToByteArray())
    return "decodeURIComponent(escape(window.atob(\"$encoded\")))"
}
