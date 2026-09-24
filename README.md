# Chat Multiplatform

基于 **Kotlin Multiplatform + Compose Multiplatform + Ktor** 的全栈即时通讯与文章写作平台。一套 Kotlin 代码同时编译到 Android、Desktop（JVM）、Web（JS/Wasm）客户端；后端使用 Ktor + PostgreSQL，通过 WebSocket 实现实时消息推送；文章协作编辑基于 **Yjs + Hocuspocus**（`collab-server`），富文本编辑器 **Tiptap** 以 React 组件库（UMD）供 Web 前端直接使用、以自包含 HTML 供 Desktop/Android 原生 WebView 加载。

## 仓库数据

[![commits](https://img.shields.io/github/commits/m/woods-marshes/chat-multiplatform?style=flat-square&label=commits&color=blue)](https://github.com/woods-marshes/chat-multiplatform/commits)
[![last commit](https://img.shields.io/github/last-commit/woods-marshes/chat-multiplatform?style=flat-square&label=last%20commit&color=blue)](https://github.com/woods-marshes/chat-multiplatform/commits)
[![commit activity](https://img.shields.io/github/commit-activity/m/woods-marshes/chat-multiplatform?style=flat-square&label=activity&color=blue)](https://github.com/woods-marshes/chat-multiplatform/graphs/commit-activity)
[![top language](https://img.shields.io/github/languages/top/woods-marshes/chat-multiplatform?style=flat-square&color=blue)](https://github.com/woods-marshes/chat-multiplatform)
[![contributors](https://img.shields.io/github/contributors/woods-marshes/chat-multiplatform?style=flat-square&label=contributors&color=blue)](https://github.com/woods-marshes/chat-multiplatform/graphs/contributors)
[![repo size](https://img.shields.io/github/repo-size/woods-marshes/chat-multiplatform?style=flat-square&label=size&color=blue)](https://github.com/woods-marshes/chat-multiplatform)
[![CI](https://github.com/woods-marshes/chat-multiplatform/actions/workflows/ci.yml/badge.svg)](https://github.com/woods-marshes/chat-multiplatform/actions/workflows/ci.yml)
[![Docker Test](https://github.com/woods-marshes/chat-multiplatform/actions/workflows/docker-test.yml/badge.svg)](https://github.com/woods-marshes/chat-multiplatform/actions/workflows/docker-test.yml)

| 指标 | 数值 | 采集方式 |
|---|---|---|
| 提交总数 | 89 | `git rev-list --count HEAD` |
| 贡献者 | 2 | `git shortlog -sne --all` |
| 首次提交 | 2026-06-12 | `git log --reverse` |
| 最近提交 | 2026-09-24 | `git log -1` |
| 已跟踪文件 | 815 | `git ls-files` |
| 代码行数 | 54,018 | 快照，见下方口径 |

### 代码构成

统计口径：2026-09-24 用 `rg` 统计已跟踪文件的物理行数，剔除 `tiptap-bridge/repomix-output.xml` 等生成物；分布条为近似值（每 █ 约 5%）。

| 语言 | 行数 | 占比 | 分布 |
|---|---:|---:|---|
| Kotlin | 34,382 | 63.6% | █████████████ |
| TSX | 5,241 | 9.7% | ██ |
| TypeScript | 4,901 | 9.1% | ██ |
| SCSS | 3,391 | 6.3% | █ |
| YAML | 2,003 | 3.7% | █ |
| CSS | 1,322 | 2.4% | █ |
| SQL | 1,009 | 1.9% | █ |
| JavaScript | 504 | 0.9% | █ |
| HTML | 434 | 0.8% | █ |
| JSX | 431 | 0.8% | █ |
| XML | 400 | 0.7% | █ |

## 架构概览

```
┌──────────────────────────────────────────────────────────┐
│    androidApp   │  desktopApp   │  webApp (JS/WasmJS)    │
├──────────────────────────────────────────────────────────┤
│                       composeApp                         │
│    共享 Compose UI 装配 + Koin 初始化（KMP 库）           │
├──────────────────────────────────────────────────────────┤
│  features/*  (article, article-editor, auth, chat,       │
│               contacts, conversations, profile,          │
│               search, settings)                          │
├──────────────────────────────────────────────────────────┤
│  core/*                                                  │
│  common / model / data / network / database / datastore  │
│  / domain / ui / navigation                              │
├──────────────────────────┬───────────────────────────────┤
│  web (Kotlin/JS + React) │  tiptap-bridge (React/Vite)   │
│  浏览器端聊天 + 文章        │  UMD 供 web 调用               │
└──────────────────────────┘  自包含 HTML 供原生 WebView 加载 │
├──────────────────────────┬───────────────────────────────┤
│  server (Ktor + Netty)   │  collab-server (Node.js)      │
│  REST API + WebSocket    │  Yjs / Hocuspocus 协作后端     │
│  Exposed ORM + PG / H2   │  JWT 回调 Ktor 校验            │
└──────────────────────────┴───────────────────────────────┘
```

- **共享客户端模块** (`composeApp`) 依赖 `core/*` 和 `features/*`，负责 Compose UI 装配与 Koin 初始化；平台入口模块 `androidApp` / `desktopApp` / `webApp` 只保留启动代码（`main()` / `MainActivity`），依赖 `composeApp`
- **服务端** (`server`) 依赖 `core:model` 与 `core:network`（共享路由定义、DTO 与错误模型），可独立部署
- **Web 前端** (`web`) 基于 Kotlin/JS + React，通过 Koin 注入 `core/*` 公共模块
- **Tiptap 桥接** (`tiptap-bridge`) React 组件库：UMD 打包供 `web` 调用；自包含 HTML（editor/viewer）由 Compose 客户端通过 ComposeNativeWebView 加载
- **协作服务** (`collab-server`) 独立 Node.js 服务，管理 Yjs 文档持久化（PostgreSQL `yjs_documents` 表），鉴权通过回调 `server` 的 `/v1/auth/verify` 完成

## 技术栈

**语言与构建**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.20-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Gradle](https://img.shields.io/badge/Gradle-9.7.1-02303A?logo=gradle&logoColor=white)](https://gradle.org)
[![AGP](https://img.shields.io/badge/AGP-9.2.1-3DDC84?logo=android&logoColor=white)](https://developer.android.com/build)
[![JDK](https://img.shields.io/badge/JDK-25-ED8B00)](https://adoptium.net)
[![Node.js](https://img.shields.io/badge/Node.js-24-5FA04E?logo=nodedotjs&logoColor=white)](https://nodejs.org)

**客户端**

[![Compose Multiplatform](https://img.shields.io/badge/Compose_Multiplatform-1.12.0-4285F4)](https://www.jetbrains.com/compose-multiplatform/)
[![Material 3](https://img.shields.io/badge/Material_3-1.12.0-alpha03-6750A4)](https://m3.material.io)
[![Material 3 Adaptive](https://img.shields.io/badge/Material_3_Adaptive-1.3.0-rc01-6750A4)](https://developer.android.com/develop/ui/compose/layouts/adaptive)
[![Navigation 3](https://img.shields.io/badge/Navigation_3-1.2.0-beta01-4285F4)](https://developer.android.com/jetpack/androidx/releases/navigation)
[![Koin](https://img.shields.io/badge/Koin-4.2.2-20A4F3)](https://insert-koin.io/docs/reference/koin-compose/)
[![SQLDelight](https://img.shields.io/badge/SQLDelight-2.4.0-2E7D32)](https://sqldelight.github.io/sqldelight/)
[![Room](https://img.shields.io/badge/Room-3.0.3-4285F4)](https://developer.android.com/kotlin/multiplatform/room)
[![DataStore](https://img.shields.io/badge/DataStore-1.3.0-alpha11-4285F4)](https://developer.android.com/jetpack/datastore)
[![Paging](https://img.shields.io/badge/Paging-3.5.1-4285F4)](https://developer.android.com/topic/libraries/architecture/paging/v3-overview)
[![ComposeNativeWebView](https://img.shields.io/badge/ComposeNativeWebView-1.0.3-7C3AED)](https://github.com/NucleusFramework/ComposeNativeWebview)
[![Nucleus](https://img.shields.io/badge/Nucleus-2.5.16-7C3AED)](https://nucleusframework.dev)
[![Coil](https://img.shields.io/badge/Coil-3.6.3-7C3AED)](https://coil-kt.github.io/coil/compose/)
[![Miuix](https://img.shields.io/badge/Miuix-0.9.3-7C3AED)](https://github.com/YuKongA/miuix)
[![Lyricist](https://img.shields.io/badge/Lyricist-1.8.0-7C3AED)](https://github.com/skaldebane/lyricist)

**服务端**

[![Ktor](https://img.shields.io/badge/Ktor-3.6.0-087CFA?logo=ktor&logoColor=white)](https://ktor.io)
[![Exposed](https://img.shields.io/badge/Exposed-1.5.0-087CFA)](https://github.com/JetBrains/Exposed)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org)
[![H2](https://img.shields.io/badge/H2-2.5.250-09476B?logo=h2database&logoColor=white)](https://www.h2database.com)
[![kotlinx.serialization](https://img.shields.io/badge/kotlinx.serialization-1.11.0-087CFA)](https://github.com/Kotlin/kotlinx.serialization)
[![jave2](https://img.shields.io/badge/jave2-4.2.0-087CFA)](https://github.com/psibre/jave2)
[![Docker Compose](https://img.shields.io/badge/Docker_Compose-2496ED?logo=docker&logoColor=white)](https://docs.docker.com/compose/)

**Web / 协作编辑 / 工程**

[![React](https://img.shields.io/badge/React-19.2.6-61DAFB?logo=react&logoColor=black)](https://react.dev)
[![Vite](https://img.shields.io/badge/Vite-8.0.16-9135FF?logo=vite&logoColor=white)](https://vite.dev)
[![Tiptap](https://img.shields.io/badge/Tiptap-3.27.1-9135FF)](https://tiptap.dev)
[![Tailwind CSS](https://img.shields.io/badge/Tailwind_CSS-4.3.1-06B6D4?logo=tailwindcss&logoColor=white)](https://tailwindcss.com)
[![Yjs](https://img.shields.io/badge/Yjs-13.6.31-9135FF)](https://docs.yjs.dev)
[![Hocuspocus](https://img.shields.io/badge/Hocuspocus-4.3.0-9135FF)](https://tiptap.dev/hocuspocus)
[![ESLint](https://img.shields.io/badge/ESLint-10.5.0-4B32C3)](https://eslint.org)
[![GitHub Actions](https://img.shields.io/badge/GitHub_Actions-2088FF?logo=githubactions&logoColor=white)](https://github.com/features/actions)
[![Dependabot](https://img.shields.io/badge/Dependabot-enabled-2088FF?logo=dependabot&logoColor=white)](https://docs.github.com/code-security/dependabot)

| 层 | 技术 |
|---|---|
| **语言/构建** | Kotlin 2.4.20 + Gradle 9.7.1（Version Catalog + 约定插件 `build-logic`） |
| **UI 框架** | Compose Multiplatform 1.12.0 + Material 3 Adaptive 1.3.0 |
| **导航** | Jetpack Navigation 3 1.2.0-beta01（Scene + NavEntry，自适应 ListDetail 布局） |
| **DI** | Koin 4.2.2（ViewModel → `viewModelOf`，Repository → `single`） |
| **网络** | Ktor 3.6.0（Client + Server），REST API（Ktor Resources）+ WebSocket |
| **序列化** | kotlinx-serialization 1.11.0（JSON + Protobuf 双格式，共享 DTO） |
| **客户端数据库** | SQLDelight 2.4.0（7 表，含离线优先文章缓存 + UUIDv7 游标分页） |
| **服务端数据库** | Exposed 1.5.0 + PostgreSQL 17（生产）/ H2 2.5.250（开发默认） |
| **实时协作** | Yjs 13.6 + @hocuspocus/server 4.3（collab-server） |
| **富文本** | Tiptap 3.27.1（React UMD + 自包含 HTML 双产物） |
| **图片/媒体** | Coil 3.6.3、jave2 4.2.0（音视频处理/波形）、BlurHash |
| **国际化** | Lyricist 1.8.0（KSP 从 `strings.xml` 生成） |
| **认证** | JWT（HMAC256，Bearer）+ BCrypt 密码散列 |
| **WebView** | ComposeNativeWebView 1.0.3（`dev.nucleusframework:composewebview`，桌面端由 Nucleus Tao 托管） |

## 主要功能

- **用户认证** — 注册/登录，JWT Token 认证，本地 DataStore 持久化
- **即时通讯** — 单聊/群聊，WebSocket 实时收发、输入状态、撤回、回复引用
- **会话管理** — 会话列表、创建/加入群组、置顶、免打扰
- **联系人** — 好友请求/删除、A-Z 索引栏、在线状态
- **搜索** — 联系人/会话搜索（防抖）
- **个人设置** — 头像、资料、主题（明暗/品牌）、通知偏好、退出登录
- **自适应布局** — 手机/平板/桌面窗口尺寸自动切换 ListDetail / 导航栏形态
- **文章写作** ✨ — Tiptap 所见即所得编辑器；Compose 端经 WebView 嵌入，Base64 JSON 桥通信；多人实时协作（Yjs）
- **文章浏览** ✨ — 列表（UUIDv7 游标分页 + 全部/我的 Tab）、详情渲染（WebView 静态渲染）、文章卡片组件
- **文章管理** ✨ — 新建/编辑、存草稿/发布、软删除、离线优先缓存（RemoteMediator 回源 + 本地 SQLDelight）

## 项目结构

```
chat-multiplatform/
├── composeApp/          # 共享客户端模块（Compose UI 装配 + Koin 初始化，KMP 库）
├── androidApp/          # Android 入口（MainActivity / Application）
├── desktopApp/          # Desktop 入口（JVM，compose.desktop 原生打包）
├── webApp/              # Compose Web 入口（JS + WasmJS）
├── server/              # Ktor 服务端（REST API + WebSocket + 文件上传）
├── web/                 # Kotlin/JS + React 浏览器端
├── tiptap-bridge/       # React 组件库（UMD + 自包含 HTML WebView 壳）
├── collab-server/       # Node.js 协作服务（Yjs/Hocuspocus，非 Gradle 模块）
│
├── core/
│   ├── common/          # AppDispatchers、Koin 公共模块、PlatformContext、UiState
│   ├── model/           # 领域模型、DomainError 密封类、UI 模型（客户端+服务端共享）
│   ├── data/            # Repository 接口与实现（离线优先 + RemoteMediator）
│   ├── network/         # Ktor HttpClient、REST API、WebSocket、DTO、路由定义(V1)
│   ├── database/        # SQLDelight 数据库（7 表）+ DAO + QueryPagingSource
│   ├── database-room/   # Room 3.0 KMP 数据库（迁移中的替代方案）
│   ├── datastore/       # DataStore 键值存储（Token / 用户设置）
│   ├── domain/          # 预留：业务逻辑 UseCase
│   ├── ui/              # Compose 共享组件、主题、聊天气泡、i18n
│   └── navigation/      # Navigation 3 路由状态
│
├── features/
│   ├── article/         # 文章浏览（列表 + 详情 WebView + 分页）
│   ├── article-editor/  # 文章编辑器（Tiptap WebView + 协作连接）
│   ├── auth/            # 登录 & 注册
│   ├── chat/            # 聊天界面
│   ├── contacts/        # 联系人列表
│   ├── conversations/   # 会话列表
│   ├── profile/         # 用户资料
│   ├── search/          # 搜索
│   └── settings/        # 应用设置
│
├── build-logic/         # Gradle 约定插件（KMP/Android/Compose 统一配置）
├── gradle/              # Version Catalog（libs.versions.toml）
├── notes/               # 开发文档（CLAUDE.md 引用）
├── compose.yml          # Docker Compose（server + postgres + collab-server）
└── Dockerfile           # 服务端多阶段构建（含 ffmpeg）
```

## 快速开始

### 环境要求

- **JDK 25**（server 与 Desktop 目标为 JVM 25 字节码；Gradle 工具链可自动下载，见 foojay resolver 配置）
- **Android Studio** / Android SDK — 如需编译 Android 目标（minSdk 24 / targetSdk 37）
- **Node.js ≥ 20** — `tiptap-bridge` 构建需要 npm
- **Docker** — 如需容器化部署
- 不修改任何代码即可运行：客户端默认连接 `http://127.0.0.1:9051`，服务端默认使用 H2 内存数据库

### 1. 配置客户端 API 地址（可选）

```bash
cp network-config.properties.template network-config.properties
# host=127.0.0.1 / port=9051 / useTls=false
```

该文件经 BuildKonfig 注入 `core:network`，已 gitignore；不创建时使用内置默认值。

### 2. 运行服务端

```bash
./gradlew :server:run          # 默认 H2 内存库，监听 http://127.0.0.1:9051
```

连接 PostgreSQL：

```bash
DATABASE_TYPE=postgres \
POSTGRES_URL=jdbc:postgresql://localhost:5432/chat_db \
POSTGRES_USERNAME=postgres \
POSTGRES_PASSWORD=<your-password> \
JWT_SECRET=<your-secret> \
./gradlew :server:run
```

> ⚠️ 生产部署务必通过环境变量覆盖 `JWT_SECRET` 与数据库口令（`application.yaml` 中的默认值仅供本地开发）。

### 3. 运行客户端

```bash
./gradlew :desktopApp:run                  # Desktop 客户端
./gradlew :androidApp:installDebug -PenableAndroid=true # Android（需模拟器/真机）
./gradlew :webApp:jsBrowserDevelopmentRun  # Compose Web 客户端（JS，开发模式）
./gradlew :webApp:wasmJsBrowserDevelopmentRun  # Compose Web 客户端（Wasm，开发模式）
./gradlew :web:jsBrowserDevelopmentRun     # Web 前端 React 版（开发模式）
./gradlew :web:jsBrowserDistribution       # Web 前端 React 版（生产构建）
```

### 4. 构建 Tiptap 桥接产物（如修改了编辑器源码）

```bash
cd tiptap-bridge
npm ci
npm run build              # UMD → 自动拷贝进 web/src/jsMain/resources
npm run build:webview:editor
npm run build:webview:viewer  # 自包含 HTML → 拷贝进 features/* 的 composeResources
```

> `web` 与 `features/article`、`features/article-editor` 的 Gradle 任务已挂接这些 npm 构建，直接执行对应 Gradle 任务亦可。**未修改 tiptap-bridge 时可跳过此步**（产物已提交到仓库）。

## IDEA 已知问题：Android/KMP 导入空库

在本项目的 IDEA 2026.2.3 环境中，混合导入 Android/KMP 与 JVM 模型会出现同名库合并时保留空 Classes 根的现象：Gradle 编译成功，编辑器仍无法解析 Ktor 等依赖。相关问题：KTIJ-39840；仅排除 Android Application 入口在本项目中不足以规避。

仓库默认启用全部 Android targets。受影响的开发者可复制 `local-build.properties.example` 为 `local-build.properties`，设置：

```properties
enableAndroid=false
```

本地文件已被 Git 和 Docker 忽略。保持 IDE 插件启用，执行 Gradle Reload。关闭模式不应用共享模块的 AGP KMP 插件，不创建 Android targets，也不包含 `:androidApp`；JVM、JS、Wasm 保留。Android 源码不会被删除，但当前 IDE 工程不能编辑解析或运行完整 Android 功能。

```bash
./gradlew :desktopApp:compileKotlin -PenableAndroid=false
./gradlew :androidApp:assembleDebug -PenableAndroid=true
./gradlew :androidApp:installDebug -PenableAndroid=true
./gradlew check -PenableAndroid=true
```

优先级：Gradle property（如 `-PenableAndroid=true`）→ 本地配置 → 默认 `true`。值仅接受 `true`/`false`。旧的 `includeAndroidApp` 仅控制应用入口且默认 `true`；若曾将其设置为 `false`，构建 Android 应用时还需删除该旧配置或显式传 `-PincludeAndroidApp=true`。`enableAndroid=false` 始终排除应用入口。

CI 对 Desktop 验证 Android 开启/关闭两种模式，并在开启模式构建 Android Debug APK；server job 验证完整模式的服务端和协议测试。Docker 服务端构建显式使用 `-PenableAndroid=false`。

此开关是本地 IDE 兼容性规避，不是上游缺陷修复，不修改 Ktor/Coil 版本。恢复 Android targets 后，当前受影响的 IDE 可能再次出现空库。Android 开发需使用经验证兼容的 IDE/插件组合；命令行编译通过不能代替 IDE 导入验证。

## 使用示例

### REST API（curl）

```bash
# 注册
curl -s -X POST http://127.0.0.1:9051/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","email":"alice@example.com","password":"Passw0rd!"}'

# 登录（返回 user + accessToken）
TOKEN=$(curl -s -X POST http://127.0.0.1:9051/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"Passw0rd!"}' \
  | jq -r '.accessToken')

# 查询当前用户
curl -s http://127.0.0.1:9051/v1/users/me -H "Authorization: Bearer $TOKEN"

# 发布文章（Tiptap JSON 正文，返回含 id）
ARTICLE_ID=$(curl -s -X POST http://127.0.0.1:9051/v1/articles \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"title":"Hello","content":{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"Hi"}]}]},"status":"PUBLISHED"}' \
  | jq -r '.id')

# 文章列表（UUIDv7 游标分页）
curl -s "http://127.0.0.1:9051/v1/articles?limit=20"          # 首页
curl -s "http://127.0.0.1:9051/v1/articles?beforeId=$ARTICLE_ID"  # 上一页
```

完整路由定义见 `core/network-api/src/commonMain/.../api/V1.kt`（客户端与服务端共享同一路由声明），Swagger UI 在开发模式下位于 `http://127.0.0.1:9051/openapi`。

### WebSocket 实时消息

客户端通过 `RealtimeApi`（`core:network`）连接 `ws://<host>:9051/realtime?access_token=<JWT>`，订阅 `RealtimeEvent` 共享流；服务端事件（新消息、已读、联系人请求、会话变更）经 EventBus 广播到同会话在线成员。Protobuf 为 WS 线格式，JSON 为 REST 线格式，二者共享 DTO。

### 多人协作编辑

1. 启动 `server`（REST + JWT）与 `collab-server`（`PORT=1234`，需 `DATABASE_URL` 指向同一 PostgreSQL、`KTOR_AUTH_URL=http://<server>:9051/v1/auth/verify`）
2. 任一端打开文章编辑器：Compose 端在编辑器 WebView 内连接 `ws://<collab>:1234`；Web 端经 `TiptapEditorBridge` 连接
3. 多人同时编辑时，Yjs 增量更新实时同步，2 秒防抖持久化到 `yjs_documents` 表

### 运行测试

```bash
./gradlew :server:test            # 服务端：启动流程 + Hashing/JWT/会话索引/Excerpt/错误映射
./gradlew :core:common:jvmTest    # UiState 结果映射
./gradlew :core:network-api:jvmTest # JSON/Protobuf 双格式多态序列化往返
./gradlew :core:data:jvmTest      # DTO/领域/DB 实体映射器（含缺失发送者等错误场景）
./gradlew check                   # 全部
```

## Docker 部署

`compose.yml` 编排三个服务：`server`（9051，uploads 卷）、`db`（postgres:17-alpine，含健康检查）、`collab-server`（1234，Yjs 持久化 + 回调 server 鉴权）。`.env` 内置 demo 默认值，可直接启动；**生产部署前必须修改 `POSTGRES_PASSWORD` 与 `JWT_SECRET`**。

```bash
docker compose up -d
docker compose logs -f server
```

服务端口：`9051`（API + Web SPA）、`1234`（协作服务）。服务端镜像含 ffmpeg，支持音视频处理与波形生成；健康检查走 `GET /`。

## 常用命令

| 目的 | 命令 |
|---|---|
| 编译检查（客户端） | `./gradlew :desktopApp:compileKotlin` |
| 全部测试 | `./gradlew check` |
| 服务端测试 | `./gradlew :server:test` |
| Compose Web 开发运行 | `./gradlew :webApp:jsBrowserDevelopmentRun` |
| Web 开发运行 | `./gradlew :web:jsBrowserDevelopmentRun` |
| Web 生产构建 | `./gradlew :web:jsBrowserDistribution` |
| 服务端 fat jar | `./gradlew :server:buildFatJar` |
| Tiptap WebView 构建 | `cd tiptap-bridge && npm run build:webview:editor && npm run build:webview:viewer` |
| 列出所有任务 | `./gradlew tasks` |

> **注意**：配置缓存已启用，遇到异常行为时加 `--no-configuration-cache`。

## 开发约定

- **Git Commit**：`<scope>: <summary>`，scope 可选 `client`/`server`/`web`/`core`/`features`/`build`/`deps`/`tiptap`，英文、≤72 字符
- **代码风格**：Kotlin Official Style，4 空格缩进，显式 import，标识符与注释使用英文
- **JVM 目标**：composeApp/Android → 17，desktopApp → 25，server → 25
- **JS 目标**：web → ES2015
- **依赖管理**：`gradle/libs.versions.toml` 为唯一版本来源
- **DI**：Koin 注册顺序 `common → datastore → serializers → daos → network → data → domain → feature ViewModels`

详见 `CLAUDE.md` 与 `notes/` 下的详细文档。

## License

MIT
