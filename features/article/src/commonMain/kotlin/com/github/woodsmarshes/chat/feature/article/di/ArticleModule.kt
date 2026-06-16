package com.github.woodsmarshes.chat.feature.article.di

import com.github.woodsmarshes.chat.feature.article.ui.ArticleDetailViewModel
import com.github.woodsmarshes.chat.feature.article.ui.ArticleListViewModel
import kotlin.uuid.Uuid
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val articleModule = module {
    viewModelOf(::ArticleListViewModel)
    viewModel { (articleId: Uuid) ->
        ArticleDetailViewModel(
            articleRepository = get(),
            userRepository = get(),
            articleId = articleId,
        )
    }
}
