package com.github.woodsmarshes.chat.feature.article_editor.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.github.woodsmarshes.chat.feature.article_editor.ui.ArticleEditorScreen
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class ArticleEditorNavKey(val id: Uuid? = null) : NavKey

fun EntryProviderScope<NavKey>.editorEntry(
    onBack: () -> Unit,
    metadata: Map<String, Any> = emptyMap(),
) {
    entry<ArticleEditorNavKey>(metadata = metadata) { key ->
        ArticleEditorScreen(
            articleId = key.id,
            onBack = onBack,
        )
    }
}
