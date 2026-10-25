# AGENTS.md

**重要：所有编程助手必须始终使用中文对话。回复、解释、建议、计划 — 全部中文。但生成的代码（含注释和 KDoc）必须使用英文。**

**重要：当你对某件事不明白、不清楚、不确定、或需要询问用户才能继续时，必须先向用户确认清楚再行动，禁止猜测后直接动手。**

## Ponytail 官方规则入口

开始任务前，必须完整读取并遵守 [Ponytail 官方原文](notes/ponytail/AGENTS.md)，不要用摘要或本文件中的项目约束代替读取原文。

官方原文作为独立文件逐字保留，不翻译、不改写、不删减、不添加项目规则。下文仅为本项目约束，不属于 Ponytail 官方提示词。

本仓库使用规则文件接入，不代表已安装 Ponytail 插件、技能命令或 hooks。其他客户端必须加载本入口及其引用的原文；Git 同步不传播用户级插件安装和授权。

以下架构和命令以主分支为参考，执行前核对当前分支的实际构建文件与模块。

## 核心约束

- **Koin DI**：ViewModel → `viewModelOf(::X)` 或 `viewModel { (params) -> X(get(), params) }`。Repository → `single<Interface> { Impl(get()) }`。Repository 接口和实现均在 `core:data`。禁止 nullable 构造函数依赖。ViewModel 直接注入 Repository，不在 domain 中建无意义的转发 UseCase。详见 `notes/koin-di.md`
- **版本目录**：`gradle/libs.versions.toml` 是依赖版本的唯一来源。添加依赖时先加到 catalog，再通过 `libs.<alias>` 引用。完整版本清单见 `notes/versions.md`
- **平台分离**：composewebview 库不支持 Kotlin/JS，需用 `expect`/`actual` 分离。非 JS 平台依赖加在 `jvmMain`/`androidMain`/`wasmJsMain` 的 `dependencies` 块中。KMP 不支持 JVM+Android 共享源集，需各自独立 `actual`。
- **桌面 WebView2 UDF**：项目统一使用可写的共享 `dataDirectory`（jvm actual 设为 `~/.chat-multiplatform/webview`），这是应用策略，并非 WebView2 的同进程限制。默认目录位于可执行文件旁，可能因权限不足失败。不同 UDF 受支持；共享浏览器进程的环境选项必须兼容。创建失败应检查具体阶段与 HRESULT，不能仅凭目录差异归因。

## 架构速览

```
依赖方向：androidApp / desktopApp / webApp（平台入口）→ composeApp（共享 UI 装配）→ core/* + features/*     server → core:model + core:network     web → core:common + core:model + core:network + core:datastore
```

| 层 | 模块 | 关键内容 |
|---|---|---|
| Common | `core:common` | AppDispatchers, Koin commonModule, PlatformContext |
| Model | `core:model` | 领域模型、Error 密封类、UiState、Article/ArticleStatus（纯数据，仅依赖 kotlinx-serialization） |
| Data | `core:data` | 仓库接口 + 实现，编排 network ↔ database ↔ datastore，ArticleRepository（离线优先），RemoteMediator |
| Network | `core:network` | Ktor HttpClient, REST API (ArticleApi), WebSocket, BuildKonfig, TokenRefresher |
| Contract | `core:contract` | 两端共享的线协议契约：DTO、Ktor Resources 路由 (V1)、JSON/Protobuf 配置（server 与 client 共用同一份声明） |
| Database | `core:database` | SQLDelight（含 Article；表数量以当前 schema 为准）+ DAO + QueryPagingSource |
| Datastore | `core:datastore` | Key-value 存储（token、偏好设置） |
| UI | `core:ui` | Compose Multiplatform 组件, Material 3 (Miuix), i18n (Lyricist), ArticleCardItem |
| Navigation | `core:navigation` | Navigation3 路由 |
| Features | `features/*` | 每个 feature 含 model/ui/di/navigation 四层 |
| Server | `server/` | Ktor + Netty, Exposed ORM（表数量以当前 schema 为准）, JWT auth, WebSocket, Ktor Resources 路由 |
| Web 前端 | `web/` | Kotlin/JS (ES2015) + React + Tiptap，通过 Koin 注入 core 模块 |
| Tiptap 桥接 | `tiptap-bridge/` | React 组件库：UMD 打包供 web 调用 + Vite 自包含 HTML 供 ComposeNativeWebView 加载 |

