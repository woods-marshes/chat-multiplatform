package com.github.woodsmarshes.chat.app.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import com.github.woodsmarshes.chat.feature.auth.navigation.AuthNavKey
import com.github.woodsmarshes.chat.feature.chat.navigation.ChatNavKey
import com.github.woodsmarshes.chat.feature.contacts.navigation.ContactsNavKey
import com.github.woodsmarshes.chat.feature.conversations.navigation.ConversationsNavKey
import com.github.woodsmarshes.chat.feature.profile.navigation.ProfileNavKey
import com.github.woodsmarshes.chat.feature.settings.navigation.SettingsNavKey
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

val navConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(ConversationsNavKey::class, ConversationsNavKey.serializer())
            subclass(ContactsNavKey::class, ContactsNavKey.serializer())
            subclass(SettingsNavKey::class, SettingsNavKey.serializer())
            subclass(ChatNavKey::class, ChatNavKey.serializer())
            subclass(ProfileNavKey::class, ProfileNavKey.serializer())
            subclass(AuthNavKey::class, AuthNavKey.serializer())
        }
    }
}