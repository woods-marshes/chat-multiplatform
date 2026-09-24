# 自适应布局（Material 3 Adaptive）

## 概述

项目使用 Material 3 Adaptive 实现响应式布局，核心组件：

| 组件 | 用途 |
|---|---|
| `NavigationSuiteScaffold` | 底部导航栏（compact）/ 导航轨（medium）/ 永久导航 drawers（expanded），末位附加当前用户头像 item |
| `ListDetailSceneStrategy` | 列表-详情双/三窗格布局 |
| `WindowSizeClass` | 窗口尺寸断点判断 |

> **注**：ModalNavigationDrawer（侧滑抽屉）已按 M3 Expressive 的建议移除
> （官方不再推荐 Navigation Drawer，改用 expanded navigation rail）。
> 原抽屉承载的登出迁入 Settings 页，个人资料入口由导航栏末位的头像 item 承担。

## WindowSizeClass 断点

```kotlin
// Compact:  < 600dp
// Medium:   600dp ~ 840dp
// Expanded: > 840dp
```

项目中使用的 `currentWindowAdaptiveInfoV2()` 提供 V2 版本的窗口尺寸信息（修复了 V1 的某些宽度计算 bug）。

```kotlin
val windowAdaptiveInfo = currentWindowAdaptiveInfoV2()
val isMediumOrLarger = windowAdaptiveInfo.windowSizeClass
    .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) // >= 600dp
```

## ListDetailSceneStrategy

### 窗格元数据

```kotlin
val listPaneMeta = remember { ListDetailSceneStrategy.listPane() }
val detailPaneMeta = remember { ListDetailSceneStrategy.detailPane() }
val extraPaneMeta = remember { ListDetailSceneStrategy.extraPane() }
```

### 窗格分配

| 页面 | 窗格 | 说明 |
|---|---|---|
| Conversations | `listPane` | 会话列表 |
| Contacts | `listPane` | 联系人列表 |
| Chat | `detailPane` | 聊天详情 |
| Profile | `detailPane` + `extraPane` | 用户资料（可同时出现在第二和第三窗格） |
| Settings | 无窗格 | 全屏模式 |
| Search | 无窗格 | 全屏模式 |

### 在 NavDisplay 中使用

```kotlin
val strategy = rememberListDetailSceneStrategy<NavKey>()
NavDisplay(
    entries = navigationState.toEntries(entryProvider),
    sceneStrategies = listOf(strategy),
    onBack = { navigator.goBack() },
    modifier = Modifier.fillMaxSize()
)
```

### 布局行为

| 窗口宽度 | 布局 |
|---|---|
| Compact | 单窗格，列表和详情各自全屏（类似手机） |
| Medium (600-840dp) | 双窗格（列表 + 详情，类似平板竖屏） |
| Expanded (>840dp) | 三窗格（列表 + 详情 + 额外窗格，类似桌面） |

在 compact 模式下从列表导航到详情时，`NavigationSuiteScaffold` 的导航栏会自动隐藏。

## NavigationSuiteScaffold

```kotlin
NavigationSuiteScaffold(
    layoutType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(windowAdaptiveInfo),
    navigationSuiteItems = {
        topLevelItems.forEach { item ->
            item(
                selected = navigationState.currentKey == item.key,
                onClick = { navigator.navigate(item.key) },
                icon = { Icon(...) },
                label = { Text(...) },
            )
        }
    }
) { ... }
```

### 可见性控制

```kotlin
val isTopLevelRoute = navigationState.currentKey in navigationState.topLevelKeys
val showNavigationSuite = isTopLevelRoute || isMediumOrLarger
```

- **Compact + 详情页** → 隐藏导航栏（让内容全屏）
- **Compact + 顶层** → 显示导航栏
- **Medium+** → 始终显示（用作导航轨或永久抽屉）

## 导航栏账户入口（替代已移除的抽屉）

`NavigationSuiteScaffold` 的 `navigationSuiteItems` 末位附加一个非选中态的头像 item，
点击进入当前登录用户的 Profile（`sessionManager.currentUser` 提供头像与昵称）：

```kotlin
NavigationSuiteScaffold(
    navigationSuiteItems = {
        topLevelNavigationItems.forEach { ... }
        item(
            selected = false,
            onClick = { currentUser?.let { navigator.navigate(ProfileNavKey(it.id)) } },
            icon = { UserAvatar(name = ..., avatarUrl = ..., size = 26.dp) },
            label = { Text(strings.profileTitle) },
        )
    },
) { ... }
```

登出入口在 Settings 页（`settingsEntry(onBack, onLogout)`），经 `SessionManager.logout()`
统一编排断连、清凭据与关库时序。

## AuthScreen 自适应

```kotlin
@Composable
fun AuthScreen() {
    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    val isDesktopOrTablet = windowSizeClass.isWidthAtLeastBreakpoint(
        WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND
    )

    if (isDesktopOrTablet) {
        // 横向布局：品牌区域（左）+ 登录表单 Card（右，80% 宽度）
        Row { BrandingColumn(); LoginCard() }
    } else {
        // 纵向布局：品牌 + 可滚动的登录表单
        Column(modifier = Modifier.verticalScroll(...)) { Branding(); LoginForm() }
    }
}
```

## WindowInsets

```kotlin
// 禁用内容窗口内边距（由 Scaffold 内部自行处理）
contentWindowInsets = WindowInsets(0, 0, 0, 0)

// Snackbar 使用安全绘制内边距（减去 IME）
AppSnackbarHost(modifier = Modifier.padding(safeDrawing.only(WindowInsetsSides.Top + ... )))
```

## 依赖

```kotlin
implementation(libs.compose.material3.adaptive)
implementation(libs.compose.material3.adaptive.layout)
implementation(libs.compose.material3.adaptiveNavigation3)
implementation(libs.compose.material3.adaptive.navigation.suite)
```

## 关键文件

| 文件 | 内容 |
|---|---|
| `composeApp/.../app/MainApp.kt` | 顶层布局组合：AnimatedContent（登录门）+ NavigationSuiteScaffold + NavDisplay |
| `composeApp/.../app/navigation/TopLevelNavigation.kt` | 顶层标签页项定义 |
| `composeApp/.../app/navigation/NavConfiguration.kt` | NavKey 序列化器注册 |
| `composeApp/.../app/session/SessionManager.kt` | 会话编排（登录态 / 数据库 / WebSocket / 登出序列） |
| `features/auth/.../ui/AuthScreen.kt` | AuthScreen 自适应布局 |
