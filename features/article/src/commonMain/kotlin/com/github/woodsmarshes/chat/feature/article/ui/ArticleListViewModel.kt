package com.github.woodsmarshes.chat.feature.article.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.github.woodsmarshes.chat.core.data.repository.ArticleRepository
import com.github.woodsmarshes.chat.core.data.repository.UserRepository
import com.github.woodsmarshes.chat.core.model.ui.ArticleListUiModel
import com.github.woodsmarshes.chat.feature.article.model.ArticleListUiState
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

class ArticleListViewModel(
    private val articleRepository: ArticleRepository,
    private val userRepository: UserRepository,
) : ViewModel() {
    private val log = KotlinLogging.logger {}

    private val _uiState = MutableStateFlow(ArticleListUiState())
    val uiState: StateFlow<ArticleListUiState> = _uiState.asStateFlow()

    /** 全部已发布文章 */
    val allArticles: Flow<PagingData<ArticleListUiModel>> =
        articleRepository.getArticles(getMyArticle = false)
            .cachedIn(viewModelScope)

    /** 当前登录用户的文章 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val myArticles: Flow<PagingData<ArticleListUiModel>> =
        userRepository.getMeFlow()
            .distinctUntilChanged()  // 只在用户信息发生变化时触发
            .map { user -> user?.id } // 提取 id（可能为 null）
            .flatMapLatest { authorId ->
                if (authorId != null) {
                    articleRepository.getArticles(
                        getMyArticle = true,
                        authorId = authorId
                    )
                } else {
                    // 用户未登录，返回空数据流
                    emptyFlow()
                }
            }
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
