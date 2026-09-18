package com.github.woodsmarshes.chat.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldValue
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass
import com.github.woodsmarshes.chat.app.navigation.navConfiguration
import com.github.woodsmarshes.chat.app.navigation.topLevelNavigationItems
import com.github.woodsmarshes.chat.app.session.SessionManager
import com.github.woodsmarshes.chat.core.navigation.Navigator
import com.github.woodsmarshes.chat.core.navigation.rememberNavigationState
import com.github.woodsmarshes.chat.core.navigation.toEntries
import com.github.woodsmarshes.chat.core.ui.components.avatar.UserAvatar
import com.github.woodsmarshes.chat.core.ui.components.LocalAccountAffordance
import com.github.woodsmarshes.chat.core.ui.components.feedback.AppSnackbarHost
import com.github.woodsmarshes.chat.core.ui.components.feedback.AppSnackbarState
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import com.github.woodsmarshes.chat.feature.auth.ui.AuthScreen
import androidx.compose.ui.unit.dp
import com.github.woodsmarshes.chat.feature.article.navigation.ArticleDetailNavKey
import com.github.woodsmarshes.chat.feature.article.navigation.ArticleListNavKey
import com.github.woodsmarshes.chat.feature.article.navigation.articleDetailEntry
import com.github.woodsmarshes.chat.feature.article.navigation.articleListEntry
import com.github.woodsmarshes.chat.feature.article_editor.navigation.ArticleEditorNavKey
import com.github.woodsmarshes.chat.feature.article_editor.navigation.editorEntry
import com.github.woodsmarshes.chat.feature.chat.navigation.ChatNavKey
import com.github.woodsmarshes.chat.feature.chat.navigation.chatEntry
import com.github.woodsmarshes.chat.feature.contacts.navigation.ContactsNavKey
import com.github.woodsmarshes.chat.feature.contacts.navigation.contactsEntry
import com.github.woodsmarshes.chat.feature.conversations.navigation.ConversationsNavKey
import com.github.woodsmarshes.chat.feature.conversations.navigation.conversationsEntry
import com.github.woodsmarshes.chat.feature.profile.navigation.ProfileNavKey
import com.github.woodsmarshes.chat.feature.profile.navigation.profileEntry
import com.github.woodsmarshes.chat.feature.search.navigation.SearchNavKey
import com.github.woodsmarshes.chat.feature.search.navigation.SearchType
import com.github.woodsmarshes.chat.feature.search.navigation.searchEntry
import com.github.woodsmarshes.chat.feature.settings.navigation.SettingsNavKey
import com.github.woodsmarshes.chat.feature.settings.navigation.settingsEntry

