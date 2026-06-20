package com.github.woodsmarshes.chat.feature.article.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.github.woodsmarshes.chat.core.network.serialization.ProjectJson
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
) {
    val uiState by viewModel.uiState.collectAsState()
    val isOwnArticle by viewModel.isOwnArticle.collectAsState()

    var fabVisible by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(uiState.article?.title ?: LocalStrings.current.articleTitle)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = LocalStrings.current.backCd,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = fabVisible
                        && (uiState.article != null)
                        && (isOwnArticle || uiState.article?.stats?.allowCollaboration == true),
                enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
            ) {
                ExtendedFloatingActionButton(
                    onClick = { onEditClick(articleId) },
                    icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    text = { Text(
                        if (isOwnArticle)
                            LocalStrings.current.articleEditFab
                        else
                            LocalStrings.current.articleCollaborativeEditFab
                    ) },
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val article = uiState.article

            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.error != null -> {
                    Text(
                        text = uiState.error ?: LocalStrings.current.articleLoadFailed,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
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
            }
        }
    }
}
