# `:desktopApp`

Desktop（JVM）平台入口点，遵循 [KMP 官方推荐结构](https://kotlinlang.org/docs/multiplatform/multiplatform-project-recommended-structure.html)从 `:composeApp` 中拆出。

- 仅包含启动代码：`src/main/kotlin/.../Main.kt`（`nucleusApplication(backend = NucleusBackend.Tao)` + `DecoratedWindow` + Koin 初始化）
- `src/main/resources/logback.xml` 桌面端日志配置
- 窗口栈使用 **Nucleus Tao** 后端（`dev.nucleusframework` 插件 + `nucleus.application {}` 打包 DSL）：原生 WebView 与 Compose 同窗口栈合成，文章功能的 WebView 浮层（FAB）在桌面正常显示
- 依赖 `:composeApp`（共享 Compose UI 装配）、`compose.desktop.currentOs` 与 Nucleus 三件套（`nucleus.nucleus-application` / `nucleus.decorated-window-tao` / `nucleus.core-runtime`）

```bash
./gradlew :desktopApp:run                            # 运行
./gradlew :desktopApp:packageDistributionForCurrentOS  # 原生打包（Dmg / Msi / Deb）
```

> 运行时系统依赖：Windows 需 WebView2 Runtime（Win11 自带）；Linux 需 WebKit2GTK。
