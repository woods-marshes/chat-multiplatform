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

## Module dependency graph

<!--region graph-->
```mermaid
---
config:
  layout: elk
  elk:
    nodePlacementStrategy: SIMPLE
---
graph TB
  subgraph :features
    direction TB
    :features:article[article]:::compose-multiplatform
    :features:article-editor[article-editor]:::compose-multiplatform
    :features:auth[auth]:::compose-multiplatform
    :features:chat[chat]:::compose-multiplatform
    :features:contacts[contacts]:::compose-multiplatform
    :features:conversations[conversations]:::compose-multiplatform
    :features:profile[profile]:::compose-multiplatform
    :features:search[search]:::compose-multiplatform
    :features:settings[settings]:::compose-multiplatform
  end
  subgraph :core
    direction TB
    :core:common[common]:::kotlin-multiplatform
    :core:data[data]:::kotlin-multiplatform
    :core:database[database]:::kotlin-multiplatform
    :core:datastore[datastore]:::kotlin-multiplatform
    :core:domain[domain]:::kotlin-multiplatform
    :core:model[model]:::kotlin-multiplatform
    :core:navigation[navigation]:::compose-multiplatform
    :core:network[network]:::kotlin-multiplatform
    :core:network-api[network-api]:::kotlin-multiplatform
    :core:ui[ui]:::compose-multiplatform
  end
  :webApp[webApp]:::compose-multiplatform
  :composeApp[composeApp]:::compose-multiplatform

  :composeApp -.-> :core:common
  :composeApp -.-> :core:data
  :composeApp -.-> :core:database
  :composeApp -.-> :core:datastore
  :composeApp -.-> :core:domain
  :composeApp -.-> :core:model
  :composeApp -.-> :core:navigation
  :composeApp -.-> :core:network
  :composeApp -.-> :core:ui
  :composeApp -.-> :features:article
  :composeApp -.-> :features:article-editor
  :composeApp -.-> :features:auth
  :composeApp -.-> :features:chat
  :composeApp -.-> :features:contacts
  :composeApp -.-> :features:conversations
  :composeApp -.-> :features:profile
  :composeApp -.-> :features:search
  :composeApp -.-> :features:settings
  :core:data -.-> :core:common
  :core:data -.-> :core:database
  :core:data -.-> :core:datastore
  :core:data -.-> :core:model
  :core:data -.-> :core:network
  :core:database -.-> :core:common
  :core:database -.-> :core:model
  :core:datastore -.-> :core:common
  :core:datastore -.-> :core:model
  :core:domain -.-> :core:common
  :core:domain -.-> :core:model
  :core:network -.-> :core:common
  :core:network -.-> :core:datastore
  :core:network -.-> :core:model
  :core:network --> :core:network-api
  :core:network-api --> :core:model
  :core:ui -.-> :core:common
  :core:ui --> :core:model
  :features:article -.-> :core:common
  :features:article -.-> :core:data
  :features:article -.-> :core:domain
  :features:article -.-> :core:model
  :features:article -.-> :core:navigation
  :features:article -.-> :core:network
  :features:article -.-> :core:ui
  :features:article-editor -.-> :core:common
  :features:article-editor -.-> :core:data
  :features:article-editor -.-> :core:domain
  :features:article-editor -.-> :core:model
  :features:article-editor -.-> :core:navigation
  :features:article-editor -.-> :core:network
  :features:article-editor -.-> :core:ui
  :features:auth -.-> :core:common
  :features:auth -.-> :core:data
  :features:auth -.-> :core:domain
  :features:auth -.-> :core:model
  :features:auth -.-> :core:navigation
  :features:auth -.-> :core:ui
  :features:chat -.-> :core:common
  :features:chat -.-> :core:data
  :features:chat -.-> :core:domain
  :features:chat -.-> :core:model
  :features:chat -.-> :core:navigation
  :features:chat -.-> :core:ui
  :features:contacts -.-> :core:common
  :features:contacts -.-> :core:data
  :features:contacts -.-> :core:domain
  :features:contacts -.-> :core:model
  :features:contacts -.-> :core:navigation
  :features:contacts -.-> :core:ui
  :features:conversations -.-> :core:common
  :features:conversations -.-> :core:data
  :features:conversations -.-> :core:domain
  :features:conversations -.-> :core:model
  :features:conversations -.-> :core:navigation
  :features:conversations -.-> :core:ui
  :features:profile -.-> :core:common
  :features:profile -.-> :core:data
  :features:profile -.-> :core:domain
  :features:profile -.-> :core:model
  :features:profile -.-> :core:navigation
  :features:profile -.-> :core:ui
  :features:search -.-> :core:common
  :features:search -.-> :core:data
  :features:search -.-> :core:domain
  :features:search -.-> :core:model
  :features:search -.-> :core:navigation
  :features:search -.-> :core:ui
  :features:settings -.-> :core:common
  :features:settings -.-> :core:data
  :features:settings -.-> :core:datastore
  :features:settings -.-> :core:domain
  :features:settings -.-> :core:model
  :features:settings -.-> :core:navigation
  :features:settings -.-> :core:ui
  :webApp -.-> :composeApp
  :webApp -.-> :core:common

classDef android-application fill:#CAFFBF,stroke:#000,stroke-width:2px,color:#000;
classDef desktop-application fill:#BDB2FF,stroke:#000,stroke-width:2px,color:#000;
classDef compose-multiplatform fill:#FFD6A5,stroke:#000,stroke-width:2px,color:#000;
classDef ktor-server fill:#FDFFB6,stroke:#000,stroke-width:2px,color:#000;
classDef kotlin-multiplatform fill:#9BF6FF,stroke:#000,stroke-width:2px,color:#000;
classDef unknown fill:#FFADAD,stroke:#000,stroke-width:2px,color:#000;
```

<details><summary>📋 Graph legend</summary>

```mermaid
graph TB
  AndroidApplication[Android App]:::android-application
  DesktopApplication[Desktop App]:::desktop-application
  ComposeMultiplatform[Compose UI]:::compose-multiplatform
  KtorServer[Ktor Server]:::ktor-server
  KotlinMultiplatform[Kotlin Multiplatform]:::kotlin-multiplatform

  AndroidApplication -.-> KotlinMultiplatform
  KotlinMultiplatform --> ComposeMultiplatform

classDef android-application fill:#CAFFBF,stroke:#000,stroke-width:2px,color:#000;
classDef desktop-application fill:#BDB2FF,stroke:#000,stroke-width:2px,color:#000;
classDef compose-multiplatform fill:#FFD6A5,stroke:#000,stroke-width:2px,color:#000;
classDef ktor-server fill:#FDFFB6,stroke:#000,stroke-width:2px,color:#000;
classDef kotlin-multiplatform fill:#9BF6FF,stroke:#000,stroke-width:2px,color:#000;
classDef unknown fill:#FFADAD,stroke:#000,stroke-width:2px,color:#000;
```

</details>
<!--endregion-->
