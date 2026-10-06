package com.github.woodsmarshes.chat.di

import com.github.woodsmarshes.chat.service.AttachmentLifecycle
import com.github.woodsmarshes.chat.service.AuthService
import com.github.woodsmarshes.chat.service.ContactService
import com.github.woodsmarshes.chat.service.ConversationLifecycleService
import com.github.woodsmarshes.chat.service.ConversationSettingsService
import com.github.woodsmarshes.chat.service.FileService
import com.github.woodsmarshes.chat.service.GroupMembershipService
import com.github.woodsmarshes.chat.service.MessageService
import com.github.woodsmarshes.chat.service.ArticleService
import com.github.woodsmarshes.chat.service.RealtimeService
import com.github.woodsmarshes.chat.service.UserService
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val serviceModule = module {
    singleOf(::AuthService)
    singleOf(::ContactService)
    singleOf(::ConversationLifecycleService)
    singleOf(::GroupMembershipService)
    singleOf(::ConversationSettingsService)
    singleOf(::FileService)
    singleOf(::AttachmentLifecycle)
    singleOf(::MessageService)
    singleOf(::UserService)
    singleOf(::ArticleService)

    // Reflective singleOf would also try to resolve typingDebounceMs — a
    // Long with a Kotlin default value, which Koin's constructor injection
    // ignores — and fail; list the dependencies explicitly instead.
    single {
        RealtimeService(
            log = get(),
            eventBus = get(),
            delivery = get(),
            sessionManager = get(),
            scope = get(),
        )
    }
}