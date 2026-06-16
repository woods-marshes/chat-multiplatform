# CLAUDE.md

**重要：Claude 必须始终使用中文对话。回复、解释、建议、计划 — 全部中文。但生成的代码（含注释和 KDoc）必须使用英文。**

**重要：当你（Claude）对某件事不明白、不清楚、不确定、或需要询问用户才能继续时，必须先向用户确认清楚再行动，禁止猜测后直接动手。**

## 核心约束

- **Koin DI**：ViewModel → `viewModelOf(::X)`，Repository → `single<Interface> { Impl(get()) }`。Repository 接口和实现均在 `core:data`。禁止 nullable 构造函数依赖。ViewModel 直接注入 Repository，不在 domain 中建无意义的转发 UseCase。详见 `@notes/koin-di.md`
- **版本目录**：`gradle/libs.versions.toml` 是依赖版本的唯一来源。添加依赖时先加到 catalog，再通过 `libs.<alias>` 引用。完整版本清单见 `@notes/versions.md`

## 架构速览

```
依赖方向：composeApp → core/* + features/*     server → core:model + core:network     web → core:common + core:model + core:network + core:datastore
```

| 层 | 模块 | 关键内容 |
|---|---|---|
| Common | `core:common` | AppDispatchers, Koin commonModule, PlatformContext |
| Model | `core:model` | 领域模型、Error 密封类、UiState、Article/ArticleStatus（纯数据，仅依赖 kotlinx-serialization） |
| Domain | `core:domain` | 预留：未来有真实业务逻辑的 UseCase（当前 module{} 空壳） |
| Data | `core:data` | 仓库接口 + 实现，编排 network ↔ database ↔ datastore |
| Network | `core:network` | Ktor HttpClient, REST API (ArticleApi 等), WebSocket, DTOs, Ktor Resources 路由定义 (V1) |
| Database | `core:database` | SQLDelight（7 表，含 Article）+ DAO；`core:database-room` 为 Room 3.0 替代 |
| Datastore | `core:datastore` | Key-value 存储（token、偏好设置） |
| UI | `core:ui` | Compose Multiplatform 组件, Material 3 (Miuix), i18n (Lyricist) |
| Navigation | `core:navigation` | Navigation3 路由 |
| Features | `features/*` | 每个 feature 含 model/ui/di/navigation 四层 |
| Server | `server/` | Ktor + Netty, Exposed ORM (9 表), JWT auth, WebSocket, Ktor Resources 路由 |
| Web 前端 | `web/` | Kotlin/JS (ES2015) + React + Tiptap 富文本编辑器，通过 Koin 注入 core 模块 |
| Tiptap 桥接 | `tiptap-bridge/` | React 组件库，打包为 UMD 供 Kotlin/JS 调用，含编辑器 (SimpleEditor) 和静态渲染器 (renderArticleContent) |

**DI 注册顺序**：`commonModule → dataStoreModule → serializersModule → daosModule → networkModule → dataModule → domainModule → feature ViewModel modules`

## 文章系统概览

```
tiptap-bridge (React/TS) ──UMD──▶ web (Kotlin/JS) ──ArticleApi (Ktor)──▶ server (Ktor/Exposed)
                                        │
                                        ▼
                               core:network (ArticleApi, DTOs)
                                        │
                                        ▼
                               core:model (Article, ArticleStatus, ArticleStats)
                                        │
                                        ▼
                               core:database (Article.sq → ArticleEntity)
```

- **web 模块**：使用 `@file:JsModule("./tiptap-editor-bridge.umd.js")` 外部声明调用 Tiptap 编辑器和静态渲染器
- **ArticleRepository**（web/storage）：封装 ArticleApi 调用，在浏览器端做数据存取
- **server**：ArticleService → ArticleRepository（Exposed ORM），完整的 REST CRUD（含软删除、excerpt 自动生成）
- **features/article** + **features/article-editor**：空壳模块，等待 Compose Multiplatform 实现（通过 ComposeNativeWebView）
- **ComposeNativeWebView**：`io.github.kdroidfilter:composewebview:1.0.0-beta-02` 已在版本目录中

## 常用命令

| 目的 | 命令 |
|---|---|
| 编译检查（客户端） | `./gradlew :composeApp:jvmMainClasses` |
| 运行全部测试 | `./gradlew check` |
| 运行桌面客户端 | `./gradlew :composeApp:run` |
| 运行服务端 (端口 9051) | `./gradlew :server:run` |
| 服务端测试 | `./gradlew :server:test` |
| Web 开发构建 | `./gradlew :web:jsBrowserDevelopmentRun` |
| Web 生产构建 | `./gradlew :web:jsBrowserDistribution` |
| Tiptap 构建 + 复制 | `./gradlew :web:buildAndCopyTiptap` |
| 列出所有任务 | `./gradlew tasks` |

