package com.github.woodsmarshes.chat.feature.article_editor.di

import com.github.woodsmarshes.chat.feature.article_editor.ui.ArticleEditorViewModel
import kotlin.uuid.Uuid
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val articleEditorModule = module {
    viewModel { (articleId: Uuid?) ->
        ArticleEditorViewModel(
            articleRepository = get(),
            userRepository = get(),
            networkConfig = get(),
            authRepository = get(),
            articleId = articleId,
        )
    }
}
