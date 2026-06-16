package com.github.woodsmarshes.chat.app.di

import com.github.woodsmarshes.chat.core.common.di.commonModule
import com.github.woodsmarshes.chat.core.data.di.dataModule
import com.github.woodsmarshes.chat.core.database.di.daosModule
import com.github.woodsmarshes.chat.core.database.di.databaseModule
import com.github.woodsmarshes.chat.core.datastore.di.dataStoreModule
import com.github.woodsmarshes.chat.core.domain.di.domainModule
import com.github.woodsmarshes.chat.core.network.di.networkConfig
import com.github.woodsmarshes.chat.core.network.di.networkModule
import com.github.woodsmarshes.chat.core.network.di.serializersModule
import com.github.woodsmarshes.chat.feature.article.di.articleModule
import com.github.woodsmarshes.chat.feature.article_editor.di.articleEditorModule
import com.github.woodsmarshes.chat.feature.auth.di.authModule
import com.github.woodsmarshes.chat.feature.chat.di.chatModule
import com.github.woodsmarshes.chat.feature.contacts.di.contactsModule
import com.github.woodsmarshes.chat.feature.conversations.di.conversationsModule
import com.github.woodsmarshes.chat.feature.profile.di.profileModule
import com.github.woodsmarshes.chat.feature.search.di.searchModule
import com.github.woodsmarshes.chat.feature.settings.di.settingsModule
import io.github.woodsmarshes.chat.db.ChatDatabase
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

fun initKoin(
    platformModule: Module,
    appDeclaration: KoinAppDeclaration = {},
) {
    startKoin {
        modules(
            platformModule,
            commonModule,
            dataStoreModule,
            databaseModule,
            daosModule,
            serializersModule,
            networkConfig,
            networkModule,
            dataModule,
            domainModule,
            articleModule,
            articleEditorModule,
            authModule,
            conversationsModule,
            contactsModule,
            chatModule,
            profileModule,
            settingsModule,
            searchModule,
        )
        appDeclaration()
    }
}
