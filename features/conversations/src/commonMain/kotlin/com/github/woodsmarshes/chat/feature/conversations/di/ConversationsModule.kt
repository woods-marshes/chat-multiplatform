package com.github.woodsmarshes.chat.feature.conversations.di

import com.github.woodsmarshes.chat.feature.conversations.ui.ConversationsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val conversationsModule = module {
    viewModelOf(::ConversationsViewModel)
}
