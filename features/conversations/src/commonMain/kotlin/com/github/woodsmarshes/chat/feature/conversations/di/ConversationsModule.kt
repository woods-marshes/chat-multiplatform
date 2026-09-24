package com.github.woodsmarshes.chat.feature.conversations.di

import com.github.woodsmarshes.chat.feature.conversations.ui.ConversationsViewModel
import com.github.woodsmarshes.chat.feature.conversations.ui.GroupInfoViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val conversationsModule = module {
    viewModelOf(::ConversationsViewModel)
    viewModel { (conversationId: String) -> GroupInfoViewModel(conversationId, get(), get()) }
}
