# chat-multiplatform

> 同一套 Kotlin 代码，同时跑在 Android、桌面和浏览器上的全栈聊天 + 协作写作应用。

[![CI](https://github.com/woods-marshes/chat-multiplatform/actions/workflows/ci.yml/badge.svg)](https://github.com/woods-marshes/chat-multiplatform/actions/workflows/ci.yml)
[![Docker Test](https://github.com/woods-marshes/chat-multiplatform/actions/workflows/docker-test.yml/badge.svg)](https://github.com/woods-marshes/chat-multiplatform/actions/workflows/docker-test.yml)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.20-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![License](https://img.shields.io/github/license/woods-marshes/chat-multiplatform)](./LICENSE)

一个仓库里包含**三个客户端**（Compose Android / Compose Desktop / Compose Web JS+Wasm）、**一个 Kotlin/JS + React 网页版**、**Ktor 服务端**和**Node.js 协作编辑服务**。即时通讯走 WebSocket；文章功能把 Tiptap 富文本编辑器同时交付给网页版（React UMD）和原生客户端（WebView 自包含 HTML），并用 Yjs + Hocuspocus 支持多人实时协作编辑。

想快速判断能不能跑：**改不改一行代码就能启动** —— 服务端默认用 H2 内存库，客户端默认连 `127.0.0.1:9051`。

<!-- TODO: 放一张桌面端聊天 + 一张文章协作编辑的截图或 GIF（约 5-10 秒）。这是目前 README 最缺的东西。 -->

## 快速开始

需要 JDK 25（Gradle 工具链可自动下载）。客户端和服务端都可以零配置启动：

```bash
# 1. 启动服务端（H2 内存库，监听 http://127.0.0.1:9051）
./gradlew :server:run

# 2. 另开一个终端启动桌面客户端
./gradlew :desktopApp:run
```

其他运行方式：

```bash
./gradlew :androidApp:installDebug -PenableAndroid=true   # Android（需模拟器/真机）
./gradlew :webApp:jsBrowserDevelopmentRun                 # Compose Web（JS）
./gradlew :web:jsBrowserDevelopmentRun                   # 网页版（Kotlin/JS + React）
docker compose up -d                                     # 服务端 + PostgreSQL + 协作服务
```

服务端开发模式提供 Swagger UI：`http://127.0.0.1:9051/openapi`。

## 功能

**聊天**
- 单聊 / 群聊，WebSocket 实时收发，输入状态、撤回、回复引用
- 会话列表、创建与加入群组、置顶、免打扰
- 联系人（好友请求、A-Z 索引、在线状态）、会话与联系人搜索
- 离线优先：消息先落本地库（SQLDelight），断网可继续写，恢复后由 outbox 自动重发

**文章**
- Tiptap 所见即所得编辑器，草稿 / 发布 / 软删除
- 列表用 UUIDv7 游标分页 + RemoteMediator 离线缓存
- 多人实时协作：Yjs 增量同步，服务端 2 秒防抖持久化
- 同一份编辑器跑在 WebView（Android / Desktop）和 Kotlin/JS 网页版上

**通用**
- 自适应布局：手机 / 平板 / 桌面窗口自动切换单栏与 ListDetail
- 主题（明暗 + 品牌色）、Lyricist 国际化、离线登录态持久化
- JWT 认证 + BCrypt 密码散列，WebSocket 走 query token（浏览器 API 限制）

## 架构

```
androidApp / desktopApp / webApp   ← 平台入口，只写启动代码
            │
        composeApp                  ← 共享 UI 装配 + Koin 初始化
            │
     features/*  +  core/*         ← 业务模块 / 基础设施（见下表）
            │
   ┌────────┴─────────┬──────────────────┐
 web (Kotlin/JS)   tiptap-bridge     server (Ktor)
 React 前端          React 组件库      REST + WebSocket
                    ├ UMD → web      Exposed + PostgreSQL/H2
                    └ HTML → WebView
                          │
                   collab-server (Node.js)
                   Yjs / Hocuspocus 持久化
```

| 模块 | 内容 |
|---|---|
| `core/model` | 领域模型、`DomainError` 密封类、UI 模型（客户端与服务端共享） |
| `core/network` `core/network-api` | Ktor Client、WebSocket、DTO、Ktor Resources 路由定义（两端共用同一份声明） |
| `core/data` | Repository 接口与实现，离线优先编排、RemoteMediator、outbox 重发 |
| `core/database` | SQLDelight（7 表）、DAO、`QueryPagingSource` 游标分页 |
| `core/ui` `core/navigation` | Compose 共享组件、Material 3 Adaptive、Navigation 3 路由 |
| `features/*` | `auth` `chat` `contacts` `conversations` `article` `article-editor` `profile` `search` `settings` |
| `web` | Kotlin/JS + React 网页版，通过 Koin 复用 `core/*` |
| `tiptap-bridge` | Tiptap React 组件库，双产物：UMD（网页版）+ 自包含 HTML（原生 WebView） |
| `server` | Ktor + Netty，REST API、WebSocket、文件上传、OpenAPI |
| `collab-server` | Node.js + Hocuspocus，Yjs 文档持久化，鉴权回调 `server` 的 `/v1/auth/verify` |

一个值得注意的设计：REST 用 JSON、WebSocket 用 Protobuf，**但共用同一套 DTO**（`core/model` 里带 `@ProtoNumber`），所以新增字段只需要改一处。

## 技术栈

| 领域 | 选型 |
|---|---|
| 语言 / 构建 | Kotlin 2.4.20、Gradle 9.7.1（Version Catalog + `build-logic` 约定插件） |
| UI | Compose Multiplatform 1.12.0、Material 3 Adaptive、Miuix 主题、Navigation 3 |
| 依赖注入 | Koin 4.2.2 |
| 网络 | Ktor 3.6.0（Client + Server）、Ktor Resources、WebSocket + kotlinx.serialization Protobuf |
| 客户端存储 | SQLDelight 2.4.0、DataStore、Paging 3.5.1 |
| 服务端存储 | Exposed 1.5.0 + PostgreSQL 17（生产）/ H2（开发默认） |
| 协作编辑 | Tiptap 3.27.1、Yjs 13.6、Hocuspocus 4.3 |
| Web | Kotlin/JS、React 19、Vite 8、Tailwind CSS 4 |
| 工程 | GitHub Actions（客户端编译矩阵 + 服务端测试 + Docker 构建）、Dependabot |

版本号不在 README 里维护，以 `gradle/libs.versions.toml` 为唯一来源。

## API

服务端默认监听 `9051`，路由定义在 `core/network-api/.../api/V1.kt`，客户端和服务端共用：

```bash
TOKEN=$(curl -s -X POST http://127.0.0.1:9051/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","email":"alice@example.com","password":"Passw0rd!"}' | jq -r '.accessToken')

# 发消息（WebSocket 线格式为 Protobuf，这里用 REST 演示鉴权）
curl -s http://127.0.0.1:9051/v1/users/me -H "Authorization: Bearer $TOKEN"

# 文章列表，UUIDv7 游标分页
curl -s "http://127.0.0.1:9051/v1/articles?limit=20"
curl -s "http://127.0.0.1:9051/v1/articles?beforeId=<上一页最后一个 id>"
```

## 多人协作编辑

```bash
./gradlew :server:run                    # 终端 1：REST + JWT
cd collab-server && npm install && \
  PORT=1234 \
  DATABASE_URL=postgres://postgres:postgres@localhost:5432/chat_db \
  KTOR_AUTH_URL=http://127.0.0.1:9051/v1/auth/verify \
  node index.js                          # 终端 2：Yjs 协作服务
```

打开文章编辑器后，客户端连 `ws://127.0.0.1:1234`；多人同时编辑时 Yjs 增量实时同步，2 秒防抖后落库到 `yjs_documents` 表。非作者进入房间是只读的，未发布草稿则拒绝访问。

`docker compose up -d` 会把服务端、PostgreSQL 和协作服务一起拉起，开箱即用。

## 开发

```bash
./gradlew :server:test       # 服务端测试
./gradlew check              # 全部测试
./gradlew :desktopApp:compileKotlin   # 客户端编译检查

cd tiptap-bridge && npm ci && npm run build && npm run build:webview   # 仅在改了编辑器源码时需要
```

提交规范 `<scope>: <summary>`，改动前请看 [CONTRIBUTING.md](./CONTRIBUTING.md)；更详细的模块说明在 [`notes/`](./notes) 和 [`CLAUDE.md`](./CLAUDE.md)。

## License

MIT