**DI 注册顺序**：`commonModule → dataStoreModule → databaseModule → daosModule → serializersModule → networkModule → dataModule → sessionModule → feature ViewModel modules`

## 文章系统概览

```
tiptap-bridge (React/Vite)
├── UMD 构建 ──▶ web (Kotlin/JS) ──▶ 浏览器直接渲染
└── 自包含 HTML ──▶ ComposeNativeWebView ──▶ Desktop/Android 原生 WebView
                           │
                    features/article (浏览) + features/article-editor (编辑)
                           │
                    ArticleRepository (离线优先: DB → API → Paging)
                           │
                    core:network (ArticleApi, UUIDv7 游标分页)
                           │
                    server (ArticleService → ArticleRepository → Exposed ORM)
```

- **web 模块**：使用 `@file:JsModule` 声明调用 UMD，列表 + 详情 + 编辑均已实现分页
- **Compose 客户端**：features/article（列表 + 详情 WebView）+ features/article-editor（Tiptap 编辑器 WebView）
- **server**：UUIDv7 游标分页（`beforeId`），完整 REST CRUD
- **ComposeNativeWebView**：`dev.nucleusframework:composewebview:1.0.3`，支持 android/jvm/wasmJs，不支持 js；桌面端由 Nucleus Tao 后端托管（`desktopApp` 入口为 `nucleusApplication(backend = NucleusBackend.Tao)`），WebView 与 Compose 同窗口栈合成，可正常叠加 Compose 浮层
- **JS Bridge 通信**：Base64 编码传 JSON，`rememberUpdatedState` 防闭包过期，"一次性初始化"防白屏重载

## 常用命令

| 目的 | 命令 |
|---|---|
| 编译检查（客户端） | `./gradlew :desktopApp:compileKotlin` |
| 运行全部测试 | `./gradlew check` |
| 运行桌面客户端 | `./gradlew :desktopApp:run` |
| 运行服务端 (端口 9051) | `./gradlew :server:run` |
| 服务端测试 | `./gradlew :server:test` |
| Web 开发构建 | `./gradlew :web:jsBrowserDevelopmentRun` |
| Web 生产构建 | `./gradlew :web:jsBrowserDistribution` |
| Tiptap WebView 构建 | `npm run build:webview`（在 tiptap-bridge/ 下） |
| 列出所有任务 | `./gradlew tasks` |
| 更新 yarn lock（改动 Kotlin/JS npm 依赖后必跑，构建已设 FAIL） | `./gradlew kotlinUpgradeYarnLock` |

Windows PowerShell 使用 `./gradlew.bat` 替代 `./gradlew`。旧分支没有 desktopApp 时，先确认 `:composeApp:jvmMainClasses` 和 `:composeApp:run` 是否存在，不直接套用主分支命令。

**注意**：配置缓存已启用，遇到异常行为用 `--no-configuration-cache`。JVM daemon: `-Xmx3072M`。本机代理 `127.0.0.1:10808`（gradle.properties 未配置，需要时以 `-Dhttp(s).proxyHost/Port` 传入）。更多命令和测试详情见 `notes/testing.md`。

## 约定

