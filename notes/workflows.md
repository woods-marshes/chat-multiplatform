# 开发工作流

## 添加新 Feature

一个 Feature 模块包含 4 层（`model/ui/di/navigation`），共 5 个源文件。以添加 `notes` feature 为例：

### 1. 创建模块目录

```
features/notes/
├── build.gradle.kts
└── src/commonMain/kotlin/com/github/woodsmarshes/chat/feature/notes/
    ├── model/NotesUiState.kt
    ├── ui/NotesScreen.kt
    ├── ui/NotesViewModel.kt
    ├── di/NotesModule.kt
    └── navigation/NotesNavigation.kt
```

### 2. build.gradle.kts

```kotlin
plugins {
    alias(libs.plugins.project.kotlinMultiplatform)
    alias(libs.plugins.project.composeMultiplatform)
    alias(libs.plugins.kotlin.plugin.serialization)
}

kotlin {
    // 继承约定插件的目标配置
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.ui)
            implementation(projects.core.common)
            implementation(projects.core.data)
            implementation(projects.core.domain)
            implementation(projects.core.model)
            implementation(projects.core.navigation)
            implementation(libs.kotlinx.coroutines.core)
            // 按需添加额外依赖
        }
    }
}
```

### 3. 在 settings.gradle.kts 中注册

```kotlin
include(":features:notes")
```

### 4. 在 composeApp/build.gradle.kts 中添加依赖

```kotlin
implementation(projects.features.notes)
```

### 5. NavKey 定义（navigation/）

```kotlin
@Serializable
data object NotesNavKey : NavKey

fun EntryProviderScope<NavKey>.notesEntry(
    onBack: () -> Unit,
    metadata: Map<String, NavEntryMetadata>,
) {
    entry(
        key = { it.key },
        metadata = { _, _ -> metadata },
        content = { _, _, _ -> NotesScreen(onBack = onBack) }
    )
}
```

### 6. UiState（model/）

```kotlin
data class NotesUiState(
    val items: List<NoteItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)
```

### 7. ViewModel（ui/）

```kotlin
class NotesViewModel(
    private val someRepository: SomeRepository,
) : ViewModel() {
    // 业务逻辑
}
```

### 8. Koin Module（di/）

```kotlin
val notesModule = module {
    viewModelOf(::NotesViewModel)
}
```

### 9. Screen（ui/）

```kotlin
@Composable
fun NotesScreen(onBack: () -> Unit) {
    val viewModel: NotesViewModel = koinViewModel()
    // UI
}
```

### 10. 注册到 initKoin() 和 entryProvider

在 `AppModule.kt` 的 `startKoin { modules(...) }` 末尾添加 `notesModule`。

在 `MainApp.kt` 的 `entryProvider` 中注册 `notesEntry(onBack = { navigator.goBack() }, metadata = emptyMap())`。

### 11. 添加快捷导航方法（可选）

在 `Navigator` 中添加便捷导航方法（如需要）。

## 修改数据库

### 客户端（SQLDelight）

1. 编辑 `core/database/src/commonMain/sqldelight/com/github/woodsmarshes/chat/core/database/ChatDatabase.sq`
2. 添加新表或修改现有表的 SQL 定义
3. 如添加新表，创建对应的 DAO 接口和实现
4. 在 `DaosModule.kt` 中注册新 DAO
5. 运行 `./gradlew :core:database:generateCommonMainChatDatabaseInterface` 生成代码

### 服务端（Exposed ORM）

1. 在 `server/.../repository/database/schema/` 下新建表定义文件
2. 表继承 `UuidV7Table`（使用 UUID v7 主键）
3. 创建对应的 Repository 接口 + DataSource 实现
4. 在 `RepositoryModule.kt` 中注册
5. 在 `Application.kt` 的 `SchemaUtils.create()` 中添加新表

## 添加新 API 端点

### 1. 定义路由资源（core:network）

在 `core/network/.../V1.kt` 中添加 `@Resource` 类：

```kotlin
@Resource("/v1/notes")
class NotesResource {
    @Resource("{id}")
    class Note(val parent: NotesResource = NotesResource(), val id: String)
}
```

### 2. 实现服务端路由（server）

```kotlin
fun Route.notesRoutes() {
    val noteService by inject<NoteService>()
    route("/v1/notes") {
        get { ... }
        post { ... }
    }
}
```

### 3. 实现客户端 API（core:network）

```kotlin
class NotesApi(private val client: HttpClient) {
    suspend fun getNotes(): List<Note> = client.get("/v1/notes").body()
}
```

在 `NetworkModule.kt` 中注册：`singleOf(::NotesApi)`

## 修复 Bug 流程

1. **复现**：确认问题现象和触发条件
2. **定位**：用 `Grep` 搜索相关代码路径，阅读关键文件
3. **诊断**：添加临时日志（`KotlinLogging`）确认根因
4. **修复**：最小化改动，遵循现有代码风格
5. **验证**：`./gradlew :composeApp:jvmMainClasses` 编译通过
6. **测试**：运行 `./gradlew :server:run` + `./gradlew :composeApp:run` 端到端验证

## 常用诊断命令

```bash
# 编译检查
./gradlew :composeApp:jvmMainClasses

# 服务端编译 + 运行
./gradlew :server:run

# 无配置缓存（遇到异常时）
./gradlew :composeApp:jvmMainClasses --no-configuration-cache

# 检查 Koin 依赖图
# 如果启动时报 NoDefinitionFoundException，检查 DI 注册顺序是否正确
```
