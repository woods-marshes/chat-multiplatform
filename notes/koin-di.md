# Koin DI 完整规则

## 核心原则

### 客户端：ViewModel → `viewModelOf`，Repository → `single`

```kotlin
// ✅ 正确：viewModelOf + 构造函数注入
val authModule = module {
    viewModelOf(::AuthViewModel)  // AuthViewModel 的依赖自动从 Koin 解析
}

// ✅ 正确：singleOf + bind interface
val dataModule = module {
    singleOf(::AuthRepositoryImpl) bind AuthRepository::class
    singleOf(::ConversationRepositoryImpl) bind ConversationRepository::class
}

// ✅ 正确：参数化 ViewModel（如 ChatViewModel 需要 conversationId）
val chatModule = module {
    viewModel { (conversationId: String) ->
        ChatViewModel(conversationId, get(), get())
    }
}
```

```kotlin
// ❌ 错误：nullable 构造函数依赖
class AuthViewModel(
    private val repo: AuthRepository? = null  // 禁止！
)

// ❌ 错误：在 domain 建无意义的转发 UseCase
class GetConversationsUseCase(repo: ConversationRepository) {
    operator fun invoke() = repo.getConversations()  // 禁止！直接用 Repository
}
```

### 服务端：`single` + `by inject()`

```kotlin
// Repository 模块
val repositoryModule = module {
    singleOf(::UserDataSourceImpl) { bind<UserRepository>() }
    singleOf(::ConversationDataSourceImpl) { bind<ConversationRepository>() }
}

// Service 模块
val serviceModule = module {
    singleOf(::AuthService)
    singleOf(::UserService)
}

// 在 Ktor Route 中使用
class AuthRoutes {
    private val authService by inject<AuthService>()
}
```

## DI 注册顺序（客户端）

```
platformModule      → PlatformContext（Android Context / 桌面空实现）
commonModule        → AppDispatchers, CoroutineScope
dataStoreModule     → DataStore<Preferences>, AuthTokenDataSource, UserSettingDataSource
databaseModule      → DatabaseHolder
daosModule          → 6 个 DAO（依赖 DatabaseHolder + AppDispatchers）
serializersModule   → ProjectJson, ProjectProtobuf
networkModule       → HttpClient, NetworkConfig, 6 个 API 对象
dataModule          → 5 个 Repository 实现 + MessageRemoteMediator
domainModule        → （空，预留）
authModule → conversationsModule → contactsModule → chatModule → profileModule → settingsModule → searchModule
```

顺序至关重要：后注册的模块可以依赖前面注册的模块。

## 平台特定实现

```kotlin
// expect/actual 模式处理平台差异
// commonMain
expect fun provideDbDriver(): SqlDriver

// androidMain
actual fun provideDbDriver(): SqlDriver = AndroidSqliteDriver(ChatDatabase.Schema, context, "chat.db")

// jvmMain
actual fun provideDbDriver(): SqlDriver = JdbcSqliteDriver("jdbc:sqlite:chat.db")

// webMain
actual fun provideDbDriver(): SqlDriver = WebWorkerDriver(Worker { /* ... */ })
```

## 客户端 ViewModel 消费

```kotlin
// 无参数 ViewModel
@Composable
fun AuthScreen() {
    val viewModel: AuthViewModel = koinViewModel()
}

// 参数化 ViewModel（如 ChatViewModel）
@Composable
fun ChatScreen(conversationId: String) {
    val viewModel: ChatViewModel = koinViewModel(
        key = "chat_$conversationId",
        parameters = { parametersOf(conversationId) }
    )
}
```

使用 `key` 确保不同 conversation 的 ViewModel 实例隔离。

## 顶层依赖获取

在 `ChatApp` 等顶层 Composable 中直接获取依赖：

```kotlin
@Composable
fun ChatApp() {
    val authRepo = koinInject<AuthRepository>()
    val realtimeApi = koinInject<RealtimeApi>()
}
```

## 反模式

| 反模式 | 说明 |
|---|---|
| `factory` 注册 Repository | Repository 应全局单例，用 `single` |
| ViewModel 中 `getKoin().get()` | 全部通过构造函数注入 |
| 手动 `parametersOf(get(), get())` | 无参数用 `viewModelOf(::X)` 自动解析 |
| 在 domain 建 1 行转发 UseCase | ViewModel 直接注入 Repository |
| nullable 构造函数参数 | 全部依赖通过 Koin 注入，不可为 null |

## 服务端 Ktor + Koin

```kotlin
// Frameworks.kt
install(Koin) {
    slf4jLogger(Level.INFO)
    bridge { koinToKtor() }  // 允许在 Route 中 by inject<T>()
    modules(MainModule, repositoryModule, serviceModule)
    createEagerInstances()    // 启动时立即创建所有 singleton
}
```

`bridge { koinToKtor() }` 是关键——它将 Koin 的作用域与 Ktor 的依赖系统连接，使得 Route 中可以直接 `by inject<T>()`。

## 数据库生命周期

`DatabaseHolder`（在 `databaseModule` 中注册为 `single`）管理 SQLDelight 数据库实例，支持按用户切换数据库文件。它内部维护一个 `userId → ChatDatabase` 的映射缓存。
