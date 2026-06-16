# Chat Multiplatform

基于 **Kotlin Multiplatform + Compose Multiplatform + Ktor** 的全栈应用。一套 Kotlin 代码同时编译到 Android、Desktop（JVM）、Web（Wasm/JS）客户端，后端使用 Ktor + PostgreSQL，通过 WebSocket 实现实时消息推送。同时内置**文章写作平台**，使用 Tiptap 富文本编辑器，Desktop/Android 端通过 ComposeNativeWebView 原生 WebView 渲染。

## 架构概览

```
┌──────────────────────────────────────────────────────┐
│                    composeApp                        │
│  Android  │  Desktop (JVM)  │  Web (Wasm/JS)         │
├──────────────────────────────────────────────────────┤
│  features/*  (article, article-editor, auth, chat,   │
│               contacts, conversations, profile,      │
│               search, settings)                      │
├──────────────────────────────────────────────────────┤
│  core/*                                              │
│  UI / Data / Model / Network / Database / Datastore  │
├──────────────────────────────────────────────────────┤
│  web (Kotlin/JS + React + Tiptap)                    │
├──────────────────────────┬───────────────────────────┤
│  tiptap-bridge (React)   │  server (Ktor + Netty)    │
│  UMD + 自包含 HTML        │  REST API + WebSocket     │
│                          │  Exposed ORM + PG / H2    │
└──────────────────────────┴───────────────────────────┘
```

- **客户端** (`composeApp`) 依赖 `core/*` 和 `features/*`，按需引入平台特定实现
- **服务端** (`server`) 依赖 `core:model` 和 `core:network`，可独立部署
- **Web 前端** (`web`) 基于 Kotlin/JS + React + Tiptap，通过 Koin 注入 `core/*` 公共模块
- **Tiptap 桥接** (`tiptap-bridge`) React 组件库：UMD 打包供 web 调用 + 自包含 HTML 供 ComposeNativeWebView 加载

## 技术栈

| 层 | 技术 |
|---|---|
| **UI 框架** | Compose Multiplatform 1.12 + Material 3 Adaptive |
| **Web 前端** | Kotlin/JS (ES2015) + React + Tiptap 3.x |
| **主题** | Miuix (MIUI 风格) |
| **导航** | Jetpack Navigation 3（Scene + NavEntry，支持自适应布局） |
| **DI** | Koin 4.2（ViewModel → `viewModelOf`，Repository → `single`） |
| **网络** | Ktor 3.5（Client + Server），REST API + WebSocket |
| **序列化** | kotlinx-serialization (JSON + Protobuf) |
| **数据库（客户端）** | SQLDelight 2.3（7 表，跨平台本地存储） |
| **数据库（服务端）** | Exposed ORM + PostgreSQL（生产）/ H2（开发） |
| **图片加载** | Coil 3.5 |
| **国际化** | Lyricist |
| **构建** | Gradle 9.5.1 + Kotlin 2.3.21 + Version Catalog |
| **WebView（多平台）** | ComposeNativeWebView 1.0.0-beta-02（Desktop/Android 原生 WebView） |

## 功能特性

- **用户认证** — 注册/登录，JWT Token 认证
- **即时通讯** — 多人聊天，WebSocket 实时推送
- **会话管理** — 会话列表、置顶、已读状态
- **联系人** — 好友管理，在线状态
- **搜索** — 消息/联系人搜索
- **个人设置** — 头像、昵称、密码修改
- **自适应布局** — 手机/平板/桌面不同窗口尺寸自动适配 ListDetail 模式
- **文章写作** ✨ — Tiptap 富文本编辑器，所见即所得，Compose 端通过 ComposeNativeWebView 嵌入
- **文章浏览** ✨ — 文章列表（分页 + 全部/我的 tab）、详情查看（WebView 静态渲染）、文章卡片组件
- **文章管理** ✨ — 新建/编辑（自动保存 Base64 传输）、草稿/发布、软删除、离线优先缓存

## 项目结构

