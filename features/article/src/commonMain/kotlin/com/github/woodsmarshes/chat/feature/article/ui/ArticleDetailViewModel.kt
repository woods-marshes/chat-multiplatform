package com.github.woodsmarshes.chat.feature.article.ui

import androidx.lifecycle.ViewModel
import com.github.woodsmarshes.chat.core.data.repository.ArticleRepository
import com.github.woodsmarshes.chat.core.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.uuid.Uuid

class ArticleDetailViewModel(
    private val articleRepository: ArticleRepository,
    private val userRepository: UserRepository,
    private val articleId: Uuid,
) : ViewModel() {
    val user = userRepository.getMeFlow()

    // TODO: compare with logged-in user id
    private val _isOwnArticle = MutableStateFlow(false)
    val isOwnArticle: StateFlow<Boolean> = _isOwnArticle.asStateFlow()
}
