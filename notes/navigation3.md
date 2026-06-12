# 导航系统（Navigation 3）

## 核心概念

Navigation 3 使用**双层回退栈**模型：

```
topLevelStack: [ConversationsNavKey, ContactsNavKey]
                    ↓
subStacks: {
    ConversationsNavKey → [ConversationsNavKey, ChatNavKey("abc"), ProfileNavKey("user1")]
    ContactsNavKey      → [ContactsNavKey, ProfileNavKey("user2")]
}
```

- **topLevelStack**：标签页的访问顺序
- **subStacks**：每个标签页内部的导航历史
- **currentKey**：当前显示的页面 = `currentSubStack.last()`

## NavigationState

```kotlin
// core:navigation/NavigationState.kt
class NavigationState(
    val startKey: NavKey,
    val topLevelStack: NavBackStack<NavKey>,
    val subStacks: Map<NavKey, NavBackStack<NavKey>>,
) {
    val currentTopLevelKey: NavKey get() = topLevelStack.last()
    val currentSubStack: NavBackStack<NavKey> get() = subStacks[currentTopLevelKey]!!
    val currentKey: NavKey get() = currentSubStack.last()
}
```

创建方式：
```kotlin
val navigationState = rememberNavigationState(
    startKey = ConversationsNavKey,
    topLevelKeys = setOf(ConversationsNavKey, ContactsNavKey, SettingsNavKey),
    configuration = navConfiguration
)
```

`toEntries()` 将双层栈扁平化为 `NavDisplay` 可用的 `SnapshotStateList<NavEntry<NavKey>>`，并附加 `SaveableStateHolder` 和 `ViewModelStore` 装饰器。

## Navigator

```kotlin
// core:navigation/Navigator.kt
class Navigator(val state: NavigationState) {
    fun navigate(key: NavKey)
    fun goBack()
}
```

### navigate 逻辑

| 场景 | 行为 |
|---|---|
| `key == currentTopLevelKey` | 清空当前子栈（回到标签页根） |
| `key in topLevelKeys`（不同标签） | 切换到目标标签页 |
| 其他（详情页） | 推入当前子栈，移除旧实例防止重复 |

### goBack 逻辑

| 场景 | 行为 |
|---|---|
| `currentKey == startKey` | 抛出异常（应退出 App） |
| `currentKey in topLevelKeys`（子栈根） | 弹出 topLevelStack 回到上一个标签 |
| 其他 | 弹出当前子栈 |

## NavKey 清单

| NavKey | 类型 | 参数 | 用途 |
|---|---|---|---|
| `ConversationsNavKey` | data object | — | 会话列表（top-level，startKey） |
| `ContactsNavKey` | data object | — | 联系人列表（top-level） |
| `SettingsNavKey` | data object | — | 设置（top-level） |
| `ChatNavKey` | data class | `conversationId: String` | 聊天详情 |
| `ProfileNavKey` | data class | `userId: String` | 用户资料 |
| `AuthNavKey` | data object | — | 登录/注册（独立，不在主栈中） |
| `SearchNavKey` | data object | — | 搜索 |

## NavKey 序列化配置

```kotlin
// composeApp/.../NavConfiguration.kt
@Serializable
sealed interface NavKey {
    @Serializable
    data object ConversationsNavKey : NavKey

    @Serializable
    data object ContactsNavKey : NavKey

    @Serializable
    data object SettingsNavKey : NavKey

    @Serializable
    @SerialName("chat")
    data class ChatNavKey(val conversationId: String) : NavKey

    @Serializable
    @SerialName("profile")
    data class ProfileNavKey(val userId: String) : NavKey

    @Serializable
    data object AuthNavKey : NavKey
}
```

> **注意**：`SearchNavKey` 在 entry provider 中已注册但未加入序列化器，若需状态保存需补上。

## Entry 注册模式

每个 feature 在 `navigation/` 包中提供 NavKey 定义 + entry 注册函数：

```kotlin
// 无参数路由
fun EntryProviderScope<NavKey>.conversationsEntry(
    onNavigateToChat: (ChatNavKey) -> Unit,
    onMenuClick: () -> Unit,
    metadata: Map<String, NavEntryMetadata>,
) {
    entry(
        key = { entry: NavEntry<ConversationsNavKey> -> entry.key },
        metadata = { _, _ -> metadata },
        content = { key, _, _ ->
            ConversationsScreen(
                onConversationClick = { onNavigateToChat(ChatNavKey(it)) },
                onMenuClick = onMenuClick,
            )
        }
    )
}

// 参数化路由
fun EntryProviderScope<NavKey>.chatEntry(
    onBack: () -> Unit,
    onNavigateToProfile: (ProfileNavKey) -> Unit,
    metadata: Map<String, NavEntryMetadata>,
) {
    entry(
        key = { entry: NavEntry<ChatNavKey> -> entry.key },
        metadata = { _, _ -> metadata },
        content = { key, _, _ ->
            ChatScreen(
                viewModel = koinViewModel(
                    key = "chat_${key.conversationId}",
                    parameters = { parametersOf(key.conversationId) }
                ),
                onBack = onBack,
                onNavigateToProfile = { onNavigateToProfile(ProfileNavKey(it)) }
            )
        }
    )
}
```

## 认证屏障

登录/登出使用 `AnimatedContent` 在顶层切换，不参与 Navigation3 的导航栈：

```kotlin
// MainApp.kt
AnimatedContent(targetState = appState.isLoggedIn) { loggedIn ->
    when (loggedIn) {
        null -> CircularProgressIndicator()
        false -> AuthScreen(onAuthSuccess = { /* 登录成功后刷新 */ })
        true -> MainContent(...)  // 登录后才初始化 NavigationState
    }
}
```

这确保退出登录后整个导航栈被销毁，不会出现"回退到已登录页面"的问题。

## 依赖

`core:navigation` 模块依赖：
- `androidx.navigation3.ui`
- `androidx.compose.material3.adaptiveNavigation3`
- `androidx.lifecycle.viewmodelNavigation3`
- `androidx.savedstate`