- **Git Commit**：`<scope>: <summary>`，scope 可选 `client`/`server`/`web`/`core`/`features`/`build`/`deps`/`tiptap`，主题 ≤72 字符，英文
- **代码风格**：Kotlin official style，4 空格缩进，显式 import（不用通配符），英文标识符和注释
- **Compose**：参数顺序 → required callbacks, `Modifier`, flags, visual params, content lambda 最后。颜色用 `MiuixTheme.colorScheme.*`。不确定 API 用法时用当前环境可用的网页检索或浏览工具 查官方文档，禁止猜测
- **失败策略**：server 是数据权威方，跨多表写入 → 单事务 + `Err(...).bind()` 整体回滚，不用 runCatching 吞错；client 往本地缓存应用服务端批量数据 → 逐行隔离（坏行跳过、好行入库，一条坏数据不得中止整批同步），`runCatching` 只允许包住单行插入，不许包转换逻辑或整段流程
- **JVM 目标**：core/features（约定插件固定）与 composeApp/Android → JVM 17，desktopApp → JVM 25，server → JVM 25
- **JS 目标**：web 模块 → ES2015，browser 平台
- **API 基址**：`http://127.0.0.1:9051/v1/`。复制 `network-config.properties.template` → `network-config.properties` 自定义（该文件已 gitignore）
- **Web 模块特殊规则**：
  - Kotlin/JS 通过 `@file:JsModule` + `@file:JsNonModule` 声明外部 JS 模块
  - React 组件包装为 `FC<Props>` 的 `external` 声明或 `val X = FC<XProps> { ... }` 的 Kotlin DSL
  - JSON 序列化：Kotlin `JsonElement` ↔ JS `dynamic` 需通过 `JSON.stringify` + `ProjectJson` 手动转换

## 工作效率与验证

- 优先用内容检索（例如 `rg`）定位关键行，再按需读取上下文，避免反复读取大文件。
- 重要功能或重构前先给出简短计划；复杂多步骤任务使用当前客户端可用的任务工具，或简短文本记录进度。小改动不强制建计划。
- 上文已提供的信息直接引用，不复述；不主动输出完整文件。长对话按客户端能力建议压缩上下文或提供交接摘要。
- 对不确定的 Compose / Kotlin / Ktor / React API，先查下方官方文档；无法核实且会影响方案时向用户确认，不猜测接口。
- 修改前检查 Git 状态，保护用户未提交工作，不擅自 reset、stash、覆盖或合并无关业务改动。
- 选择覆盖改动风险的最小验证；明确列出实际执行的命令、结果和未执行原因。纯规则修改检查 diff、引用和跨分支文件一致性即可。

## 实时文档查询

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
| `notes/navigation3.md` | Navigation 3 核心：back stack、NavEntry、Scene、SceneStrategy、动画、模块化 |
| `notes/adaptive-layout.md` | M3 Adaptive：WindowSizeClass、ListDetailSceneStrategy、NavigationSuiteScaffold、Drawer、Canonical Layouts |
| `notes/koin-di.md` | Koin DI 完整规则、代码示例、反模式、Scope、测试 |
| `notes/ktor-di.md` | Server 端 Ktor + Koin 集成 |
| `notes/versions.md` | 完整依赖版本清单 |
| `notes/testing.md` | 各模块测试配置与命令 |
| `notes/workflows.md` | 添加 Feature / 修改数据库 / 修复 Bug 详细步骤 |

## 来源与许可

- 官方仓库：https://github.com/DietrichGebert/ponytail
- 固定上游提交：`2961b52fc1fe6a5a87c7a959a79512e5c2f3ffe5`
- 官方规则来源：https://github.com/DietrichGebert/ponytail/blob/2961b52fc1fe6a5a87c7a959a79512e5c2f3ffe5/AGENTS.md
- 本地原文：`notes/ponytail/AGENTS.md`
- 原始文件 SHA-256：`b98b15e1d683ee205e124b52b8d330128593f4a87b545cf8185e2712cd1bb25f`
- 上游 MIT 许可原文：`notes/ponytail-LICENSE.txt`
- 更新时从明确的上游提交替换原始文件并重新核验，不在原文中编辑项目要求。
