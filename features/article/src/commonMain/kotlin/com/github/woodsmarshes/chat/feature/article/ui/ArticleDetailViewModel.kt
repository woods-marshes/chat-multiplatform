package com.github.woodsmarshes.chat.feature.article.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.onErr
import com.github.michaelbull.result.onOk
import com.github.woodsmarshes.chat.core.data.repository.ArticleRepository
import com.github.woodsmarshes.chat.core.data.repository.UserRepository
import com.github.woodsmarshes.chat.core.model.Article
import com.github.woodsmarshes.chat.core.model.error.ArticleError
import com.github.michaelbull.result.Result
import com.github.woodsmarshes.chat.feature.article.model.ArticleDetailUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.uuid.Uuid

class ArticleDetailViewModel(
    private val articleRepository: ArticleRepository,
    private val userRepository: UserRepository,
    private val articleId: Uuid,
    private val authorId: Uuid,
) : ViewModel() {
    val user = userRepository.getMeFlow()

    /** Whether the current user is the author of this article. */
    val isOwnArticle: StateFlow<Boolean> = user
        .map { it?.id == authorId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /**
     * Reactive article data — switches between own-article API (drafts visible)
     * and public API depending on [isOwnArticle].
     *
     * Offline-first: DB cached content emits immediately; network fetch
     * runs in background and re-emits if data changed.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val article: StateFlow<Result<Article?, ArticleError>> = isOwnArticle
        .flatMapLatest { isOwn ->
            articleRepository.getArticle(getMyArticle = isOwn, articleId = articleId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Ok(null))

    val uiState: StateFlow<ArticleDetailUiState> = article
        .map { result ->
            var state = ArticleDetailUiState(isLoading = false)
            result
                .onOk { a -> state = state.copy(article = a) }
                .onErr { e -> state = state.copy(error = e.toString()) }
            state
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ArticleDetailUiState())
}
