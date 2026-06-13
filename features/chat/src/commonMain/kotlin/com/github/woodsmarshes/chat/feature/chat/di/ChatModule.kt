package com.github.woodsmarshes.chat.feature.chat.di

import com.github.woodsmarshes.chat.feature.chat.ui.ChatViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val chatModule = module {
    viewModel { (conversationId: String, isGroup: Boolean) ->
        ChatViewModel(
            conversationId = conversationId,
            isGroup = isGroup,
            messageRepository = get(),
            userRepository = get(),
        )
    }
}
