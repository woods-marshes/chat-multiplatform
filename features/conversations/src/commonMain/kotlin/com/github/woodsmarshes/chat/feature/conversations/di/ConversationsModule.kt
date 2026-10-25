package com.github.woodsmarshes.chat.feature.conversations.di

import com.github.woodsmarshes.chat.core.model.GroupProfile
import com.github.woodsmarshes.chat.feature.conversations.ui.ConversationsViewModel
import com.github.woodsmarshes.chat.feature.conversations.ui.GroupInfoViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val conversationsModule = module {
    viewModelOf(::ConversationsViewModel)
    // Both parameters must always be passed (GroupInfoScreen does, via
    // parametersOf(conversationId, searchProfile)): destructuring hits
    // component2(), which throws on a missing element instead of yielding
    // null like the old params.getOrNull() did.
    viewModel { (id: String, searchProfile: GroupProfile?) ->
        GroupInfoViewModel(id, get(), get(), get(), searchProfile)
    }
}
