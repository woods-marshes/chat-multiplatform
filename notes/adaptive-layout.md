# 自适应布局（Material 3 Adaptive）

## 概述

项目使用 Material 3 Adaptive 实现响应式布局，核心组件：

| 组件 | 用途 |
|---|---|
| `NavigationSuiteScaffold` | 底部导航栏（compact）/ 导航轨（medium）/ 永久导航 drawers（expanded） |
| `ListDetailSceneStrategy` | 列表-详情双/三窗格布局 |
| `ModalNavigationDrawer` | 侧滑抽屉（用户菜单） |
| `WindowSizeClass` | 窗口尺寸断点判断 |

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

## ModalNavigationDrawer

包裹整个 `NavigationSuiteScaffold`，提供侧滑用户菜单：

```kotlin
ModalNavigationDrawer(
    drawerState = drawerState,
    drawerContent = {
        MeDrawerSheet(
            displayName = ...,
            onProfileClick = { ... },
            onSettingsClick = { ... },
            onLogoutClick = { ... },
        )
    },
    gesturesEnabled = showNavigationSuite,  // 仅在顶层路由或大屏时启用滑动手势
) {
    NavigationSuiteScaffold(...) { ... }
}
```

### 手势策略

- 在 Conversations / Contacts 顶层 → 可滑动打开
- 在 Chat / Profile 等详情页（compact） → 禁用手势，防止误触
- 在 Medium+ → 始终可滑动

## AuthScreen 自适应

```kotlin
@Composable
fun AuthScreen(onAuthSuccess: () -> Unit) {
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
| `composeApp/.../app/MainApp.kt` | 顶层布局组合：AnimatedContent + ModalNavigationDrawer + NavDisplay |
| `composeApp/.../app/MainApp.kt` | `NavigationSuiteScaffold` 配置 + 自适应逻辑 |
| `composeApp/.../app/navigation/TopLevelNavigation.kt` | 顶层标签页项定义 |
| `composeApp/.../app/navigation/NavConfiguration.kt` | NavKey 序列化器注册 |
| `composeApp/.../app/ui/MeDrawerSheet.kt` | 侧滑抽屉菜单内容 |
| `features/auth/.../ui/AuthScreen.kt` | AuthScreen 自适应布局 |
