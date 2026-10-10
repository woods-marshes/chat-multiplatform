package com.github.woodsmarshes.chat.feature.article.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.woodsmarshes.chat.core.network.serialization.ProjectJson
import com.github.woodsmarshes.chat.core.ui.components.ChatTopAppBar
import com.github.woodsmarshes.chat.core.ui.components.isInListDetailScene
import com.github.woodsmarshes.chat.core.ui.components.state.EmptyContent
import com.github.woodsmarshes.chat.core.ui.components.state.ErrorContent
import com.github.woodsmarshes.chat.core.ui.components.state.LoadingContent
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import kotlinx.serialization.json.JsonElement
import kotlin.uuid.Uuid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleDetailScreen(
    articleId: Uuid,
    onBack: () -> Unit,
    onEditClick: (Uuid) -> Unit,
    viewModel: ArticleDetailViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isOwnArticle by viewModel.isOwnArticle.collectAsStateWithLifecycle()

    var fabVisible by remember { mutableStateOf(true) }

    val article = uiState.article
    val canEdit = article != null &&
        (isOwnArticle || article.stats.allowCollaboration)
    val editLabel = if (isOwnArticle) {
        LocalStrings.current.articleEditFab
    } else {
        LocalStrings.current.articleCollaborativeEditFab
    }

    val inMultiPane = isInListDetailScene()

    Scaffold(
        modifier = modifier,
        topBar = {
            ChatTopAppBar(
                title = article?.title ?: LocalStrings.current.articleTitle,
                showBackButton = !inMultiPane,
                onBackClick = onBack,
            )
        },
        floatingActionButton = {
            ArticleEditFloatingActionButton(
                visible = canEdit && fabVisible,
                label = editLabel,
                onClick = { onEditClick(articleId) },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.isLoading -> {
                    LoadingContent(modifier = Modifier.fillMaxSize())
                }

                uiState.error != null -> {
                    ErrorContent(
                        message = uiState.error ?: LocalStrings.current.articleLoadFailed,
                        modifier = Modifier.fillMaxSize(),
                        onRetry = viewModel::retry,
                    )
                }

                article != null -> {
                    val jsonStr = remember(article) {
                        ProjectJson.encodeToString(JsonElement.serializer(), article.content)
                    }
                    TiptapViewerWebView(
                        jsonContentStr = jsonStr,
                        onScrollUp = { fabVisible = true },
                        onScrollDown = { fabVisible = false },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                else -> {
                    EmptyContent(
                        message = LocalStrings.current.articleNotFound,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
