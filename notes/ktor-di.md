# 服务端 Ktor + Koin 集成

## 架构总览

服务端基于 Ktor 3.5 + Netty，使用 Koin 4.2 进行依赖注入，Exposed ORM 操作数据库。

```
Application.module()
  ├── configureFrameworks()     → Koin DI + 数据库 + Schema 创建
  ├── configureSerialization()  → JSON + Protobuf ContentNegotiation
  ├── CORS                      → 跨域支持
  ├── configureSockets()        → WebSocket（Protobuf 帧）
  ├── configureSecurity()       → JWT 认证
  ├── configureHTTP()           → 压缩/限流/缓存/日志/Swagger
  └── configureRouting()        → 路由 + 状态页
```

## 插件清单

| 插件 | 文件 | 说明 |
|---|---|---|
| `ContentNegotiation` | `Serialization.kt` | JSON (ProjectJson) + Protobuf (ProjectProtobuf) |
| `CORS` | `Application.kt` | 开发模式 `anyHost()`，生产模式仅 localhost |
| `WebSockets` | `Sockets.kt` | ping 15s，maxFrame 10MB，Protobuf 序列化 |
| `Authentication` (JWT) | `Security.kt` | HMAC256，支持 query param 方式（WebSocket 用） |
| `CallLogging` | `HTTP.kt` | 仅记录 `/v1` 和 `/ws` 路径 |
| `RateLimit` | `HTTP.kt` | api: 1000/60s，uploads: 60/60s |
| `Compression` | `HTTP.kt` | Gzip for text content |
| `CachingHeaders` | `HTTP.kt` | CSS 缓存 24h |
| `StatusPages` | `Routing.kt` | AppException → HTTP 状态码映射 |
| `Resources` | `Routing.kt` | 类型安全路由（`@Resource` 注解） |
| `Koin` | `Frameworks.kt` | Koin DI 插件 |

## Koin 模块

### MainModule — 基础设施

```kotlin
singleOf(::TokenServiceImpl) bind TokenService::class
singleOf(::HashingServiceImpl) bind HashingService::class
singleOf(::EventBusImpl) bind EventBus::class
singleOf(::TemporaryUploadStoreImpl) bind TemporaryUploadStore::class
single { WebSocketSessionManager() }
single { MessageBroadcaster(get(), get(), get()) }
```

### RepositoryModule — 数据访问（9 个）

```kotlin
singleOf(::UserDataSourceImpl) { bind<UserRepository>() }
singleOf(::ConversationDataSourceImpl) { bind<ConversationRepository>() }
singleOf(::MessageDataSourceImpl) { bind<MessageRepository>() }
singleOf(::UserSettingDataSourceImpl) { bind<UserSettingRepository>() }
singleOf(::GroupProfileDataSourceImpl) { bind<GroupProfileRepository>() }
singleOf(::ConversationParticipantDataSourceImpl) { bind<ConversationParticipantRepository>() }
singleOf(::ContactSourceImpl) { bind<ContactRepository>() }
singleOf(::GroupJoinRequestSourceImpl) { bind<GroupJoinRequestRepository>() }
singleOf(::ContactRequestSourceImpl) { bind<ContactRequestRepository>() }
```

### ServiceModule — 业务服务（9 个）

```kotlin
singleOf(::AuthService)
singleOf(::ContactService)
singleOf(::ConversationLifecycleService)
singleOf(::GroupMembershipService)
singleOf(::ConversationSettingsService)
singleOf(::FileService)
singleOf(::MessageService)
singleOf(::UserService)
singleOf(::RealtimeService)
```

## 数据库

### 双数据库支持

| 模式 | 配置 | 连接池 |
|---|---|---|
| **开发** (H2) | `jdbc:h2:mem:test;DB_CLOSE_DELAY=-1` | 6 |
| **生产** (PostgreSQL) | 环境变量 `POSTGRES_URL` | 10 |

连接池使用 HikariCP。通过 `database.type` 配置切换。

### 表结构（9 张 Exposed 表）

所有表使用 UUID v7 主键（`UuidV7Table`），通过 `kotlin.uuid.Uuid.generateV7()` 生成。

