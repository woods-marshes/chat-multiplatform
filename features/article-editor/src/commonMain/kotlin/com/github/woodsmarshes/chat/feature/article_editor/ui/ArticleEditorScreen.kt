package com.github.woodsmarshes.chat.feature.article_editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.woodsmarshes.chat.core.model.ArticleStatus
import com.github.woodsmarshes.chat.core.ui.components.AppProgressIndicator
import com.github.woodsmarshes.chat.core.ui.components.ChatTopAppBar
import com.github.woodsmarshes.chat.core.ui.components.state.ErrorContent
import com.github.woodsmarshes.chat.core.ui.components.state.LoadingContent
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.uuid.Uuid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleEditorScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    articleId: Uuid? = null,
    viewModel: ArticleEditorViewModel = koinViewModel { parametersOf(articleId) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = LocalStrings.current

    // A save is only meaningful once the editor holds the real document: before
    // that, the body is still the placeholder default.
    val canSave = uiState.isLoaded && !uiState.isSaving && !uiState.isCollaborativeEditing

    Scaffold(
        modifier = modifier,
        topBar = {
            ChatTopAppBar(
                title = if (uiState.isNew) strings.articleNewTitle else strings.articleEditTitle,
                showBackButton = true,
                onBackClick = onBack,
                actions = {
                    TextButton(
                        onClick = { viewModel.saveArticle(ArticleStatus.DRAFT) },
                        enabled = canSave,
                    ) { Text(strings.articleSaveDraft) }
                    TextButton(
                        onClick = { viewModel.saveArticle(ArticleStatus.PUBLISHED) },
                        enabled = canSave,
                    ) { Text(strings.articlePublish) }
                },
            )
        },
    ) { innerPadding ->
        val loadFailed = uiState.error != null && uiState.roomId == null
        if (uiState.isLoading) {
            LoadingContent(modifier = Modifier.fillMaxSize().padding(innerPadding))
        } else if (loadFailed) {
            ErrorContent(
                message = uiState.error.orEmpty(),
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                onRetry = viewModel::reload,
            )
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                if (uiState.error != null) {
                    Text(
                        text = uiState.error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                TiptapEditorWebView(
                    initialTitle = uiState.title,
                    initialJsonStr = uiState.contentJsonStr,
                    onTitleChanged = viewModel::updateTitle,
                    onContentChanged = viewModel::updateContent,
                    collabUrl = uiState.collabUrl,
                    roomId = uiState.roomId,
                    token = uiState.token,
                    userInfoName = uiState.userInfoName,
                    userInfoColor = uiState.userInfoColor,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onBack()
    }

    // Saving overlay — blocks interaction and shows spinner
    if (uiState.isSaving) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = { /* consume clicks */ },
                ),
            contentAlignment = Alignment.Center,
        ) {
            AppProgressIndicator(color = Color.White)
        }
    }
}
