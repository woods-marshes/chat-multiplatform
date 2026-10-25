package com.github.woodsmarshes.chat.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldState
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldValue
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass
import com.github.woodsmarshes.chat.app.navigation.AppTransitions
import com.github.woodsmarshes.chat.app.navigation.CompactNavigationLayout
import com.github.woodsmarshes.chat.app.navigation.navConfiguration
import com.github.woodsmarshes.chat.app.navigation.topLevelNavigationItems
import com.github.woodsmarshes.chat.app.session.SessionManager
import com.github.woodsmarshes.chat.app.session.SessionState
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.navigation.Navigator
import com.github.woodsmarshes.chat.core.navigation.rememberNavigationState
import com.github.woodsmarshes.chat.core.navigation.toEntries
import com.github.woodsmarshes.chat.core.ui.components.AppProgressIndicator
import com.github.woodsmarshes.chat.core.ui.components.avatar.UserAvatar
import com.github.woodsmarshes.chat.core.ui.components.feedback.AppSnackbarHost
import com.github.woodsmarshes.chat.core.ui.components.feedback.AppSnackbarState
import com.github.woodsmarshes.chat.core.ui.components.LocalAccountAffordance
import com.github.woodsmarshes.chat.core.ui.components.motion.LocalAppSharedTransitionScope
import com.github.woodsmarshes.chat.core.ui.components.state.ErrorContent
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import com.github.woodsmarshes.chat.core.ui.theme.isMiuixTheme
import com.github.woodsmarshes.chat.feature.article.navigation.articleDetailEntry
import com.github.woodsmarshes.chat.feature.article.navigation.ArticleDetailNavKey
import com.github.woodsmarshes.chat.feature.article.navigation.articleListEntry
import com.github.woodsmarshes.chat.feature.article.navigation.ArticleListNavKey
import com.github.woodsmarshes.chat.feature.article_editor.navigation.ArticleEditorNavKey
import com.github.woodsmarshes.chat.feature.article_editor.navigation.editorEntry
import com.github.woodsmarshes.chat.feature.auth.ui.AuthScreen
import com.github.woodsmarshes.chat.feature.chat.navigation.chatEntry
import com.github.woodsmarshes.chat.feature.chat.navigation.ChatNavKey
import com.github.woodsmarshes.chat.feature.contacts.navigation.contactsEntry
import com.github.woodsmarshes.chat.feature.contacts.navigation.ContactsNavKey
import com.github.woodsmarshes.chat.feature.conversations.navigation.ChatGroupInfoNavKey
import com.github.woodsmarshes.chat.feature.conversations.navigation.conversationsEntry
import com.github.woodsmarshes.chat.feature.conversations.navigation.ConversationsNavKey
import com.github.woodsmarshes.chat.feature.conversations.navigation.groupInfoEntry
import com.github.woodsmarshes.chat.feature.conversations.navigation.GroupInfoNavKey
import com.github.woodsmarshes.chat.feature.profile.navigation.ChatProfileNavKey
import com.github.woodsmarshes.chat.feature.profile.navigation.profileEntry
import com.github.woodsmarshes.chat.feature.profile.navigation.ProfileNavKey
import com.github.woodsmarshes.chat.feature.search.navigation.searchEntry
import com.github.woodsmarshes.chat.feature.search.navigation.SearchNavKey
import com.github.woodsmarshes.chat.feature.search.navigation.SearchType
import com.github.woodsmarshes.chat.feature.settings.model.SettingsCategory
import com.github.woodsmarshes.chat.feature.settings.navigation.ChatEditProfileNavKey
import com.github.woodsmarshes.chat.feature.settings.navigation.EditProfileNavKey
import com.github.woodsmarshes.chat.feature.settings.navigation.OpenSourceLicensesNavKey
import com.github.woodsmarshes.chat.feature.settings.navigation.SettingsDetailNavKey
import com.github.woodsmarshes.chat.feature.settings.navigation.settingsEntry
import com.github.woodsmarshes.chat.feature.settings.navigation.SettingsNavKey
import top.yukonga.miuix.kmp.basic.NavigationBar as MiuixNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem as MiuixNavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationRail as MiuixNavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem as MiuixNavigationRailItem
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AppNavigationChrome(
    isMediumOrLarger: Boolean,
    isMiuix: Boolean,
    navigationSuiteLayout: NavigationSuiteType,
    showNavigationSuite: Boolean,
    scaffoldState: NavigationSuiteScaffoldState,
    currentTopLevelKey: NavKey?,
    selectTopLevelItem: (NavKey) -> Unit,
    sceneContent: @Composable (Modifier, Dp) -> Unit,
    accountAffordance: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    Box(modifier = modifier) {
        CompositionLocalProvider(LocalAccountAffordance provides accountAffordance) {

            if (!isMediumOrLarger) {
                CompactNavigationLayout(
                    showNavigation = showNavigationSuite,
                    navigation = {
                        if (isMiuix) {
                            MiuixNavigationBar(color = MiuixTheme.colorScheme.surface) {
                                topLevelNavigationItems.forEach { item ->
                                    val selected = item.navKey == currentTopLevelKey
                                    MiuixNavigationBarItem(
                                        selected = selected,
                                        onClick = { selectTopLevelItem(item.navKey) },
                                        icon = if (selected) item.selectedIcon else item.unselectedIcon,
                                        label = item.label(strings),
                                    )
                                }
                            }
                        } else {
                            NavigationBar {
                                topLevelNavigationItems.forEach { item ->
                                    val selected = item.navKey == currentTopLevelKey
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { selectTopLevelItem(item.navKey) },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                                contentDescription = item.label(strings),
                                            )
                                        },
                                        label = { Text(item.label(strings)) },
                                    )
                                }
                            }
                        }
                    },
                ) { bottomBarHeight ->
                    sceneContent(Modifier.fillMaxSize(), bottomBarHeight)
                }
            } else if (isMiuix) {
                Row(modifier = Modifier.fillMaxSize()) {
                    if (navigationSuiteLayout == NavigationSuiteType.NavigationRail && showNavigationSuite) {
                        MiuixNavigationRail(
                            color = MiuixTheme.colorScheme.surface,
                        ) {
                            topLevelNavigationItems.forEach { item ->
                                val selected = item.navKey == currentTopLevelKey
                                MiuixNavigationRailItem(
                                    selected = selected,
                                    onClick = { selectTopLevelItem(item.navKey) },
                                    icon = if (selected) item.selectedIcon else item.unselectedIcon,
                                    label = item.label(strings),
                                )
                            }
                        }
                    }
                    sceneContent(Modifier.weight(1f).fillMaxSize(), 0.dp)
                }
            } else {
                NavigationSuiteScaffold(
                    navigationSuiteItems = {
                        topLevelNavigationItems.forEach { item ->
                            val selected = item.navKey == currentTopLevelKey
                            item(
                                selected = selected,
                                onClick = { selectTopLevelItem(item.navKey) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon
                                        else item.unselectedIcon,
                                        contentDescription = item.label(strings),
                                    )
                                },
                                label = { Text(item.label(strings)) },
                            )
                        }
                    },
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    layoutType = navigationSuiteLayout,
                    state = scaffoldState,
                ) {
                    sceneContent(Modifier.fillMaxSize(), 0.dp)
                }
            }
        }
    }
}

@Composable
internal fun AccountRailFooter(
    currentUser: User?,
    openProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
                modifier = modifier
                    .navigationBarsPadding()
                    .padding(start = 10.dp, bottom = 12.dp)
                    .width(60.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = openProfile)
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                UserAvatar(
                    name = currentUser?.displayName?.ifEmpty { null } ?: currentUser?.username,
                    avatarUrl = currentUser?.avatarUrl,
                    size = 36.dp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = currentUser?.displayName?.ifEmpty { null }
                        ?: currentUser?.username ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
}
