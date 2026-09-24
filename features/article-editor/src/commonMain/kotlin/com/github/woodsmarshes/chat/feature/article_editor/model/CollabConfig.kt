package com.github.woodsmarshes.chat.feature.article_editor.model

import kotlinx.serialization.Serializable

/**
 * Wire payload for the Tiptap editor WebView collaboration bootstrap.
 *
 * The Kotlin side serializes this with the shared project JSON and hands the
 * Base64 result to `window.__editorShell.initialize(...)`; the JS side does
 * `JSON.parse` on it. Building this by string interpolation was unsafe: any
 * `"` or `\` in the user display name produced invalid JSON and collaboration
 * silently died.
 */
@Serializable
data class CollabConfig(
    val collabUrl: String,
    val roomId: String,
    val token: String? = null,
    val userInfo: CollabUserInfo? = null,
)

@Serializable
data class CollabUserInfo(
    val name: String,
    val color: String,
)
