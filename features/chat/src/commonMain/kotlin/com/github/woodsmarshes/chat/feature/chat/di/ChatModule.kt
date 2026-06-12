package com.github.woodsmarshes.chat.feature.chat.di

import com.github.woodsmarshes.chat.feature.chat.ui.ChatViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val chatModule = module {
    viewModel { (conversationId: String) ->
        ChatViewModel(
            conversationId = conversationId,
            messageRepository = get(),
            userRepository = get(),
        )
    }
}
