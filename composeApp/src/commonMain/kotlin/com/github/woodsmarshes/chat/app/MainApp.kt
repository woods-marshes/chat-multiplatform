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
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldValue
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.material3.minimumInteractiveComponentSize
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
import com.github.woodsmarshes.chat.app.session.SessionState
import com.github.woodsmarshes.chat.core.navigation.Navigator
import com.github.woodsmarshes.chat.core.navigation.rememberNavigationState
import com.github.woodsmarshes.chat.core.navigation.toEntries
import com.github.woodsmarshes.chat.core.ui.components.AppProgressIndicator
import com.github.woodsmarshes.chat.core.ui.components.avatar.UserAvatar
import com.github.woodsmarshes.chat.core.ui.components.LocalAccountAffordance
import com.github.woodsmarshes.chat.core.ui.components.feedback.AppSnackbarHost
import com.github.woodsmarshes.chat.core.ui.components.feedback.AppSnackbarState
import com.github.woodsmarshes.chat.core.ui.components.state.ErrorContent
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import com.github.woodsmarshes.chat.core.ui.theme.isMiuixTheme
import com.github.woodsmarshes.chat.feature.auth.ui.AuthScreen
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.NavigationBar as MiuixNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem as MiuixNavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationRail as MiuixNavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem as MiuixNavigationRailItem
import top.yukonga.miuix.kmp.theme.MiuixTheme
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
import com.github.woodsmarshes.chat.feature.conversations.navigation.ChatGroupInfoNavKey
import com.github.woodsmarshes.chat.feature.conversations.navigation.ConversationsNavKey
import com.github.woodsmarshes.chat.feature.conversations.navigation.GroupInfoNavKey
import com.github.woodsmarshes.chat.feature.conversations.navigation.conversationsEntry
import com.github.woodsmarshes.chat.feature.conversations.navigation.groupInfoEntry
import com.github.woodsmarshes.chat.feature.profile.navigation.ChatProfileNavKey
import com.github.woodsmarshes.chat.feature.profile.navigation.ProfileNavKey
import com.github.woodsmarshes.chat.feature.profile.navigation.profileEntry
import com.github.woodsmarshes.chat.feature.search.navigation.SearchNavKey
import com.github.woodsmarshes.chat.feature.search.navigation.SearchType
import com.github.woodsmarshes.chat.feature.search.navigation.searchEntry
import com.github.woodsmarshes.chat.feature.settings.model.SettingsCategory
import com.github.woodsmarshes.chat.feature.settings.navigation.ChatEditProfileNavKey
import com.github.woodsmarshes.chat.feature.settings.navigation.EditProfileNavKey
import com.github.woodsmarshes.chat.feature.settings.navigation.OpenSourceLicensesNavKey
import com.github.woodsmarshes.chat.feature.settings.navigation.SettingsDetailNavKey
import com.github.woodsmarshes.chat.feature.settings.navigation.SettingsNavKey
import com.github.woodsmarshes.chat.feature.settings.navigation.settingsEntry
import com.github.woodsmarshes.chat.feature.settings.ui.SettingsDetailScreen

