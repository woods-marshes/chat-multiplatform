package com.github.woodsmarshes.chat.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import com.github.woodsmarshes.chat.feature.contacts.navigation.ContactsNavKey
import com.github.woodsmarshes.chat.feature.article.navigation.ArticleListNavKey
import com.github.woodsmarshes.chat.feature.conversations.navigation.ConversationsNavKey
import com.github.woodsmarshes.chat.feature.settings.navigation.SettingsNavKey
import com.github.woodsmarshes.chat.lyricist.Strings

data class TopLevelNavigationItem(
    val navKey: NavKey,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val label: (Strings) -> String,
)

val topLevelNavigationItems = listOf(
    TopLevelNavigationItem(
        navKey = ArticleListNavKey,
        selectedIcon = Icons.Filled.Create,
        unselectedIcon = Icons.Outlined.Create,
        label = { it.articleTitle },
    ),
    TopLevelNavigationItem(
        navKey = ConversationsNavKey,
        selectedIcon = Icons.AutoMirrored.Filled.Chat,
        unselectedIcon = Icons.AutoMirrored.Outlined.Chat,
        label = { it.navConversations },
    ),
    TopLevelNavigationItem(
        navKey = ContactsNavKey,
        selectedIcon = Icons.Filled.People,
        unselectedIcon = Icons.Outlined.People,
        label = { it.navContacts },
    ),
    TopLevelNavigationItem(
        navKey = SettingsNavKey,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        label = { it.navSettings },
    ),
)
