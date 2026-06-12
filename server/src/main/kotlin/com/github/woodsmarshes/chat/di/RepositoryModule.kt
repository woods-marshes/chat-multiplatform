package com.github.woodsmarshes.chat.di

import com.github.woodsmarshes.chat.repository.ContactRepository
import com.github.woodsmarshes.chat.repository.ContactRequestRepository
import com.github.woodsmarshes.chat.repository.ContactRequestSourceImpl
import com.github.woodsmarshes.chat.repository.ContactSourceImpl
import com.github.woodsmarshes.chat.repository.ConversationDataSourceImpl
import com.github.woodsmarshes.chat.repository.ConversationParticipantDataSourceImpl
import com.github.woodsmarshes.chat.repository.ConversationParticipantRepository
import com.github.woodsmarshes.chat.repository.ConversationRepository
import com.github.woodsmarshes.chat.repository.GroupJoinRequestRepository
import com.github.woodsmarshes.chat.repository.GroupJoinRequestSourceImpl
import com.github.woodsmarshes.chat.repository.GroupProfileDataSourceImpl
import com.github.woodsmarshes.chat.repository.GroupProfileRepository
import com.github.woodsmarshes.chat.repository.MessageDataSourceImpl
import com.github.woodsmarshes.chat.repository.MessageRepository
import com.github.woodsmarshes.chat.repository.UserDataSourceImpl
import com.github.woodsmarshes.chat.repository.UserRepository
import com.github.woodsmarshes.chat.repository.UserSettingDataSourceImpl
import com.github.woodsmarshes.chat.repository.UserSettingRepository
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
val repositoryModule = module {

    singleOf(::UserDataSourceImpl){
        bind<UserRepository>()
    }

    singleOf(::ConversationDataSourceImpl) {
        bind<ConversationRepository>()
    }

    singleOf(::MessageDataSourceImpl) {
        bind<MessageRepository>()
    }

    singleOf(::UserSettingDataSourceImpl) {
        bind<UserSettingRepository>()
    }

    singleOf(::GroupProfileDataSourceImpl) {
        bind<GroupProfileRepository>()
    }

    singleOf(::ConversationParticipantDataSourceImpl) {
        bind<ConversationParticipantRepository>()
    }

    singleOf(::ContactSourceImpl) {
        bind<ContactRepository>()
    }

    singleOf(::GroupJoinRequestSourceImpl) {
        bind<GroupJoinRequestRepository>()
    }

    singleOf(::ContactRequestSourceImpl) {
        bind<ContactRequestRepository>()
    }

}