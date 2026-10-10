package com.github.woodsmarshes.chat.feature.article.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.github.woodsmarshes.chat.core.model.ui.ArticleListUiModel
import com.github.woodsmarshes.chat.core.ui.components.AppFloatingActionButton
import com.github.woodsmarshes.chat.core.ui.components.AppModalBottomSheet
import com.github.woodsmarshes.chat.core.ui.components.AppProgressIndicator
import com.github.woodsmarshes.chat.core.ui.components.AppPullToRefreshBox
import com.github.woodsmarshes.chat.core.ui.components.AppTabRow
import com.github.woodsmarshes.chat.core.ui.components.ChatTopAppBar
import com.github.woodsmarshes.chat.core.ui.components.isInListDetailScene
import com.github.woodsmarshes.chat.core.ui.components.item.articleItems
import com.github.woodsmarshes.chat.core.ui.components.state.EmptyContent
import com.github.woodsmarshes.chat.core.ui.components.state.ErrorContent
import com.github.woodsmarshes.chat.core.ui.components.state.LoadingContent
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid
import org.koin.compose.viewmodel.koinViewModel

private val log = KotlinLogging.logger {}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleListScreen(
    onArticleClick: (id: Uuid, authorId: Uuid) -> Unit,
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedArticleId: Uuid? = null,
    viewModel: ArticleListViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { 2 })
    val inMultiPane = isInListDetailScene()
    val strings = LocalStrings.current

    // Sync tab → pager
    LaunchedEffect(uiState.selectedTabIndex) {
        pagerState.animateScrollToPage(uiState.selectedTabIndex)
    }
    // Sync pager → tab (only after scroll settles)
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != uiState.selectedTabIndex) {
            viewModel.selectTab(pagerState.currentPage)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            ChatTopAppBar(
                title = strings.articleTitle,
                showAccountAffordance = true,
                actions = {
                    IconButton(onClick = viewModel::showSortSheet) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = strings.articleSortCd)
                    }
                },
            )
        },
        floatingActionButton = {
            AppFloatingActionButton(
                onClick = onCreateClick,
                icon = Icons.Default.Add,
                contentDescription = strings.articleCreateCd,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Tabs: 全部 | 我的
            AppTabRow(
                tabs = listOf(strings.articleAllTab, strings.articleMyTab),
                selectedTabIndex = uiState.selectedTabIndex,
                onTabSelected = viewModel::selectTab,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                withContour = false,
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                val flow = when (page) {
                    0 -> viewModel.allArticles
                    1 -> viewModel.myArticles
                    else -> viewModel.allArticles
                }
                ArticleListContent(
                    articlesFlow = flow,
                    selectedArticleId = if (inMultiPane) selectedArticleId else null,
                    onArticleClick = onArticleClick,
                )
            }
        }
    }

    // ---- Sort bottom sheet ----
    AppModalBottomSheet(
        show = uiState.showSortSheet,
        onDismissRequest = viewModel::dismissSortSheet,
        title = strings.articleSortTitle,
    ) {
        Text(
            text = strings.articleSortComingSoon,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ---- Extracted list content (shared by all / my tabs) ----

@Composable
private fun ArticleListContent(
    articlesFlow: Flow<PagingData<ArticleListUiModel>>,
    selectedArticleId: Uuid? = null,
    onArticleClick: (id: Uuid, authorId: Uuid) -> Unit,
) {
    val articles = articlesFlow.collectAsLazyPagingItems()
    val strings = LocalStrings.current

    val refreshState = articles.loadState.refresh
    if (refreshState is LoadState.Error) {
        LaunchedEffect(refreshState) {
            log.error(refreshState.error) { "Article list refresh failed" }
        }
    }

    when {
        articles.loadState.refresh is LoadState.Loading && articles.itemCount == 0 -> {
            LoadingContent(modifier = Modifier.fillMaxSize())
        }
        articles.loadState.refresh is LoadState.Error && articles.itemCount == 0 -> {
            ErrorContent(
                message = strings.loadFailed,
                modifier = Modifier.fillMaxSize(),
                onRetry = { articles.retry() },
            )
        }
        articles.itemCount == 0 && articles.loadState.refresh is LoadState.NotLoading -> {
            EmptyContent(
                message = strings.articleNoArticles,
                modifier = Modifier.fillMaxSize(),
            )
        }
        else -> {
            val isRefreshing = articles.loadState.refresh is LoadState.Loading

            AppPullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { articles.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    articleItems(
                        itemCount = articles.itemCount,
                        itemProvider = { articles[it] },
                        onArticleClick = onArticleClick,
                        selectedArticleId = selectedArticleId,
                    )

                    when (articles.loadState.append) {
                        is LoadState.Loading -> {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    AppProgressIndicator(size = 24.dp)
                                }
                            }
                        }
                        is LoadState.Error -> {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    TextButton(onClick = { articles.retry() }) {
                                        Text(
                                            text = "${strings.articleLoadMoreFailed} · ${strings.retry}",
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}
