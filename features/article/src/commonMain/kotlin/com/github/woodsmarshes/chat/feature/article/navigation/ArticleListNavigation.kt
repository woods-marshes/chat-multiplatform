package com.github.woodsmarshes.chat.feature.article.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.github.woodsmarshes.chat.feature.article.ui.ArticleDetailScreen
import com.github.woodsmarshes.chat.feature.article.ui.ArticleListScreen
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Serializable
data object ArticleListNavKey : NavKey

@Serializable
data class ArticleDetailNavKey(val id: Uuid, val authorId: Uuid) : NavKey

fun EntryProviderScope<NavKey>.articleListEntry(
    onArticleClick: (id: Uuid, authorId: Uuid) -> Unit,
    onCreateClick: () -> Unit,
    metadata: Map<String, Any> = emptyMap(),
) {
    entry<ArticleListNavKey>(metadata = metadata) {
        ArticleListScreen(
            onArticleClick = onArticleClick,
            onCreateClick = onCreateClick,
        )
    }
}

fun EntryProviderScope<NavKey>.articleDetailEntry(
    onBack: () -> Unit,
    onEditClick: (Uuid) -> Unit,
    metadata: Map<String, Any> = emptyMap(),
) {
    entry<ArticleDetailNavKey>(metadata = metadata) { key ->
        ArticleDetailScreen(
            articleId = key.id,
            viewModel = koinViewModel(
                key = "article_detail_${key.id}",
                parameters = { parametersOf(key.id, key.authorId) },
            ),
            onBack = onBack,
            onEditClick = onEditClick,
        )
    }
}
