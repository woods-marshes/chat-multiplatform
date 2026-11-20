package com.github.woodsmarshes.chat.feature.articleeditor.di

import com.github.woodsmarshes.chat.feature.articleeditor.ui.ArticleEditorViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import kotlin.uuid.Uuid

val articleEditorModule =
    module {
        viewModel { (articleId: Uuid?) ->
            ArticleEditorViewModel(
                articleRepository = get(),
                userRepository = get(),
                serverEndpointProvider = get(),
                authRepository = get(),
                articleId = articleId,
            )
        }
    }
