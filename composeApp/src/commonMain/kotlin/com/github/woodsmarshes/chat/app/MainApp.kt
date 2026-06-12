package com.github.woodsmarshes.chat.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
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
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass
import com.github.woodsmarshes.chat.app.navigation.navConfiguration
import kotlinx.coroutines.launch
import com.github.woodsmarshes.chat.app.navigation.topLevelNavigationItems
import com.github.woodsmarshes.chat.core.navigation.Navigator
import com.github.woodsmarshes.chat.core.navigation.rememberNavigationState
import com.github.woodsmarshes.chat.core.navigation.toEntries
import com.github.woodsmarshes.chat.core.ui.components.feedback.AppSnackbarHost
import com.github.woodsmarshes.chat.core.ui.components.feedback.AppSnackbarState
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import com.github.woodsmarshes.chat.feature.auth.ui.AuthScreen
import com.github.woodsmarshes.chat.feature.chat.navigation.ChatNavKey
import com.github.woodsmarshes.chat.feature.chat.navigation.chatEntry
import com.github.woodsmarshes.chat.feature.contacts.navigation.ContactsNavKey
import com.github.woodsmarshes.chat.feature.contacts.navigation.contactsEntry
import com.github.woodsmarshes.chat.feature.conversations.navigation.ConversationsNavKey
import com.github.woodsmarshes.chat.feature.conversations.navigation.conversationsEntry
import com.github.woodsmarshes.chat.feature.profile.navigation.ProfileNavKey
import com.github.woodsmarshes.chat.feature.profile.navigation.profileEntry
import com.github.woodsmarshes.chat.feature.search.navigation.searchEntry
import com.github.woodsmarshes.chat.feature.settings.navigation.SettingsNavKey
import com.github.woodsmarshes.chat.feature.settings.navigation.settingsEntry

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun MainApp(
    appState: ChatAppState,
    snackbarState: AppSnackbarState,
    modifier: Modifier = Modifier,
    windowAdaptiveInfo: WindowAdaptiveInfo = currentWindowAdaptiveInfoV2()
) {
    val isLoggedIn = appState.isLoggedIn.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = isLoggedIn.value,
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
                AuthScreen(
                    onAuthSuccess = { },
                )
            }

            true -> {
                MainContent(
                    appState = appState,
                    snackbarState = snackbarState,
                    modifier = modifier,
                    windowAdaptiveInfo = windowAdaptiveInfo
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun MainContent(
    appState: ChatAppState,
    snackbarState: AppSnackbarState,
    modifier: Modifier,
    windowAdaptiveInfo: WindowAdaptiveInfo
) {
    val topLevelKeys = setOf(ConversationsNavKey, ContactsNavKey, SettingsNavKey)
    val navigationState = rememberNavigationState(
        startKey = ConversationsNavKey,
        topLevelKeys = topLevelKeys,
        configuration = navConfiguration
    )

    val navigator = remember { Navigator(navigationState) }

    val strings = LocalStrings.current

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val scope = rememberCoroutineScope()

    val listPaneMeta = remember { ListDetailSceneStrategy.listPane() }
    val detailPaneMeta = remember { ListDetailSceneStrategy.detailPane() }
    val extraPaneMeta = remember { ListDetailSceneStrategy.extraPane() }
    val entryProvider = entryProvider {
//        authEntry(onAuthSuccess = {
//            navigator.navigate(ConversationsNavKey)
//        })
        conversationsEntry(
            onNavigateToChat = { navigator.navigate(ChatNavKey(it)) },
            onMenuClick = { scope.launch { drawerState.open() } },
            metadata = listPaneMeta,
        )
        contactsEntry(
            onNavigateToProfile = { navigator.navigate(ProfileNavKey(it)) },
            onMenuClick = { scope.launch { drawerState.open() } },
            metadata = listPaneMeta,
        )
        settingsEntry(onBack = { navigator.goBack() })
        chatEntry(
            onBack = { navigator.goBack() },
            onNavigateToProfile = { navigator.navigate(ProfileNavKey(it)) },
            metadata = detailPaneMeta,
        )
        profileEntry(
            onBack = { navigator.goBack() },
            metadata = detailPaneMeta + extraPaneMeta,
        )
        searchEntry(onBack = { navigator.goBack() })
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MeDrawerSheet(
                displayName = "用户",
                username = "user",
                avatarUrl = null,
                onProfileClick = {
                    scope.launch { drawerState.close() }
                },
                onSettingsClick = {
                    scope.launch { drawerState.close() }
                    navigator.navigate(SettingsNavKey)
                },
                onLogoutClick = {
                    scope.launch { drawerState.close() }
                },
            )
        },
        gesturesEnabled = showNavigationSuite,
    ) {
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
            layoutType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(windowAdaptiveInfo),
            state = scaffoldState,
            modifier = modifier,
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
                        modifier = Modifier.fillMaxSize()
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
}
