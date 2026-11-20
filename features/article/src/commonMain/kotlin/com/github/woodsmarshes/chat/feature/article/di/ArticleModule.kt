package com.github.woodsmarshes.chat.feature.article.di

import com.github.woodsmarshes.chat.feature.article.ui.ArticleDetailViewModel
import com.github.woodsmarshes.chat.feature.article.ui.ArticleListViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import kotlin.uuid.Uuid

val articleModule =
    module {
        viewModelOf(::ArticleListViewModel)
        viewModel { (articleId: Uuid, authorId: Uuid) ->
            ArticleDetailViewModel(
                articleRepository = get(),
                userRepository = get(),
                articleId = articleId,
                authorId = authorId,
            )
        }
    }
