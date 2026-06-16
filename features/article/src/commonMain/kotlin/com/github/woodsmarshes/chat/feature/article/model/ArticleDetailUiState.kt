package com.github.woodsmarshes.chat.feature.article.model

import com.github.woodsmarshes.chat.core.model.Article

data class ArticleDetailUiState(
    val article: Article? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)
