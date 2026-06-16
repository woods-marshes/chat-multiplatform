package com.github.woodsmarshes.chat.feature.article.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CenterAlignedTopAppBar
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
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import kotlin.uuid.Uuid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleDetailScreen(
    articleId: Uuid,
    onBack: () -> Unit,
    onEditClick: (Uuid) -> Unit,
    viewModel: ArticleDetailViewModel,
) {
    val isOwnArticle by viewModel.isOwnArticle.collectAsState()

    // TODO: FAB visibility should be driven by scroll direction reported
    // from the WebView JS bridge (kmpJsBridge.callNative('scrollUp'/'scrollDown')).
    // For now, always visible when the article is the user's own.
    var fabVisible by remember { mutableStateOf(isOwnArticle) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(LocalStrings.current.articleTitle) },
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
                visible = fabVisible && isOwnArticle,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
            ) {
                ExtendedFloatingActionButton(
                    onClick = { onEditClick(articleId) },
                    icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    text = { Text("编辑") },
                )
            }
        },
    ) { innerPadding ->
        // WebView placeholder — the article body will be rendered here
        // via ComposeNativeWebView loading a viewer.html shell (Phase 3).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "文章内容即将上线",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