| 表 | 说明 |
|---|---|
| `Users` | 用户（username, email, passwordHash, role, soft-delete） |
| `Conversations` | 会话（PRIVATE/GROUP, lastMessageId, metadata JSONB） |
| `Messages` | 消息（content JSONB, searchText, replyTo, 全文搜索） |
| `Contacts` | 好友关系（composite PK: userId + contactId） |
| `ContactRequests` | 好友申请（发送/接受/拒绝） |
| `ConversationParticipants` | 会话成员（role, settings JSONB, mutedUntil） |
| `GroupProfiles` | 群资料（name, avatar, description） |
| `GroupJoinRequests` | 加群申请 |
| `UserSettings` | 用户设置（key-value） |

### Schema 初始化

```kotlin
transaction(database) {
    SchemaUtils.create(
        Users, Conversations, Messages, Contacts, ContactRequests,
        ConversationParticipants, GroupProfiles, GroupJoinRequests, UserSettings
    )
}
```

### 事务辅助

```kotlin
// Extensions.kt
suspend fun <T> dbQuery(block: () -> T): T =
    withContext(Dispatchers.IO) {
        transaction(db = getKoin().get(), statement = block)
    }
```

## JWT 认证

- **算法**: HMAC256
- **配置**: `application.conf` → `jwt.secret`, `jwt.issuer`, `jwt.audience`
- **Token 有效期**: 默认 15 天
- **WebSocket 兼容**: 支持 `?access_token=xxx` query param（因为浏览器 WebSocket 无法设置自定义 header）

```kotlin
// 从已认证请求中提取 userId
fun ApplicationCall.extractUserId(): Uuid {
    val principal = principal<JWTPrincipal>()
        ?: throw AuthenticationException("Missing JWT principal")
    return principal.payload.getClaim("userId")?.asString()
        ?.let { Uuid.parse(it) }
        ?: throw AuthenticationException("Missing userId claim")
}
```

## WebSocket

- **端点**: `/ws`
- **序列化**: Protobuf（通过 `KotlinxWebsocketSerializationConverter`）
- **会话管理**: `WebSocketSessionManager`（`SessionIndex` + `RoomIndex`）
- **消息广播**: `MessageBroadcaster`（fire-and-forget via `SupervisorJob`）

### 事件总线 → 实时推送流水线

```
Service → EventBus (SharedFlow buffer=64, DROP_OLDEST)
  ├── contactEvents
  ├── conversationEvents
  └── messageEvents
       ↓
RealtimeService (collects flows)
       ↓
MessageBroadcaster (broadcasts to users/conversations/sessions)
```

### WebSocket 消息类型

客户端发送（`MessageRequest` 密封类）：
- `Send` — 发送消息
- `Withdraw` — 撤回消息
- `Read` — 标记已读
- `Typing` — 输入状态

## API 路由（/v1 前缀）

```
/v1/auth/*          → 注册/登录/刷新 token（公开）
/v1/users/*         → 用户搜索/资料/设置/会话列表（部分公开）
/v1/conversations/* → 会话 CRUD/消息/成员/加群（部分公开）
/v1/contacts/*      → 好友管理（需认证）
/v1/files/*         → 文件上传（需认证）
/ws                 → WebSocket（需认证）
```

路由使用 Ktor Resources 类型安全路由（`@Resource` 注解），路由定义在 `core/network` 中与客户端共享。

## 错误处理

```kotlin
// 所有 Service 返回 Result<V, DomainError>
// Route 中调用 .getOrThrow() 抛出 AppException
// StatusPages 捕获 AppException → 映射为 HTTP 状态码

DomainError 密封类层次：
  AuthError → 401/403
  ContactError → 400/404
  ConversationError → 400/404/409
  FileError → 400/413/415
  MessageError → 400/404
  UserError → 400/404/422
```

## 文件上传

- 使用 Ktor multipart 接收
- `TemporaryUploadStore` 暂存文件（防止孤儿文件）
- 文件在消息中引用时才永久保存
- 大小限制（`File.kt`）：avatar 5MB, image 5MB, audio 20MB, video 100MB, file 50MB
- 定期清理未引用的临时文件

## 生产部署

```bash
docker compose up -d  # Server + PostgreSQL
```

环境变量：`DATABASE_TYPE`, `POSTGRES_URL`, `POSTGRES_USERNAME`, `POSTGRES_PASSWORD`, `JWT_SECRET`, `JWT_ISSUER`, `JWT_AUDIENCE`
