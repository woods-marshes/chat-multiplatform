package com.github.woodsmarshes.chat.di

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
    singleOf(::MessageService)
    singleOf(::UserService)
    singleOf(::ArticleService)
    singleOf(::RealtimeService)
}