package com.github.woodsmarshes.chat.feature.article.model

data class ArticleListUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val selectedTabIndex: Int = 0, // 0 = 全部, 1 = 我的
    val showSortSheet: Boolean = false,
)