**注意**：配置缓存已启用，遇到异常行为用 `--no-configuration-cache`。JVM daemon: `-Xmx3072M`。代理: `127.0.0.1:10808`（`gradle.properties`）。更多命令和测试详情见 `@notes/testing.md`。

## 约定

- **Git Commit**：`<scope>: <summary>`，scope 可选 `client`/`server`/`web`/`core`/`features`/`build`/`deps`/`tiptap`，主题 ≤72 字符，英文
- **代码风格**：Kotlin official style，4 空格缩进，显式 import（不用通配符），英文标识符和注释
- **Compose**：参数顺序 → required callbacks, `Modifier`, flags, visual params, content lambda 最后。颜色用 `MiuixTheme.colorScheme.*`。图标优先用 `MiuixIcons.*`（basic）或 `top.yukonga.miuix.kmp.icon.extended.*`（仅当 Miuix 无等价物时回退 `Icons.Default.*`）。不确定 API 用法时用 `WebFetch` 查官方文档，禁止猜测
- **JVM 目标**：composeApp/Android → JVM 17，composeApp/Desktop → JVM 25，server → JVM 25
- **JS 目标**：web 模块 → ES2015，browser 平台
- **API 基址**：`http://127.0.0.1:9051/v1/`。复制 `network-config.properties.template` → `network-config.properties` 自定义（该文件已 gitignore）
- **Web 模块特殊规则**：
  - Kotlin/JS 通过 `@file:JsModule` + `@file:JsNonModule` 声明外部 JS 模块
  - React 组件包装为 `FC<Props>` 的 `external` 声明或 `val X = FC<XProps> { ... }` 的 Kotlin DSL
  - 使用 `kotlinx.browser.window` / `kotlinx.browser.document` 访问浏览器 API
  - JSON 序列化：Kotlin `JsonElement` ↔ JS `dynamic` 需通过 `JSON.stringify` + `ProjectJson` 手动转换

## 对话效率

- **查找代码优先用 `Grep`**（提取关键行），而非 `Read` 整个文件。读大文件用 `offset` + `limit`
- **长对话（>15 轮）时**主动建议用户 `/compact` 压缩上下文
- **复杂多步骤任务**用 `TaskCreate` 跟踪进度
- **上文已提供的信息**直接引用，不复述。不主动输出完整文件内容
- **Bash/PowerShell 中可用**：`rg`、`jq`、`fd`、`gh`、`fzf`。不可用：`docker`、`go`、`make`。完整清单见用户记忆
- **重要功能或重构前**先制定 Plan
- **写 Compose / Kotlin / Ktor / React 代码遇到不确定的 API 时**，用 `WebFetch` 实时查官方文档（URL 见下方），禁止凭记忆猜测

## 实时文档查询

写代码遇到不确定的 API 时，按需 `WebFetch` 以下来源：

| 场景 | 查询 URL |
|---|---|
| Compose Multiplatform 组件/API | `https://www.jetbrains.com/compose-multiplatform/docs/` |
| Android Compose 状态/性能/Modifier | `https://developer.android.com/develop/ui/compose/` + 子路径 |
| Kotlin 标准库 / coroutines | `https://kotlinlang.org/api/kotlinx.coroutines/` |
| Ktor Client/Server | `https://ktor.io/docs/` |
| Koin DI (Compose 集成) | `https://insert-koin.io/docs/reference/koin-compose/` |
| Coil 图片加载 | `https://coil-kt.github.io/coil/compose/` |
| Navigation3 | `https://developer.android.com/jetpack/androidx/releases/navigation` |
| SQLDelight | `https://cashapp.github.io/sqldelight/` |
| ComposeNativeWebView | `https://github.com/kdroidFilter/ComposeNativeWebview` |
| Kotlin/JS React Wrappers | `https://github.com/JetBrains/kotlin-wrappers` |

## 详细文档索引

| 文档 | 内容 |
|---|---|
| `@notes/navigation3.md` | Navigation 3 核心：back stack、NavEntry、Scene、SceneStrategy、动画、模块化 |
| `@notes/adaptive-layout.md` | M3 Adaptive：WindowSizeClass、ListDetailSceneStrategy、NavigationSuiteScaffold、Drawer、Canonical Layouts |
| `@notes/koin-di.md` | Koin DI 完整规则、代码示例、反模式、Scope、测试 |
| `@notes/ktor-di.md` | Server 端 Ktor + Koin 集成 |
| `@notes/versions.md` | 完整依赖版本清单 |
| `@notes/testing.md` | 各模块测试配置与命令 |
| `@notes/workflows.md` | 添加 Feature / 修改数据库 / 修复 Bug 详细步骤 |