@OptIn(ExperimentalMaterial3AdaptiveApi::class, kotlin.uuid.ExperimentalUuidApi::class)
@Composable
fun MainApp(
    sessionManager: SessionManager,
    snackbarState: AppSnackbarState,
    modifier: Modifier = Modifier,
    windowAdaptiveInfo: WindowAdaptiveInfo = currentWindowAdaptiveInfoV2()
) {
    val isLoggedIn by sessionManager.isLoggedIn.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = isLoggedIn,
        transitionSpec = {
            when (initialState) {
                null -> fadeIn() togetherWith fadeOut()
                // 从登录 -> 主界面：主界面从右滑入，登录页向左滑出
                false if targetState == true -> {
                    (slideInHorizontally { width -> width } + fadeIn()) togetherWith
                            (slideOutHorizontally { width -> -width } + fadeOut())
                }
                // 从主界面 -> 登录（登出）：登录页从左滑入，主界面向右滑出
                true if targetState == false -> {
                    (slideInHorizontally { width -> -width } + fadeIn()) togetherWith
                            (slideOutHorizontally { width -> width } + fadeOut())
                }
                // 其他情况（比如 null 到 true/false）使用默认淡入淡出
                else -> fadeIn() togetherWith fadeOut()
            }
        },
        label = "AuthTransition",
        modifier = modifier
    ) { currentState ->
        when (currentState) {
            null -> {
                Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            false -> {
                AuthScreen()
            }

            true -> {
                MainContent(
                    sessionManager = sessionManager,
                    snackbarState = snackbarState,
                    modifier = modifier,
                    windowAdaptiveInfo = windowAdaptiveInfo
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class, kotlin.uuid.ExperimentalUuidApi::class)
@Composable
private fun MainContent(
    sessionManager: SessionManager,
    snackbarState: AppSnackbarState,
    modifier: Modifier,
    windowAdaptiveInfo: WindowAdaptiveInfo
) {
    val topLevelKeys = setOf(ArticleListNavKey, ConversationsNavKey, ContactsNavKey, SettingsNavKey)
    val navigationState = rememberNavigationState(
        startKey = ConversationsNavKey,
        topLevelKeys = topLevelKeys,
        configuration = navConfiguration
    )

    val navigator = remember { Navigator(navigationState) }

    val currentUser by sessionManager.currentUser.collectAsStateWithLifecycle()

    val strings = LocalStrings.current

    val listPaneMeta = remember { ListDetailSceneStrategy.listPane() }
    val detailPaneMeta = remember { ListDetailSceneStrategy.detailPane() }
    val entryProvider = entryProvider {
        conversationsEntry(
            onNavigateToChat = { conversationId, isGroup ->
                navigator.navigate(ChatNavKey(conversationId , isGroup))
            },
            onSearchClick = {
                navigator.navigate(SearchNavKey(SearchType.CONVERSATION))
            },
            metadata = listPaneMeta,
        )
        contactsEntry(
            onNavigateToProfile = { navigator.navigate(ProfileNavKey(it)) },
            onSearchClick = { navigator.navigate(SearchNavKey(SearchType.CONTACT)) },
            metadata = listPaneMeta,
        )
        settingsEntry(
            onBack = { navigator.goBack() },
            onLogout = { sessionManager.logout() },
            onSearchClick = { navigator.navigate(SearchNavKey(SearchType.SETTING)) },
        )
        chatEntry(
            onBack = { navigator.goBack() },
            onNavigateToProfile = { navigator.navigate(ProfileNavKey(it)) },
            metadata = detailPaneMeta,
        )
        profileEntry(
            onBack = { navigator.goBack() },
            metadata = detailPaneMeta,
        )
        articleListEntry(
            onArticleClick = { id, authorId -> navigator.navigate(ArticleDetailNavKey(id, authorId)) },
            onCreateClick = { navigator.navigate(ArticleEditorNavKey()) },
            metadata = listPaneMeta,
        )
        articleDetailEntry(
            onBack = { navigator.goBack() },
            onEditClick = { id -> navigator.navigate(ArticleEditorNavKey(id)) },
            metadata = detailPaneMeta,
        )
        editorEntry(
            onBack = { navigator.goBack() },
            // detail-pane metadata: keeps the editor inside a ListDetail scene.
            // A plain (metadata-less) entry on top of the stack makes the next
            // top-level switch fall back to a single-pane scene, which leaves
            // the target tab's content uncomposed (blank screen on desktop).
            metadata = detailPaneMeta,
        )
        searchEntry(
            onBack = { navigator.goBack() },
            metadata = listPaneMeta,
        )
    }

    val isMediumOrLarger = windowAdaptiveInfo.windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    val isTopLevelRoute = navigationState.currentKey in navigationState.topLevelKeys

    val showNavigationSuite = isTopLevelRoute || isMediumOrLarger

    val scaffoldState = rememberNavigationSuiteScaffoldState(
        initialValue = if (showNavigationSuite) {
            NavigationSuiteScaffoldValue.Visible
        } else {
            NavigationSuiteScaffoldValue.Hidden
        }
    )

    LaunchedEffect(showNavigationSuite) {
        if (showNavigationSuite) {
            scaffoldState.show()
        } else {
            scaffoldState.hide() // 也可以使用 scaffoldState.snapTo(NavigationSuiteScaffoldValue.Hidden) 立即收起
        }
    }

    val navigationSuiteLayout = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(windowAdaptiveInfo)
    val openProfile: () -> Unit = {
        currentUser?.let { user -> navigator.navigate(ProfileNavKey(user.id.toString())) }
    }

    // Compact layouts keep the account affordance in top bars; medium+ pins
    // it to the rail bottom (overlay below), matching the reference design.
    val accountAffordance: (@Composable () -> Unit)? =
        if (navigationSuiteLayout == NavigationSuiteType.NavigationRail) {
            null
        } else {
            {
                UserAvatar(
                    name = currentUser?.displayName?.ifEmpty { null } ?: currentUser?.username,
                    avatarUrl = currentUser?.avatarUrl,
                    size = 28.dp,
                    onClick = openProfile,
                )
            }
        }

    Box(modifier = modifier) {
        CompositionLocalProvider(LocalAccountAffordance provides accountAffordance) {
            NavigationSuiteScaffold(
                navigationSuiteItems = {
                    topLevelNavigationItems.forEach { item ->
                        val selected = item.navKey == navigationState.currentTopLevelKey
                        item(
                            selected = selected,
                            onClick = { navigator.navigate(item.navKey) },
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
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            contentWindowInsets = WindowInsets(0, 0, 0, 0), // 归零，不让外层全局 Padding 堆叠
            snackbarHost = {
                // 确保 Snackbar 弹出时能排除软键盘，避免被键盘遮挡
                AppSnackbarHost(
                    snackbarState = snackbarState,
                    modifier = Modifier.windowInsetsPadding(
                        WindowInsets.safeDrawing.exclude(WindowInsets.ime)
                    )
                )
            },
        ) { padding ->
            // 7. 【对齐 Nia】精确消费 WindowInsets 的容器
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding) // 消费由底部导航条占用的高度
                    .consumeWindowInsets(padding) // 防范内部重复消费
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal) // 水平方向安全填充
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // Tao composites the scene over a black surface; areas
                        // no composable paints (e.g. empty list-detail panes)
                        // must get an explicit background or they render black.
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    val strategy = rememberListDetailSceneStrategy<NavKey>()

                    NavDisplay(
                        entries = navigationState.toEntries(entryProvider),
                        sceneStrategies = listOf(strategy),
                        onBack = { navigator.goBack() },
                        modifier = Modifier.fillMaxSize() // 将安全边界交给外层 Column 处理，这里保持 fillMaxSize
                    )
                }
                }
            }
            }
        }

        // Account affordance pinned to the rail bottom on medium+ layouts
        // (reference design). The rail items are top-arranged, so the bottom
        // area is always free; the rail is 80dp wide.
        if (navigationSuiteLayout == NavigationSuiteType.NavigationRail) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 12.dp, bottom = 12.dp)
                    .width(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = openProfile),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                UserAvatar(
                    name = currentUser?.displayName?.ifEmpty { null } ?: currentUser?.username,
                    avatarUrl = currentUser?.avatarUrl,
                    size = 40.dp,
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
    }
}
