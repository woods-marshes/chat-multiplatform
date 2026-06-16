package com.github.woodsmarshes.chat.feature.article.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.github.woodsmarshes.chat.core.data.repository.ArticleRepository
import com.github.woodsmarshes.chat.core.model.ui.ArticleListUiModel
import com.github.woodsmarshes.chat.feature.article.model.ArticleListUiState
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ArticleListViewModel(
    private val articleRepository: ArticleRepository,
) : ViewModel() {
    private val log = KotlinLogging.logger {}

    private val _uiState = MutableStateFlow(ArticleListUiState())
    val uiState: StateFlow<ArticleListUiState> = _uiState.asStateFlow()

    /** 全部已发布文章 */
    val allArticles: Flow<PagingData<ArticleListUiModel>> =
        articleRepository.getArticles(getMyArticle = false)
            .cachedIn(viewModelScope)

    /** 当前登录用户的文章 */
    val myArticles: Flow<PagingData<ArticleListUiModel>> =
        articleRepository.getArticles(getMyArticle = true)
            .cachedIn(viewModelScope)

    fun selectTab(index: Int) {
        _uiState.value = _uiState.value.copy(selectedTabIndex = index)
    }

    fun showSortSheet() {
        _uiState.value = _uiState.value.copy(showSortSheet = true)
    }

    fun dismissSortSheet() {
        _uiState.value = _uiState.value.copy(showSortSheet = false)
    }
}
