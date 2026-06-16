package com.github.woodsmarshes.chat.feature.article.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.github.woodsmarshes.chat.core.model.ui.ArticleListUiModel
import com.github.woodsmarshes.chat.core.ui.components.ChatTopAppBar
import com.github.woodsmarshes.chat.core.ui.components.item.articleItems
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleListScreen(
    onArticleClick: (id: Uuid, authorId: Uuid) -> Unit,
    onCreateClick: () -> Unit,
    viewModel: ArticleListViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val pagerState = rememberPagerState(pageCount = { 2 })

    // Sync tab → pager
    LaunchedEffect(uiState.selectedTabIndex) {
        pagerState.animateScrollToPage(uiState.selectedTabIndex)
    }
    // Sync pager → tab (only after scroll settles)
    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress) {
            viewModel.selectTab(pagerState.currentPage)
        }
    }

    Scaffold(
        topBar = {
            ChatTopAppBar(
                title = LocalStrings.current.articleTitle,
                actions = {
                    IconButton(onClick = viewModel::showSortSheet) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = LocalStrings.current.articleSortCd)
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateClick) {
                Icon(Icons.Default.Add, contentDescription = LocalStrings.current.articleCreateCd)
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Tabs: 全部 | 我的
            PrimaryTabRow(
                selectedTabIndex = uiState.selectedTabIndex,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Tab(
                    selected = uiState.selectedTabIndex == 0,
                    onClick = { viewModel.selectTab(0) },
                    text = { Text(LocalStrings.current.articleAllTab) },
                )
                Tab(
                    selected = uiState.selectedTabIndex == 1,
                    onClick = { viewModel.selectTab(1) },
                    text = { Text(LocalStrings.current.articleMyTab) },
                )
            }

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
                    onArticleClick = onArticleClick,
                )
            }
        }
    }

    // ---- Sort bottom sheet ----
    if (uiState.showSortSheet) {
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissSortSheet,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Text(
                    text = LocalStrings.current.articleSortTitle,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(16.dp))
                // TODO: sort options
                Text(
                    text = LocalStrings.current.articleSortComingSoon,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

// ---- Extracted list content (shared by all / my tabs) ----

@Composable
private fun ArticleListContent(
    articlesFlow: Flow<PagingData<ArticleListUiModel>>,
    onArticleClick: (id: Uuid, authorId: Uuid) -> Unit,
) {
    val articles = articlesFlow.collectAsLazyPagingItems()

    when {
        articles.loadState.refresh is LoadState.Loading && articles.itemCount == 0 -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        articles.loadState.refresh is LoadState.Error && articles.itemCount == 0 -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = LocalStrings.current.loadFailed,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        articles.itemCount == 0 && articles.loadState.refresh is LoadState.NotLoading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = LocalStrings.current.articleNoArticles,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        else -> {
            val isRefreshing = articles.loadState.refresh is LoadState.Loading
            val pullState = rememberPullToRefreshState()

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { articles.refresh() },
                modifier = Modifier.fillMaxSize(),
                state = pullState,
                indicator = {
                    Indicator(
                        modifier = Modifier.align(Alignment.TopCenter),
                        isRefreshing = isRefreshing,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        state = pullState,
                    )
                },
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    articleItems(
                        itemCount = articles.itemCount,
                        itemProvider = { articles[it] },
                        onArticleClick = onArticleClick,
                    )

                    when (articles.loadState.append) {
                        is LoadState.Loading -> {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                        is LoadState.Error -> {
                            item {
                                Text(
                                    text = LocalStrings.current.articleLoadMoreFailed,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                )
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}
