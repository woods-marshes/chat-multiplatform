package com.github.woodsmarshes.chat.core.ui.components.item

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.woodsmarshes.chat.core.model.ArticleStatus
import com.github.woodsmarshes.chat.core.model.ui.ArticleListUiModel
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import kotlin.uuid.Uuid

/**
 * Renders article cards into a [LazyListScope] with per-item keys.
 *
 * Usage in a LazyColumn:
 * ```
 * LazyColumn {
 *     articleItems(
 *         itemCount = lazyArticles.itemCount,
 *         itemProvider = { lazyArticles[it] },
 *         onArticleClick = { id -> ... },
 *     )
 * }
 * ```
 */
fun LazyListScope.articleItems(
    itemCount: Int,
    itemProvider: (Int) -> ArticleListUiModel?,
    onArticleClick: (id: Uuid, authorId: Uuid) -> Unit,
) {
    items(
        count = itemCount,
        key = { index -> itemProvider(index)?.id?.toString() ?: index },
    ) { index ->
        val article = itemProvider(index) ?: return@items
        ArticleCardItem(
            article = article,
            onClick = { onArticleClick(article.id, article.authorId) },
        )
    }
}

@Composable
fun ArticleCardItem(
    article: ArticleListUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = article.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val excerpt = article.excerpt
                    if (!excerpt.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = excerpt,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when (article.status) {
                        ArticleStatus.DRAFT -> LocalStrings.current.articleStatusDraft
                        ArticleStatus.PUBLISHED -> LocalStrings.current.articleStatusPublished
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when (article.status) {
                        ArticleStatus.PUBLISHED -> MaterialTheme.colorScheme.primary
                        ArticleStatus.DRAFT -> MaterialTheme.colorScheme.error
                    },
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = article.authorDisplayName ?: article.authorUsername,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
