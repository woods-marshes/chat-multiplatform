# Chat Multiplatform

基于 **Kotlin Multiplatform + Compose Multiplatform + Ktor** 的全栈即时通讯应用。一套 Kotlin 代码同时编译到 Android、Desktop（JVM）、Web（Wasm/JS）客户端，后端使用 Ktor + PostgreSQL，通过 WebSocket 实现实时消息推送。

## 架构概览

```
┌──────────────────────────────────────────────────────┐
│                    composeApp                        │
│  Android  │  Desktop (JVM)  │  Web (Wasm/JS)         │
├──────────────────────────────────────────────────────┤
│  features/*  (auth, chat, contacts, conversations,   │
│               profile, search, settings)             │
├──────────────┬───────────────────────────────────────┤
│  core/*       │  server (Ktor + Netty)               │
│  UI / Data   │  REST API + WebSocket + JWT          │
│  Network     │  Exposed ORM + PostgreSQL / H2        │
│  Database    │                                       │
└──────────────┴───────────────────────────────────────┘
```

- **客户端** (`composeApp`) 依赖 `core/*` 和 `features/*`，按需引入平台特定实现
- **服务端** (`server`) 依赖 `core:model` 和 `core:network`，可独立部署
- **Web 前端** (`web`) 基于 Kobweb（Compose HTML），直接引用 `core/*` 公共模块

## 技术栈

| 层 | 技术 |
|---|---|
| **UI 框架** | Compose Multiplatform 1.12 + Material 3 Adaptive |
| **主题** | Miuix (MIUI 风格) |
| **导航** | Jetpack Navigation 3（Scene + NavEntry，支持自适应布局） |
| **DI** | Koin 4.2（ViewModel → `viewModelOf`，Repository → `single`） |
| **网络** | Ktor 3.5（Client + Server），REST API + WebSocket |
| **序列化** | kotlinx-serialization (JSON + Protobuf) |
| **数据库（客户端）** | SQLDelight 2.3（跨平台本地存储） |
| **数据库（服务端）** | Exposed ORM + PostgreSQL（生产）/ H2（开发） |
| **图片加载** | Coil 3.5 |
| **国际化** | Lyricist |
| **构建** | Gradle 9.5.1 + Kotlin 2.3.21 + Version Catalog |

## 功能特性

- **用户认证** — 注册/登录，JWT Token 认证
- **即时通讯** — 一对一聊天，WebSocket 实时推送
- **会话管理** — 会话列表、置顶、已读状态
- **联系人** — 好友管理，在线状态
- **搜索** — 消息/联系人搜索
- **个人设置** — 头像、昵称、密码修改
- **自适应布局** — 手机/平板/桌面不同窗口尺寸自动适配 ListDetail 模式

## 项目结构

```
chat-multiplatform/
├── composeApp/          # 跨平台客户端入口（Android / Desktop / Web）
├── androidApp/          # Android 壳工程
├── server/              # Ktor 服务端（REST API + WebSocket）
├── web/                 # Kobweb 前端（Compose HTML）
│
├── core/
│   ├── common/          # 公共工具（Dispatchers, Koin 模块）
│   ├── model/           # 领域模型、Error 密封类、UiState
│   ├── data/            # Repository 接口与实现（编排数据源）
│   ├── network/         # Ktor HttpClient / REST API / DTO / WebSocket
│   ├── database/        # SQLDelight 数据库（6 表）+ DAO
│   ├── database-room/   # Room 3.0 KMP 数据库（替代方案）
│   ├── datastore/       # Key-Value 存储（Token / 偏好设置）
│   ├── domain/          # 预留：未来业务逻辑 UseCase
│   ├── ui/              # Compose Multiplatform 共享组件 & 主题
│   └── navigation/      # Navigation 3 路由定义
│
├── features/
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
├── compose.yml          # Docker Compose（Server + PostgreSQL）
└── Dockerfile           # 服务端多阶段构建
```

## 快速开始

### 环境要求

- **JDK** 21（服务端）/ JDK 17+（Android）
- **Android Studio** (推荐最新 Canary) — 如需编译 Android 目标
- **Docker Desktop** — 如需容器化部署

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

# Android 客户端（需要模拟器或真机）
./gradlew :androidApp:installDebug
```

### 常用命令

| 目的 | 命令 |
|---|---|
| 编译检查 | `./gradlew :composeApp:jvmMainClasses` |
| 全部测试 | `./gradlew check` |
| 服务端测试 | `./gradlew :server:test` |
| 列出所有任务 | `./gradlew tasks` |

> **注意**：配置缓存已启用，遇到异常行为时加 `--no-configuration-cache`。

## Docker 部署

`.env` 已内置 demo 默认值，可直接启动。生产部署前编辑 `.env` 修改密码和密钥即可。

```bash
# 本地 demo：一键启动
docker compose up -d

# 生产部署：先修改密钥再启动
vim .env
docker compose up -d
```

服务端口：`9051`。数据库端口 `5432` 默认不暴露（仅容器内通信），如需本地调试可取消 `compose.yml` 中 db 的 `ports` 注释。

查看日志：`docker compose logs -f server`

### 自动构建（GitHub Actions + GHCR）

项目可配置 GitHub Actions 在每次推送时自动构建镜像并推送到 GitHub Container Registry：

```yaml
# .github/workflows/docker.yml
name: Build and Push
on:
  push:
    branches: [main]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Build & push
        uses: docker/build-push-action@v6
        with:
          push: true
          tags: ghcr.io/woodsmarshes/chat-server:latest
```

用户拉取预构建镜像即可部署，无需本地安装 JDK 或 Gradle。


## 配置说明

服务端通过环境变量配置（`compose.yml` 中设置）：

| 环境变量 | 说明 | 默认值 |
|---|---|---|
| `DATABASE_TYPE` | 数据库类型（`h2` / `postgres`） | `h2` |
| `POSTGRES_URL` | PostgreSQL JDBC URL | — |
| `POSTGRES_USERNAME` | 数据库用户名 | — |
| `POSTGRES_PASSWORD` | 数据库密码 | — |
| `JWT_SECRET` | JWT 签名密钥 | — |
| `JWT_ISSUER` | JWT 签发者 | — |
| `JWT_AUDIENCE` | JWT 受众 | — |

> **生产部署务必替换 `JWT_SECRET` 和数据库密码！**

## 开发约定

- **Git Commit**：`<scope>: <summary>`，scope 可选 `client`/`server`/`web`/`core`/`features`/`build`/`deps`
- **代码风格**：Kotlin Official Style，4 空格缩进，显式 import
- **JVM 目标**：composeApp/Android → 17，composeApp/Desktop → 25，server → 25
- **依赖管理**：`gradle/libs.versions.toml` 为唯一版本来源

详见 `CLAUDE.md` 和 `@notes/` 下的详细文档。

## License

MIT