```
chat-multiplatform/
├── composeApp/          # 跨平台客户端入口（Android / Desktop / Web）
├── androidApp/          # Android 壳工程
├── server/              # Ktor 服务端（REST API + WebSocket）
├── web/                 # Kotlin/JS + React + Tiptap 前端
├── tiptap-bridge/       # React 组件库（UMD + 自包含 HTML WebView 壳）
│
├── core/
│   ├── common/          # 公共工具（Dispatchers, Koin 模块, PlatformContext）
│   ├── model/           # 领域模型、Error 密封类、UiState、Article、ArticleListUiModel
│   ├── data/            # Repository 接口与实现（离线优先 ArticleRepository + RemoteMediator）
│   ├── network/         # Ktor HttpClient / REST API (ArticleApi, UUIDv7 游标分页) / DTO / WebSocket
│   ├── database/        # SQLDelight 数据库（7 表，含 Article JOIN User）+ DAO + QueryPagingSource
│   ├── database-room/   # Room 3.0 KMP 数据库（替代方案）
│   ├── datastore/       # Key-Value 存储（Token / 偏好设置）
│   ├── domain/          # 预留：未来业务逻辑 UseCase
│   ├── ui/              # Compose Multiplatform 共享组件 & 主题 & ArticleCardItem
│   └── navigation/      # Navigation 3 路由定义
│
├── features/
│   ├── article/         # 文章浏览（列表 + 详情 WebView + 分页 + 全部/我的 tab）
│   ├── article-editor/  # 文章编辑器（Tiptap WebView + Base64 传输 + 存草稿/发布）
│   ├── auth/            # 登录 & 注册
│   ├── chat/            # 聊天界面
│   ├── contacts/        # 联系人列表
│   ├── conversations/   # 会话列表
│   ├── profile/         # 用户资料
│   ├── search/          # 搜索
│   └── settings/        # 应用设置
│
├── build-logic/         # Gradle 构建逻辑（自定义插件 & 约定）
├── gradle/              # Gradle Wrapper & Version Catalog
├── notes/               # 详细开发文档（CLAUDE.md 引用）
├── compose.yml          # Docker Compose（Server + PostgreSQL）
└── Dockerfile           # 服务端多阶段构建
```

## 快速开始

### 环境要求

- **JDK** 21（服务端）/ JDK 17+（Android）
- **Android Studio** (推荐最新 Canary) — 如需编译 Android 目标
- **Docker Desktop** — 如需容器化部署
- **Node.js** — tiptap-bridge 构建需要 npm

### 1. 配置网络

```bash
cp network-config.properties.template network-config.properties
# 编辑 network-config.properties 按需修改 API 地址
```

默认 API 基址：`http://127.0.0.1:9051/v1/`

### 2. 运行服务端

```bash
# 使用 H2 内存数据库（开发模式）
./gradlew :server:run

# 服务启动在 http://127.0.0.1:9051
```

### 3. 运行客户端

```bash
# Desktop 客户端
./gradlew :composeApp:run

# Web 前端（开发模式）
./gradlew :web:jsBrowserDevelopmentRun

# Web 前端（生产构建）
./gradlew :web:jsBrowserDistribution

# Android 客户端（需要模拟器或真机）
./gradlew :androidApp:installDebug
```

### 常用命令

| 目的 | 命令 |
|---|---|
| 编译检查 | `./gradlew :composeApp:jvmMainClasses` |
| 全部测试 | `./gradlew check` |
| 服务端测试 | `./gradlew :server:test` |
| Web 开发运行 | `./gradlew :web:jsBrowserDevelopmentRun` |
| Web 生产构建 | `./gradlew :web:jsBrowserDistribution` |
| Tiptap WebView 构建 | `cd tiptap-bridge && npm run build:webview` |
| 列出所有任务 | `./gradlew tasks` |

> **注意**：配置缓存已启用，遇到异常行为时加 `--no-configuration-cache`。

## Docker 部署

`.env` 已内置 demo 默认值，可直接启动。生产部署前编辑 `.env` 修改密码和密钥即可。

```bash
docker compose up -d
```

服务端口：`9051`。

## 开发约定

- **Git Commit**：`<scope>: <summary>`，scope 可选 `client`/`server`/`web`/`core`/`features`/`build`/`deps`/`tiptap`
- **代码风格**：Kotlin Official Style，4 空格缩进，显式 import
- **JVM 目标**：composeApp/Android → 17，composeApp/Desktop → 25，server → 25
- **JS 目标**：web → ES2015
- **依赖管理**：`gradle/libs.versions.toml` 为唯一版本来源

详见 `CLAUDE.md` 和 `notes/` 下的详细文档。

## License

MIT