@OptIn(ExperimentalMaterial3AdaptiveApi::class, kotlin.uuid.ExperimentalUuidApi::class)
@Composable
fun MainApp(
    sessionManager: SessionManager,
    snackbarState: AppSnackbarState,
    modifier: Modifier = Modifier,
    windowAdaptiveInfo: WindowAdaptiveInfo = currentWindowAdaptiveInfoV2()
) {
    val isLoggedIn by sessionManager.isLoggedIn.collectAsStateWithLifecycle()
    val sessionState by sessionManager.sessionState.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = isLoggedIn,
        transitionSpec = {
            when (initialState) {
                null -> fadeIn() togetherWith fadeOut()
                false if targetState == true -> {
                    (slideInHorizontally { width -> width } + fadeIn()) togetherWith
                            (slideOutHorizontally { width -> -width } + fadeOut())
                }
                true if targetState == false -> {
                    (slideInHorizontally { width -> -width } + fadeIn()) togetherWith
                            (slideOutHorizontally { width -> width } + fadeOut())
                }
                else -> fadeIn() togetherWith fadeOut()
            }
        },
        label = "AuthTransition",
        modifier = modifier
    ) { currentState ->
        when (currentState) {
            null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AppProgressIndicator()
                }
            }

            false -> {
                if (sessionState is SessionState.StartFailed) {
                    val strings = LocalStrings.current
                    ErrorContent(
                        message = strings.sessionStartFailed,
                        modifier = Modifier.fillMaxSize(),
                        onRetry = sessionManager::retrySessionStart,
                        secondaryActionLabel = strings.logout,
                        onSecondaryAction = sessionManager::logout,
                    )
                } else {
                    AuthScreen(modifier = Modifier.fillMaxSize())
                }
            }

            true -> {
                MainContent(
                    sessionManager = sessionManager,
                    snackbarState = snackbarState,
                    modifier = Modifier.fillMaxSize(),
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

    val listPaneMeta = remember {
        ListDetailSceneStrategy.listPane() + ListDetailSceneStrategy.preferredPaneSize(width = 320.dp)
    }
    val settingsListPaneMeta = remember {
        ListDetailSceneStrategy.listPane(
            detailPlaceholder = {
                SettingsDetailScreen(
                    category = SettingsCategory.PROFILE,
                    onBack = { navigator.goBack() },
                    onLogout = { sessionManager.logout() },
                    onOpenLicenses = {
                        navigator.navigate(OpenSourceLicensesNavKey)
                    },
                )
            }
        ) + ListDetailSceneStrategy.preferredPaneSize(width = 320.dp)
    }
    val detailPaneMeta = remember { ListDetailSceneStrategy.detailPane() }
    val extraPaneMeta = remember {
        ListDetailSceneStrategy.extraPane() + ListDetailSceneStrategy.preferredPaneSize(width = 320.dp)
    }

    // Selecting a primary detail from the list replaces any open detail AND
    // dismisses any trailing extraPane (profile / group info) from the previous item.
    val openDetailFromList: (NavKey) -> Unit = { key ->
        val stack = navigationState.currentSubStack
        if (stack.size > 1) {
            stack.subList(1, stack.size).clear()
        }
        navigator.navigate(key)
    }

    // ChatProfileNavKey, ChatGroupInfoNavKey, and ChatEditProfileNavKey all
    // occupy the rightmost extraPane alongside an active ChatScreen; switching
    // between them pops any trailing extra-pane sub-chain so closing takes one step.
    val openExtraPane: (NavKey) -> Unit = { key ->
        val stack = navigationState.currentSubStack
        while (stack.lastOrNull().let {
                it is ChatProfileNavKey || it is ChatGroupInfoNavKey || it is ChatEditProfileNavKey
            }) {
            stack.removeAt(stack.lastIndex)
        }
        navigator.navigate(key)
    }

    val openChatFromInfo: (String, Boolean) -> Unit = { conversationId, isGroup ->
        if (navigationState.currentTopLevelKey != ConversationsNavKey) {
            navigator.navigate(ConversationsNavKey)
        }
        openDetailFromList(ChatNavKey(conversationId, isGroup))
    }

    val entryProvider = entryProvider {
        conversationsEntry(
            onNavigateToChat = { conversationId, isGroup ->
                openDetailFromList(ChatNavKey(conversationId, isGroup))
            },
            onGroupInfoClick = { conversationId ->
                // Picking a group from the search bar is a list-level selection:
                // open it in the primary detailPane (replacing any previous chat)
                // rather than in the trailing extraPane beside an unrelated chat.
                openDetailFromList(GroupInfoNavKey(conversationId))
            },
            selectedConversationId = {
                val stack = navigationState.subStacks[ConversationsNavKey]
                stack?.lastOrNull {
                    it is ChatNavKey || it is GroupInfoNavKey || it is ChatGroupInfoNavKey
                }?.let {
                    when (it) {
                        is ChatNavKey -> it.conversationId
                        is GroupInfoNavKey -> it.conversationId
                        is ChatGroupInfoNavKey -> it.conversationId
                        else -> null
                    }
                }
            },
            metadata = listPaneMeta,
        )
        contactsEntry(
            onNavigateToProfile = { openDetailFromList(ProfileNavKey(it)) },
            selectedUserId = {
                val stack = navigationState.subStacks[ContactsNavKey]
                (stack?.lastOrNull { it is ProfileNavKey } as? ProfileNavKey)?.userId
            },
            metadata = listPaneMeta,
        )
        settingsEntry(
            onBack = { navigator.goBack() },
            onLogout = { sessionManager.logout() },
            onCategoryClick = { category ->
                openDetailFromList(SettingsDetailNavKey(category))
            },
            onOpenLicenses = {
                navigator.navigate(OpenSourceLicensesNavKey)
            },
            selectedCategory = {
                val stack = navigationState.subStacks[SettingsNavKey]
                when (val top = stack?.lastOrNull { it is SettingsDetailNavKey || it is OpenSourceLicensesNavKey }) {
                    is SettingsDetailNavKey -> top.category
                    is OpenSourceLicensesNavKey -> SettingsCategory.ABOUT
                    else -> null
                }
            },
            metadata = settingsListPaneMeta,
            detailMetadata = detailPaneMeta,
            extraMetadata = extraPaneMeta,
        )
        chatEntry(
            onBack = { navigator.goBack() },
            onNavigateToProfile = { openExtraPane(ChatProfileNavKey(it)) },
            onNavigateToGroupInfo = { conversationId ->
                openExtraPane(ChatGroupInfoNavKey(conversationId))
            },
            metadata = detailPaneMeta,
        )
        groupInfoEntry(
            onBack = { navigator.goBack() },
            onOpenChat = { conversationId ->
                openChatFromInfo(conversationId, true)
            },
            detailMetadata = detailPaneMeta,
            extraMetadata = extraPaneMeta,
        )
        profileEntry(
            onBack = { navigator.goBack() },
            onOpenChat = { conversationId ->
                openChatFromInfo(conversationId, false)
            },
            onEditProfile = {
                navigator.navigate(EditProfileNavKey)
            },
            onChatEditProfile = {
                navigator.navigate(ChatEditProfileNavKey)
            },
            detailMetadata = detailPaneMeta,
            extraMetadata = extraPaneMeta,
        )
        articleListEntry(
            onArticleClick = { id, authorId ->
                openDetailFromList(ArticleDetailNavKey(id, authorId))
            },
            onCreateClick = {
                openDetailFromList(ArticleEditorNavKey())
            },
            selectedArticleId = {
                val stack = navigationState.subStacks[ArticleListNavKey]
                stack?.lastOrNull { it is ArticleDetailNavKey || it is ArticleEditorNavKey }?.let {
                    when (it) {
                        is ArticleDetailNavKey -> it.id
                        is ArticleEditorNavKey -> it.id
                        else -> null
                    }
                }
            },
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
            onOpenProfile = { userId -> openDetailFromList(ProfileNavKey(userId)) },
            onOpenGroupInfo = { conversationId -> openDetailFromList(GroupInfoNavKey(conversationId)) },
            metadata = listPaneMeta,
        )
    }

    val isMediumOrLarger = windowAdaptiveInfo.windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    // Keep Settings in two-pane mode on medium/expanded windows by seeding
    // the default PROFILE detail entry whenever the Settings sub-stack has
    // only its root list entry.
    LaunchedEffect(navigationState.currentTopLevelKey, isMediumOrLarger) {
        if (isMediumOrLarger && navigationState.currentTopLevelKey == SettingsNavKey) {
            val settingsStack = navigationState.subStacks[SettingsNavKey]
            if (settingsStack != null && settingsStack.size == 1) {
                settingsStack.add(SettingsDetailNavKey(SettingsCategory.PROFILE))
            }
        }
    }

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
        currentUser?.let { user -> openDetailFromList(ProfileNavKey(user.id.toString())) }
    }
    val selectTopLevelItem: (NavKey) -> Unit = { navKey ->
        navigator.navigate(navKey)
        if (isMediumOrLarger && navKey == SettingsNavKey) {
            val settingsStack = navigationState.subStacks[SettingsNavKey]
            if (settingsStack != null && settingsStack.size == 1) {
                settingsStack.add(SettingsDetailNavKey(SettingsCategory.PROFILE))
            }
        }
    }

    // Compact layouts keep the account affordance in top bars; medium+ pins
    // it to the rail bottom (overlay below), matching the reference design.
    val accountAffordance: (@Composable () -> Unit)? =
        if (navigationSuiteLayout == NavigationSuiteType.NavigationRail) {
            null
        } else {
            {
                Box(
                    modifier = Modifier.minimumInteractiveComponentSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    UserAvatar(
                        name = currentUser?.displayName?.ifEmpty { null } ?: currentUser?.username,
                        avatarUrl = currentUser?.avatarUrl,
                        size = 28.dp,
                        onClick = openProfile,
                    )
                }
            }
        }

    val isMiuix = isMiuixTheme()
    val sceneContent: @Composable (Modifier) -> Unit = { sceneModifier ->
        Scaffold(
            modifier = sceneModifier,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = {
                AppSnackbarHost(
                    snackbarState = snackbarState,
                    modifier = Modifier.windowInsetsPadding(
                        WindowInsets.safeDrawing.exclude(WindowInsets.ime)
                    )
                )
            },
            bottomBar = {
                if (isMiuix && navigationSuiteLayout == NavigationSuiteType.NavigationBar && showNavigationSuite) {
                    MiuixNavigationBar(
                        color = MiuixTheme.colorScheme.surface,
                    ) {
                        topLevelNavigationItems.forEach { item ->
                            val selected = item.navKey == navigationState.currentTopLevelKey
                            MiuixNavigationBarItem(
                                selected = selected,
                                onClick = { selectTopLevelItem(item.navKey) },
                                icon = if (selected) item.selectedIcon else item.unselectedIcon,
                                label = item.label(strings),
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (isMiuix) MiuixTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.background
                        )
                ) {
                    val baseDirective = calculatePaneScaffoldDirective(windowAdaptiveInfo)
                    val isExpandedOrWider = windowAdaptiveInfo.windowSizeClass
                        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
                    val directive = remember(baseDirective, isExpandedOrWider) {
                        baseDirective.copy(
                            maxHorizontalPartitions = if (isExpandedOrWider) 3 else baseDirective.maxHorizontalPartitions,
                            horizontalPartitionSpacerSize = 1.dp,
                            defaultPanePreferredWidth = 320.dp,
                        )
                    }
                    val strategy = rememberListDetailSceneStrategy<NavKey>(
                        shouldHandleSinglePaneLayout = true,
                        directive = directive,
                    )
                    val currentEntries = navigationState.toEntries(entryProvider)

                    NavDisplay(
                        entries = currentEntries,
                        sceneStrategies = listOf(strategy),
                        onBack = { navigator.goBack() },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    Box(modifier = modifier) {
        CompositionLocalProvider(LocalAccountAffordance provides accountAffordance) {
            if (isMiuix) {
                Row(modifier = Modifier.fillMaxSize()) {
                    if (navigationSuiteLayout == NavigationSuiteType.NavigationRail && showNavigationSuite) {
                        MiuixNavigationRail(
                            color = MiuixTheme.colorScheme.surface,
                        ) {
                            topLevelNavigationItems.forEach { item ->
                                val selected = item.navKey == navigationState.currentTopLevelKey
                                MiuixNavigationRailItem(
                                    selected = selected,
                                    onClick = { selectTopLevelItem(item.navKey) },
                                    icon = if (selected) item.selectedIcon else item.unselectedIcon,
                                    label = item.label(strings),
                                )
                            }
                        }
                    }
                    sceneContent(Modifier.weight(1f).fillMaxSize())
                }
            } else {
                NavigationSuiteScaffold(
                    navigationSuiteItems = {
                        topLevelNavigationItems.forEach { item ->
                            val selected = item.navKey == navigationState.currentTopLevelKey
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
                    sceneContent(Modifier.fillMaxSize())
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
