# `:webApp`

Compose Web（Kotlin/JS + WasmJS）平台入口点，遵循 [KMP 官方推荐结构](https://kotlinlang.org/docs/multiplatform-project-recommended-structure.html)从 `:composeApp` 中拆出。

- 仅包含启动代码：`src/webMain/kotlin/.../Main.kt`（`ComposeViewport` + Koin 初始化）
- `src/webMain/resources/`：`index.html`（脚本入口为 `webApp.js`）与 `styles.css`
- `webpack.config.d/sqljs-config.js`：SQLDelight sql.js WASM 驱动的 webpack 配置
- 依赖 `:composeApp`（共享 Compose UI 装配）；sql.js 相关 npm 依赖随 `webMain` 一起声明在此

```bash
./gradlew :webApp:jsBrowserDevelopmentRun      # JS 开发模式
./gradlew :webApp:wasmJsBrowserDevelopmentRun  # Wasm 开发模式
./gradlew :webApp:jsBrowserDistribution        # JS 生产构建
```

> 注意：浏览器端生产 Web 前端是 `:web`（Kotlin/JS + React）；`:webApp` 为 Compose 技术栈的 Web 客户端入口。
