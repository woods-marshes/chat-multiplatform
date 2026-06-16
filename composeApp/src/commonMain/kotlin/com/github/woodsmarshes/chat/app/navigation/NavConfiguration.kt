package com.github.woodsmarshes.chat.app.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import com.github.woodsmarshes.chat.feature.article.navigation.ArticleDetailNavKey
import com.github.woodsmarshes.chat.feature.article.navigation.ArticleListNavKey
import com.github.woodsmarshes.chat.feature.article_editor.navigation.ArticleEditorNavKey
import com.github.woodsmarshes.chat.feature.auth.navigation.AuthNavKey
import com.github.woodsmarshes.chat.feature.chat.navigation.ChatNavKey
import com.github.woodsmarshes.chat.feature.contacts.navigation.ContactsNavKey
import com.github.woodsmarshes.chat.feature.conversations.navigation.ConversationsNavKey
import com.github.woodsmarshes.chat.feature.profile.navigation.ProfileNavKey
import com.github.woodsmarshes.chat.feature.search.navigation.SearchNavKey
import com.github.woodsmarshes.chat.feature.settings.navigation.SettingsNavKey
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

val navConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(ConversationsNavKey::class)
            subclass(ContactsNavKey::class)
            subclass(SettingsNavKey::class)
            subclass(ChatNavKey::class)
            subclass(ProfileNavKey::class)
            subclass(AuthNavKey::class)
            subclass(SearchNavKey::class)
            subclass(ArticleListNavKey::class)
            subclass(ArticleDetailNavKey::class)
            subclass(ArticleEditorNavKey::class)
        }
    }
}