# Article 多平台实施计划

## 总览

目标：将当前仅在 web 模块可用（Kotlin/JS + React + Tiptap）的文章写作与浏览功能，扩展为 Compose Multiplatform 原生实现，覆盖 Desktop (JVM) 和 Android。

### 平台策略

| 平台 | 文章浏览 | 文章编辑 |
|---|---|---|
| **Web (Wasm/JS)** | ✅ 已有：`ArticleContentRenderer` + `renderArticleContent` | ✅ 已有：`TiptapEditorBridge` 直接挂载 |
| **Desktop (JVM)** | 🆕 ComposeNativeWebView 加载静态渲染 HTML | 🆕 ComposeNativeWebView 加载编辑器 HTML Shell |
| **Android** | 🆕 ComposeNativeWebView（android.webkit.WebView） | 🆕 ComposeNativeWebView 加载编辑器 HTML Shell |
核心思路：**Web 平台继续使用直接 React 集成（性能最佳），Desktop/Android 通过 ComposeNativeWebView 加载自包含 HTML 页面，利用 JS ↔ Kotlin 桥接通信。**

---

## Phase 1: 客户端数据库 Article DAO（core:database）

**状态**：Article.sq 已有表定义，但缺少查询语句和 DAO。

### 1.1 补充 TypeConverter

`core/database/src/commonMain/kotlin/.../utils/TypeConverter.kt`

- 添加 `articleStatusAdapter: ColumnAdapter<ArticleStatus, String>`（SQLDelight `TEXT AS ArticleStatus` 依赖此 adapter）

### 1.2 编写 Article.sq 查询

`core/database/src/commonMain/sqldelight/.../db/Article.sq`

需要补充以下查询（参考 `Conversation.sq` / `Messages.sq` 的模式）：

```sql
-- 插入/更新
upsertArticle:
INSERT OR REPLACE INTO Article(...) VALUES ?;

-- 查询
getArticleById:
SELECT * FROM Article WHERE id = ? AND deleted_at IS NULL;

listAllArticles:
SELECT * FROM Article WHERE status = 'PUBLISHED' AND deleted_at IS NULL
ORDER BY published_at DESC LIMIT :limit OFFSET :offset;

listMyArticles:
SELECT * FROM Article WHERE author_id = ? AND deleted_at IS NULL
ORDER BY updated_at DESC LIMIT :limit OFFSET :offset;

-- 软删除
softDeleteArticle:
UPDATE Article SET deleted_at = ? WHERE id = ?;

-- 硬删除（可选）
hardDeleteArticle:
DELETE FROM Article WHERE id = ?;
```

### 1.3 创建 ArticleDao

`core/database/src/commonMain/kotlin/.../dao/ArticleDao.kt`
`core/database/src/commonMain/kotlin/.../dao/ArticleDaoImpl.kt`

- 参考现有 `ConversationDao` / `ConversationDaoImpl` 模式
- 接口方法：`upsert`, `getById`, `listAll`, `listByAuthor`, `softDelete`, `hardDelete`
- 实现直接委托给 SQLDelight 生成的 `articleQueries`

### 1.4 注册到 DI

在 `DaosModule.kt` 中注册 `single<ArticleDao> { ArticleDaoImpl(dbProvider, ioContext) }`

---

## Phase 2: UI 模型、网络 DTO、UUIDv7 游标分页、离线优先、转换逻辑

> **核心思路**：将 Article 的分页从 offset-based 改为 UUIDv7 cursor-based（对齐 Message 模式），
> 新增列表专用的 UI 模型（不含 content），新增网络 Response DTO（content 可空），
> 创建离线优先的 RemoteMediator。

### Step 2.1: 创建 ArticleListUiModel（core:model）

**操作**：新建文件

**文件路径**：`core/model/src/commonMain/kotlin/com/github/woodsmarshes/chat/core/model/ui/ArticleListUiModel.kt`

**设计**（参考 `ConversationUiModel` / `MessageUiModel` 的扁平化模式）：

```kotlin
package com.github.woodsmarshes.chat.core.model.ui

import com.github.woodsmarshes.chat.core.model.ArticleStatus
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * 文章列表专用 UI 模型，不含 content（正文），
 * 作者信息扁平化为列表可直接绑定的展示字段。
 */
data class ArticleListUiModel(
    val id: Uuid,
    val title: String,
    val authorId: Uuid,
    val authorUsername: String,
    val authorDisplayName: String?,
    val authorAvatar: String?,
    val status: ArticleStatus,
    val excerpt: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val publishedAt: Instant?,
    val coverImage: String?,
    val slug: String?,
)

/** 作者信息精简版（列表展示用），不暴露 email/bio/role。 */
data class ArticleAuthorUi(
    val id: Uuid,
    val username: String,
    val displayName: String?,
    val avatarUrl: String?,
)
```

⚠️ `Article`（core:model）保留不动 — 含 content 和完整 `SimpleUser`，供文章详情和编辑器使用。

---

### Step 2.2: 创建 ArticleListResponse DTO（core:network）

**操作**：新建文件

**文件路径**：`core/network/src/commonMain/kotlin/com/github/woodsmarshes/chat/core/network/dto/article/ArticleListResponse.kt`

**设计**：content 可空（列表请求不带正文）、作者信息嵌入。

```kotlin
@Serializable
data class ArticleListResponse(
    @ProtoNumber(1) val id: Uuid,
    @ProtoNumber(2) val title: String,
    @ProtoNumber(3)
    @Contextual
    val content: JsonElement? = null,          // 列表请求时为空
    @ProtoNumber(4) val author: ArticleAuthorDto,
    @ProtoNumber(5) val status: ArticleStatus,
    @ProtoNumber(6) val excerpt: String? = null,
    @ProtoNumber(7) val createdAt: Instant,
    @ProtoNumber(8) val updatedAt: Instant,
    @ProtoNumber(9) val publishedAt: Instant? = null,
    @ProtoNumber(10) val coverImage: String? = null,
    @ProtoNumber(11) val slug: String? = null,
)

@Serializable
data class ArticleAuthorDto(
    @ProtoNumber(1) val id: Uuid,
    @ProtoNumber(2) val username: String,
    @ProtoNumber(3) val displayName: String? = null,
    @ProtoNumber(4) val avatarUrl: String? = null,
    @ProtoNumber(5) val createdAt: Instant,
    @ProtoNumber(6) val updatedAt: Instant,
    @ProtoNumber(7) val deletedAt: Instant? = null,
)
```

---

### Step 2.3: V1.Articles 路由改为 UUIDv7 游标分页（core:network）

**操作**：编辑 `core/network/src/commonMain/kotlin/.../api/V1.kt`

**变更前**：
```kotlin
@Resource("/articles")
class Articles(val parent: V1 = V1(), val offset: Long = 0, val limit: Int = 50) {
```

**变更后**（对齐 `V1.Conversations.Id.Messages` 的 cursor 模式）：
```kotlin
@Resource("/articles")
class Articles(
    val parent: V1 = V1(),
    val beforeId: Uuid? = null,   // UUIDv7 cursor: 返回 ID < beforeId 的文章
    val limit: Int = 50
) {
```

**影响范围**：所有引用 `V1.Articles(...)` 的代码需同步更新参数名。

---

### Step 2.4: 更新 ArticleApi（client-side）

**操作**：编辑 `core/network/src/commonMain/kotlin/.../api/rest/ArticleApi.kt`

**变更**：
- `listArticles(offset, limit)` → `listArticles(beforeId: Uuid? = null, limit: Int = 50)`
- `listMyArticles(offset, limit)` → `listMyArticles(beforeId: Uuid? = null, limit: Int = 50)`
- 返回类型改为 `List<ArticleListResponse>`（列表不带 content）

---

### Step 2.5: 更新 server ArticleRoutes

**操作**：编辑 `server/src/main/kotlin/.../routes/ArticleRoutes.kt`

**变更**：
```kotlin
// 变更前
get<V1.Articles> { params ->
    val articles = articleService.listArticles(
        offset = params.offset,
        limit = params.limit
    ).getOrThrow()
    call.respond(articles)
}
// 变更后
get<V1.Articles> { params ->
    val articles = articleService.listArticles(
        beforeId = params.beforeId,
        limit = params.limit
    ).getOrThrow()
    call.respond(articles)
}
```

同步更新 `V1.Articles.My` 的处理。

---

### Step 2.6: 更新 server ArticleService

**操作**：编辑 `server/src/main/kotlin/.../service/ArticleService.kt`

**变更**：
```kotlin
// 变更前
suspend fun listArticles(offset: Long = 0, limit: Int = 50): Result<List<Article>, ArticleError>
suspend fun listMyArticles(offset: Long = 0, limit: Int = 50, userId: Uuid): Result<List<Article>, ArticleError>

// 变更后
suspend fun listArticles(beforeId: Uuid? = null, limit: Int = 50): Result<List<Article>, ArticleError>
suspend fun listMyArticles(beforeId: Uuid? = null, limit: Int = 50, userId: Uuid): Result<List<Article>, ArticleError>
```

内部委托给 repository 的同名游标方法。

---

### Step 2.7: 更新 server ArticleRepository

**操作**：编辑 `server/src/main/kotlin/.../repository/ArticleRepository.kt` 及实现

**变更**：`listAll` 改为 UUIDv7 游标分页（对齐 `MessageRepository.getHistory` 的 `beforeId` 模式）：
- `ArticleRepository.listAll(offset, limit, userId)` → `listAll(beforeId: Uuid?, limit: Int, userId: Uuid?)`
- 实现中使用 Exposed 的 `Articles.id less beforeId`（与 `Messages.id less it` 一致）+ `orderBy(Articles.id, SortOrder.DESC)`

---

### Step 2.8: 创建转换逻辑（core:data/model）

**操作**：新建文件

**文件路径**：`core/data/src/commonMain/kotlin/com/github/woodsmarshes/chat/core/data/model/Article.kt`

**内容**（参考 `Message.kt` 中的 `toMessageEntity` 系列函数）：

```kotlin
// ArticleListResponse (网络) → Article (本地 DB entity)
fun ArticleListResponse.toArticleEntity(): Article

// ListAllArticlesWithAuthor (DB JOIN 视图) → ArticleListUiModel (列表 UI)
fun ListAllArticlesWithAuthor.toArticleListUiModel(): ArticleListUiModel

// ArticleListResponse → ArticleEntity 缓存写入
fun ArticleListResponse.toArticleEntity(): Article

// 批量转换
fun List<ListAllArticlesWithAuthor>.toArticleListUiModels(): List<ArticleListUiModel>
```

---

### Step 2.9: 创建 ArticleRemoteMediator（core:data/paging）

**操作**：新建文件

**文件路径**：`core/data/src/commonMain/kotlin/com/github/woodsmarshes/chat/core/data/paging/ArticleRemoteMediator.kt`

**设计**（完全仿照 `MessageRemoteMediator`）：

```kotlin
@ExperimentalPagingApi
class ArticleRemoteMediator(
    private val articleApi: ArticleApi,
    private val articleDao: ArticleDao,
    private val userDao: UserDao,
    private val appDispatchers: AppDispatchers,
) : RemoteMediator<Uuid, ListAllArticlesWithAuthor>() {

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Uuid, ListAllArticlesWithAuthor>
    ): MediatorResult {
        // 1. 确定 cursor（REFRESH → null, APPEND → lastItem.id, PREPEND → return success）
        // 2. 调用 articleApi.listArticles(beforeId = cursor, limit = pageSize)
        // 3. 将 response 写入 local DB（articleDao.upsert + userDao.upsertUser）
        // 4. return MediatorResult.Success(endOfPaginationReached = size < pageSize)
    }
}
```

离线优先数据流：`UI(paging) → local DB(WithAuthor) → ArticleRemoteMediator → articleApi → server → local DB`

---

### Step 2.10: 更新 web 模块 ArticleRepository

**操作**：编辑 `web/src/jsMain/kotlin/.../storage/ArticleRepository.kt`

**变更**：适配新 API 签名（`listAll()` 调用 `articleApi.listArticles(beforeId, limit)`）。

---

### Phase 2 变更文件汇总

| # | 层 | 文件 | 操作 |
|---|---|---|---|
| 2.1 | core:model | `ui/ArticleListUiModel.kt` | 🆕 新建 |
| 2.2 | core:network | `dto/article/ArticleListResponse.kt` | 🆕 新建 |
| 2.3 | core:network | `api/V1.kt` | ✏️ 游标分页参数 |
| 2.4 | core:network | `api/rest/ArticleApi.kt` | ✏️ 新签名 + 新返回类型 |
| 2.5 | server | `routes/ArticleRoutes.kt` | ✏️ 适配新参数 |
| 2.6 | server | `service/ArticleService.kt` | ✏️ 新签名 |
| 2.7 | server | `repository/ArticleRepository.kt` | ✏️ UUIDv7 游标分页 |
| 2.8 | core:data | `model/Article.kt` | 🆕 转换扩展函数 |
| 2.9 | core:data | `paging/ArticleRemoteMediator.kt` | 🆕 离线优先分页 |
| 2.10 | web | `storage/ArticleRepository.kt` | ✏️ 适配新 API |

### Phase 2 完成标准

| 检查项 | 验证方法 |
|---|---|
| `ArticleListUiModel` 不含 `content` 字段 | `grep "content" ArticleListUiModel.kt` → 无结果 |
| `ArticleListResponse.content` 为可空 | `grep "val content" ArticleListResponse.kt` → `?` |
| V1.Articles 使用 `beforeId` 游标 | `grep "beforeId" V1.kt` |
| ArticleApi 返回 `List<ArticleListResponse>` | `grep "ArticleListResponse" ArticleApi.kt` |
| server ArticleRepository 使用 `id less beforeId` | `grep "less" ArticleRepository.kt` |
| ArticleRemoteMediator 编译通过 | `./gradlew :core:data:jvmMainClasses` |
| 全链编译通过 | `./gradlew :core:database:jvmMainClasses :core:data:jvmMainClasses :core:network:jvmMainClasses :server:compileKotlin :web:compileKotlinJs` |

---

## Phase 2: 数据层 ArticleRepository（core:data）

### 2.1 定义 ArticleRepository 接口

`core/data/src/commonMain/kotlin/.../repository/ArticleRepository.kt`

```kotlin
interface ArticleRepository {
    suspend fun listArticles(offset: Long, limit: Int): Result<List<Article>, ArticleError>
    suspend fun getArticle(id: Uuid): Result<Article, ArticleError>
    suspend fun listMyArticles(userId: Uuid, offset: Long, limit: Int): Result<List<Article>, ArticleError>
    suspend fun saveArticle(...): Result<Article, ArticleError>
    suspend fun deleteArticle(userId: Uuid, id: Uuid): Result<Unit, ArticleError>
}
```

### 2.2 实现 ArticleRepositoryImpl

`core/data/src/commonMain/kotlin/.../repository/ArticleRepositoryImpl.kt`

- **离线优先**策略：先读写本地 SQLDelight 数据库，后台同步到服务器
- 注入 `ArticleDao` + `ArticleApi`（Ktor 网络客户端）
- 使用 `kotlin-result` 的 `Result<T, E>` 模式（与 server ArticleService 保持一致）
- 同步逻辑：
  - `listArticles`：尝试从 API 拉取 → 写入本地 DB → 返回本地数据
  - `saveArticle`：先本地暂存 → 调用 API → 更新本地
  - API 调用失败时降级到本地缓存

### 2.3 注册到 DI

在 `DataModule.kt` 中注册 `single<ArticleRepository> { ArticleRepositoryImpl(get(), get()) }`

---

## Phase 3: Tiptap WebView HTML Shell（tiptap-bridge）

**这是最关键的一步**：为 ComposeNativeWebView 创建自包含的 HTML 页面，让 Tiptap 编辑器可以在原生 WebView 中运行。

### 3.1 创建 tiptap-webview 构建

在 `tiptap-bridge/` 中新增 Vite 构建配置，产出**完全自包含**的 HTML：

- **入口**：`tiptap-bridge/src/webview/editor.html` — 编辑器（含工具栏、标题输入）
- **入口**：`tiptap-bridge/src/webview/viewer.html` — 文章查看（静态渲染，无编辑器）
- **打包方式**：Vite `build.lib` + `inlineDynamicImports`，所有 JS/CSS 内联到单个 HTML 文件
- **React/ReactDOM** 内联绑定（不能像 UMD 那样用 external）

### 3.2 编辑器 Shell 功能

`editor.html` 加载后：

1. 初始化 Tiptap 编辑器（与 `simple-editor.tsx` 扩展列表一致）
2. 暴露 `window.__editorShell` 全局对象：
   ```js
   window.__editorShell = {
     setContent(json) { editor.commands.setContent(json) },
     getContent() { return editor.getJSON() },
     setTitle(t) { /* 更新标题输入框 */ },
     onChange(cb) { editor.on('update', () => cb(editor.getJSON())) },
   }
   ```
3. 通过 `window.kmpJsBridge.callNative('editorReady', {}, callback)` 通知 Kotlin 就绪
4. 每次内容变更时调用 `window.kmpJsBridge.callNative('contentChanged', { json: ... })`
5. 标题变更时调用 `window.kmpJsBridge.callNative('titleChanged', { title: ... })`

### 3.3 查看器 Shell 功能

`viewer.html` 加载后：

1. 暴露 `window.__viewerShell`：
   ```js
   window.__viewerShell = {
     renderContent(json) { /* 使用 renderArticleContent 渲染 */ },
   }
   ```

### 3.4 Gradle 构建集成

在 `web/build.gradle.kts` 中（或在 tiptap-bridge 中创建独立 Gradle 任务）：

```kotlin
val buildTiptapWebview = tasks.register<Exec>("buildTiptapWebview") {
    // npm run build:webview → 产出 dist/editor.html, dist/viewer.html
}
```

产物复制到 `composeApp/src/commonMain/composeResources/files/`（通过 Compose Resources API 访问）。

---

## Phase 4: features/article — 文章浏览

### 4.1 模块结构

参考 `features/auth/` 的四层结构：

```
features/article/src/commonMain/kotlin/.../
├── model/ArticleListUiState.kt     # UI 状态
├── ui/ArticleListScreen.kt         # 文章列表 Composable
├── ui/ArticleDetailScreen.kt       # 文章详情 Composable
├── ui/ArticleViewModel.kt          # ViewModel
├── di/ArticleModule.kt             # Koin 模块
└── navigation/ArticleNavigation.kt # Navigation 3 路由
```

### 4.2 ArticleListScreen

- 分页列表，每项显示：标题、摘要、作者、发布时间、状态标签
- 下拉刷新、上拉加载更多
- 使用 `LazyColumn` + `PagingData`（可选）
- 点击跳转到详情页

### 4.3 ArticleDetailScreen

- 显示文章标题、作者信息、元数据
- **文章正文渲染**：使用 ComposeNativeWebView 加载 `viewer.html`，通过 `evaluateJavaScript` 调用 `__viewerShell.renderContent(json)`
- 操作按钮：编辑（跳转 editor）、删除（确认对话框）
- 支持 `ListDetailSceneStrategy`（大屏双栏布局）

### 4.4 ArticleViewModel

```kotlin
class ArticleViewModel(
    private val articleRepository: ArticleRepository,
) : ViewModel() {
    // StateFlow<ArticleListUiState>
    // fun loadArticles()
    // fun loadArticle(id: Uuid)
    // fun deleteArticle(id: Uuid)
}
```

注入方式：`viewModelOf(::ArticleViewModel)`

### 4.5 Navigation 路由

```kotlin
// 在 core:navigation 中添加
@Serializable data class ArticleList(val filter: String? = null)
@Serializable data class ArticleDetail(val id: Uuid)
```

---

## Phase 5: features/article-editor — 文章编辑器

### 5.1 挑战

编辑器是最复杂的部分。Tiptap 是 React 组件，不能在 Compose 中直接使用。策略：

| 方案 | 优点 | 缺点 |
|---|---|---|
| **A: WebView 嵌入** | 复用全部 tiptap 功能、扩展 | JS ↔ Kotlin 桥接复杂、加载延迟 |
| **B: Compose 原生编辑器** | 原生体验、无桥接 | 开发量巨大、功能不如 Tiptap 丰富 |
| **C: 混合（WebView 编辑 + 原生 UI 外壳）** | 兼顾功能与体验 | 跨平台细节多 |

**推荐方案 C**：WebView 只负责富文本编辑区，标题、保存按钮、状态切换等用原生 Compose UI 包裹。

### 5.2 模块结构

```
features/article-editor/src/commonMain/kotlin/.../
├── model/EditorUiState.kt          # 编辑器 UI 状态
├── ui/ArticleEditorScreen.kt       # 编辑器主界面 (Compose)
├── ui/EditorWebView.kt             # 封装 ComposeNativeWebView 的 Composable
├── ui/ArticleEditorViewModel.kt    # ViewModel
├── ui/components/EditorToolbar.kt  # 顶部工具栏（保存、发布、返回...）
├── di/ArticleEditorModule.kt       # Koin 模块
└── navigation/ArticleEditorNavigation.kt
```

### 5.3 EditorWebView 封装

```kotlin
@Composable
fun EditorWebView(
    initialContent: JsonElement,
    onContentChanged: (JsonElement) -> Unit,
    onTitleChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
)
```

关键实现细节：
1. 使用 `rememberWebViewState` 加载 `editor.html`（通过 Compose Resources 路径）
2. 监听 `onPageFinished` → 注入初始内容 `navigator.evaluateJavaScript("__editorShell.setContent(...)")`
3. 注册 `kmpJsBridge` 回调处理 `contentChanged` 和 `titleChanged`
4. 支持 Kotlin → JS 命令：`setContent()`, `focus()`, `undo()`, `redo()`

### 5.4 JS ↔ Kotlin 桥接细节

**Kotlin → JS**（通过 `navigator.evaluateJavaScript`）：
```kotlin
fun setContent(json: JsonElement) {
    val jsCode = """
        __editorShell.setContent(${ProjectJson.encodeToString(json)});
    """.trimIndent()
    navigator.evaluateJavaScript(jsCode)
}
```

**JS → Kotlin**（通过 `kmpJsBridge.callNative`）：
```kotlin
// 在 onCreated 回调中设置 bridge 处理器
// ComposeNativeWebView 会自动注入 kmpJsBridge
// 在 compose-webview-multiplatform API 中通过 onJsBridgeMessage 回调处理
```

⚠️ **注意**：需确认 ComposeNativeWebView 1.0.0-beta-02 的 JS Bridge API 具体使用方式。当前文档提到 `window.kmpJsBridge.callNative("echo", {...}, callback)`，需要验证 Kotlin 侧如何注册对应处理器。

### 5.5 ArticleEditorViewModel

```kotlin
class ArticleEditorViewModel(
    private val articleRepository: ArticleRepository,
) : ViewModel() {
    // articleId: Uuid? (null = 新建)
    // title: StateFlow<String>
    // content: StateFlow<JsonElement>
    // fun save(status: ArticleStatus)
    // fun publish()
    // fun delete()
}
```

---

## Phase 6: 集成到 composeApp

### 6.1 build.gradle.kts 更新

在 `composeApp/build.gradle.kts` 的 `commonMain.dependencies` 中添加：

```kotlin
implementation(projects.features.article)
implementation(projects.features.articleEditor)
implementation(libs.composewebview)
```

### 6.2 Desktop JVM 参数

在 `composeApp/build.gradle.kts` 的 `compose.desktop` 块中添加：

```kotlin
compose.desktop {
    application {
        jvmArgs += "--enable-native-access=ALL-UNNAMED"
    }
}
```

### 6.3 Navigation 集成

在 `composeApp` 的 navigation graph 中注册 article 路由：

```kotlin
// 文章列表
scene<ArticleList> { ArticleListScreen() }
// 文章详情
scene<ArticleDetail> { ArticleDetailScreen() }
// 新建文章
scene<CreateArticle> { ArticleEditorScreen(articleId = null) }
// 编辑文章
scene<EditArticle> { ArticleEditorScreen(articleId = it.id) }
```

### 6.4 DI 注册

在 `composeApp` 的 `commonModule` 链中添加 article 相关模块。

### 6.5 资源文件

将 tiptap-webview 构建产物放入 Compose Resources：
```
composeApp/src/commonMain/composeResources/files/
├── editor.html    # 编辑器 HTML Shell
└── viewer.html    # 文章查看器 HTML Shell
```

通过 `Res.readBytes("files/editor.html")` 或 ComposeNativeWebView 的 `loadHtmlFile` 加载。

---

## Phase 7: 平台特定适配

### 7.1 Desktop（JVM — Windows/macOS/Linux）

- **Windows**: 需要系统安装 WebView2 Runtime（Win11 自带，Win10 可能需要安装）
- **macOS**: WKWebView 内置于系统，无需额外配置
- **Linux**: 需要 WebKitGTK 库（`libwebkit2gtk-4.1-dev`）
- 需要在 `compose.desktop.application` 添加 `--enable-native-access=ALL-UNNAMED`
- 编辑器的图片上传需要特别处理（文件选择器通过 bridge 转交 Kotlin 侧 FileKit 处理）

### 7.2 Android

- 使用 `android.webkit.WebView`（系统内置）
- API 24+ 支持良好
- 需要在 AndroidManifest 添加 INTERNET 权限
- 若编辑器要离线使用，需要确保 editor.html 通过 `file:///android_asset/` 加载

### 7.3 Web（Wasm/JS）

- **不改变现有实现**：继续使用直接的 React 集成
- features/article 和 features/article-editor 的 Compose UI 部分在 Wasm/JS 平台的可用性取决于 Compose Multiplatform Wasm 支持状态
- 如果 Compose for Wasm 不可用，web 平台跳过 features/*，继续使用现有 web 模块

---

## 风险与注意事项

| 风险 | 缓解措施 |
|---|---|
| **ComposeNativeWebView beta 稳定性** | 使用 1.0.0-beta-02，关注 release 更新；遇到 bug 及时反馈上游 |
| **JS Bridge 通信延迟** | Tiptap `update` 事件频率高，需在 JS 侧做 debounce（300ms）再通知 Kotlin |
| **HTML Shell 加载性能** | 编辑器 HTML 预计 ~2-5MB（含 React+Tiptap），首次加载可接受；考虑预加载/缓存 |
| **Wasm/JS 上 Compose 与 React 共存** | web 平台直接跳过 Compose feature 模块，两者不混用 |
| **Android WebView 兼容性** | API 24+ 的 WebView 支持 ES6，Tiptap 3.x 需要确认兼容性 |
| **图片上传路径差异** | Desktop 用 FileKit，Android 用 ActivityResultContracts — 统一通过 bridge 调用 Kotlin 侧处理（MVP 暂不实现，仅支持外链 URL） |
| **离线场景** | Phase 2 的离线优先策略覆盖网络断开情况；editor.html 作为本地资源加载 |

---

## 工时估算

| 阶段 | 预估工作量 | 优先级 |
|---|---|---|
| Phase 1: 客户端 DB DAO | 小（1-2 天） | 🔴 高 |
| Phase 2: 数据层 Repository | 中（2-3 天） | 🔴 高 |
| Phase 3: Tiptap WebView Shell | 大（3-5 天） | 🔴 高（核心依赖） |
| Phase 4: features/article | 中（3-4 天） | 🟡 中 |
| Phase 5: features/article-editor | 大（4-6 天） | 🟡 中 |
| Phase 6: 集成 | 中（2-3 天） | 🟢 低（依赖 4/5） |
| Phase 7: 平台适配 | 中（2-4 天） | 🟢 低 |

总预估：**17-27 天**（单人全职）。

---

## 已确认的决议

| # | 问题 | 决议 |
|---|---|---|
| 1 | tiptap-webview 放置位置 | ✅ 作为 `tiptap-bridge/` 的新构建目标（新 npm script），100% 复用 React 组件 |
| 2 | Web 平台策略 | ✅ Web 端维持纯 React 集成；Compose feature 模块仅服务 Desktop + Android |
| 3 | iOS 优先级 | ✅ 不考虑 iOS，已从计划中移除所有 iOS 相关内容 |
| 4 | 图片上传 | ✅ MVP 仅支持外链图片 URL 写入；原生选择器上传留到下一阶段 |
| 5 | JS Bridge 验证 | ✅ 直接开发，遇到问题即时解决，不单独写 Demo |

---

## Phase 1 详细实施方案

> 以下是对 Phase 1 每一步的精确代码变更清单。执行顺序即为编号顺序。

### Step 1.0: 前置检查 — 理解项目 ColumnAdapter 机制

**SQLDelight 的 Enum 处理**：SQLDelight 运行时内置 `EnumColumnAdapter()`，自动处理 `enum.name` ↔ `TEXT` 列的转换。在 `ChatDatabase()` 构造函数中通过 `.Adapter()` 配置，无需手写 `TypeConverter` 中的 adapter。

**项目中已有大量用例**（`DatabaseModule.kt`）：
- `ContactEntity.Adapter(statusAdapter = EnumColumnAdapter())`
- `ConversationEntity.Adapter(typeAdapter = EnumColumnAdapter())`
- `MessageEntity.Adapter(categoryAdapter = EnumColumnAdapter(), render_typeAdapter = EnumColumnAdapter(), local_send_statusAdapter = EnumColumnAdapter())`
- `ParticipantEntity.Adapter(roleAdapter = EnumColumnAdapter())`
- `UserEntity.Adapter(roleAdapter = EnumColumnAdapter())`

**Article.sq 现有表定义**（已在 git staged，无查询）：

```sql
import com.github.woodsmarshes.chat.core.model.ArticleStats;
import com.github.woodsmarshes.chat.core.model.ArticleStatus;
import kotlin.time.Instant;
import kotlin.uuid.Uuid;
import kotlinx.serialization.json.JsonElement;

CREATE TABLE Article (
    id BLOB AS Uuid NOT NULL PRIMARY KEY,
    title TEXT NOT NULL,
    content TEXT AS JsonElement NOT NULL,
    author_id BLOB AS Uuid NOT NULL,
    status TEXT AS ArticleStatus NOT NULL,
    excerpt TEXT,
    created_at INTEGER AS Instant NOT NULL,
    updated_at INTEGER AS Instant NOT NULL,
    published_at INTEGER AS Instant,
    cover_image TEXT,
    deleted_at INTEGER AS Instant,
    slug TEXT,
    stats TEXT AS ArticleStats,
    FOREIGN KEY (author_id) REFERENCES UserEntity(id) ON DELETE RESTRICT
);
```

**Article 列所需的 adapter 来源**：

| 列 | 类型 | Adapter | 来源 |
|---|---|---|---|
| `id` | `BLOB AS Uuid` | `uuidAdapter` | `TypeConverter.kt` ✅ |
| `author_id` | `BLOB AS Uuid` | `uuidAdapter` | `TypeConverter.kt` ✅ |
| `content` | `TEXT AS JsonElement` | `jsonElementAdapter` | `TypeConverter.kt` ✅ |
| `status` | `TEXT AS ArticleStatus` | `EnumColumnAdapter()` | SQLDelight 内置 ✅ |
| `stats` | `TEXT AS ArticleStats` | `articleStatsAdapter` | `TypeConverter.kt` ✅ |
| `created_at` | `INTEGER AS Instant` | `instantAdapter` | `TypeConverter.kt` ✅ |
| `updated_at` | `INTEGER AS Instant` | `instantAdapter` | `TypeConverter.kt` ✅ |
| `published_at` | `INTEGER AS Instant` | `instantAdapter` | `TypeConverter.kt` ✅ |
| `deleted_at` | `INTEGER AS Instant` | `instantAdapter` | `TypeConverter.kt` ✅ |

**结论**：所有 adapter 都已存在，`TypeConverter.kt` 无需修改。唯一要做的是在 `ChatDatabase()` 构造函数中注册 `ArticleEntityAdapter`。

### Step 1.1: 在 ChatDatabase 构造函数中注册 ArticleEntityAdapter

**操作**：编辑 `DatabaseModule.kt`

**文件路径**：`core/database/src/commonMain/kotlin/com/github/woodsmarshes/chat/core/database/di/DatabaseModule.kt`

**变更 1**：在文件顶部 import 区添加：
```kotlin
import app.cash.sqldelight.EnumColumnAdapter
import com.github.woodsmarshes.chat.core.database.utils.articleStatsAdapter
import com.github.woodsmarshes.chat.core.database.utils.jsonElementAdapter
import io.github.woodsmarshes.chat.db.ArticleEntity
```
（`EnumColumnAdapter` 和 `ArticleEntity` 是新增；`jsonElementAdapter`、`articleStatsAdapter` 按需确认是否已在 import 中）

**变更 2**：在 `createDatabase()` 函数的 `ChatDatabase(...)` 构造参数中添加：
```kotlin
ArticleEntityAdapter = ArticleEntity.Adapter(
    idAdapter = uuidAdapter,
    contentAdapter = jsonElementAdapter,
    author_idAdapter = uuidAdapter,
    statusAdapter = EnumColumnAdapter(),
    created_atAdapter = instantAdapter,
    updated_atAdapter = instantAdapter,
    published_atAdapter = instantAdapter,
    deleted_atAdapter = instantAdapter,
    statsAdapter = articleStatsAdapter,
)
```

⚠️ **注意**：`ChatDatabase()` 构造参数需按表名的字母顺序排列。`ArticleEntity` 应在 `ContactEntity` 之前。

**验证方式**：`./gradlew :core:database:jvmMainClasses` 编译通过。

---

### Step 1.2: 编写 Article.sq 查询

**操作**：编辑 `Article.sq`，在 `CREATE TABLE` 语句下方追加查询。

**文件路径**：`core/database/src/commonMain/sqldelight/io/github/woodsmarshes/chat/db/Article.sq`

**新增内容**：

```sql
-- 插入或更新
upsertArticle:
INSERT OR REPLACE INTO Article(
    id, title, content, author_id, status, excerpt,
    created_at, updated_at, published_at, cover_image,
    deleted_at, slug, stats
) VALUES ?;

-- 按 ID 查询（排除软删除）
getArticleById:
SELECT * FROM Article WHERE id = ? AND deleted_at IS NULL;

-- 全部已发布文章列表（不含已删除）
listAllArticles:
SELECT * FROM Article
WHERE status = 'PUBLISHED' AND deleted_at IS NULL
ORDER BY published_at DESC
LIMIT :limit OFFSET :offset;

-- 某人全部文章列表
listArticlesByAuthor:
SELECT * FROM Article
WHERE author_id = ? AND deleted_at IS NULL
ORDER BY updated_at DESC
LIMIT :limit OFFSET :offset;

-- 某人过滤状态的文章列表
listArticlesByAuthorAndStatus:
SELECT * FROM Article
WHERE author_id = ? AND status = ? AND deleted_at IS NULL
ORDER BY updated_at DESC
LIMIT :limit OFFSET :offset;

-- 软删除
softDeleteArticle:
UPDATE Article SET deleted_at = ? WHERE id = ?;

-- 硬删除
hardDeleteArticle:
DELETE FROM Article WHERE id = ?;
```

**验证方式**：`./gradlew :core:database:generateCommonMainChatDatabaseInterface` 生成 Kotlin 代码，确认无错误。

---

### Step 1.3: 创建 ArticleDao 接口

**操作**：新建文件

**文件路径**：`core/database/src/commonMain/kotlin/com/github/woodsmarshes/chat/core/database/dao/ArticleDao.kt`

**完整内容**：

```kotlin
package com.github.woodsmarshes.chat.core.database.dao

import com.github.woodsmarshes.chat.core.model.ArticleStatus
import io.github.woodsmarshes.chat.db.ArticleEntity
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface ArticleDao {
    /** 插入或全量替换 */
    suspend fun upsert(article: ArticleEntity)

    /** 批量插入 */
    suspend fun upsertAll(articles: List<ArticleEntity>)

    /** 按 ID 查询单个（排除软删除） */
    fun getById(id: Uuid): Flow<ArticleEntity?>

    /** 已发布文章列表，分页 */
    fun listAll(offset: Long = 0, limit: Int = 50): Flow<List<ArticleEntity>>

    /** 某人全部文章列表（含草稿），分页 */
    fun listByAuthor(
        authorId: Uuid,
        offset: Long = 0,
        limit: Int = 50
    ): Flow<List<ArticleEntity>>

    /** 某人过滤状态的文章列表，分页 */
    fun listByAuthorAndStatus(
        authorId: Uuid,
        status: ArticleStatus,
        offset: Long = 0,
        limit: Int = 50
    ): Flow<List<ArticleEntity>>

    /** 软删除 */
    suspend fun softDelete(id: Uuid, deletedAt: Instant)

    /** 硬删除 */
    suspend fun hardDelete(id: Uuid)
}
```

**验证方式**：编译。

---

### Step 1.4: 创建 ArticleDaoImpl

**操作**：新建文件

**文件路径**：`core/database/src/commonMain/kotlin/com/github/woodsmarshes/chat/core/database/dao/ArticleDaoImpl.kt`

**完整内容**（参考 `ConversationDaoImpl` 模式）：

```kotlin
package com.github.woodsmarshes.chat.core.database.dao

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.github.woodsmarshes.chat.core.model.ArticleStatus
import io.github.woodsmarshes.chat.db.ArticleEntity
import io.github.woodsmarshes.chat.db.ChatDatabase
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.CoroutineContext
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ArticleDaoImpl(
    private val dbProvider: () -> ChatDatabase,
    private val ioContext: CoroutineContext,
) : ArticleDao {
    private val queries
        get() = dbProvider().articleQueries

    override suspend fun upsert(article: ArticleEntity) {
        queries.upsertArticle(article)
    }

    override suspend fun upsertAll(articles: List<ArticleEntity>) {
        if (articles.isEmpty()) return
        queries.transaction {
            articles.forEach { upsert(it) }
        }
    }

    override fun getById(id: Uuid): Flow<ArticleEntity?> {
        return queries.getArticleById(id)
            .asFlow()
            .mapToOneOrNull(ioContext)
    }

    override fun listAll(
        offset: Long,
        limit: Int
    ): Flow<List<ArticleEntity>> {
        return queries.listAllArticles(offset, limit)
            .asFlow()
            .mapToList(ioContext)
    }

    override fun listByAuthor(
        authorId: Uuid,
        offset: Long,
        limit: Int
    ): Flow<List<ArticleEntity>> {
        return queries.listArticlesByAuthor(authorId, offset, limit)
            .asFlow()
            .mapToList(ioContext)
    }

    override fun listByAuthorAndStatus(
        authorId: Uuid,
        status: ArticleStatus,
        offset: Long,
        limit: Int
    ): Flow<List<ArticleEntity>> {
        return queries.listArticlesByAuthorAndStatus(authorId, status, offset, limit)
            .asFlow()
            .mapToList(ioContext)
    }

    override suspend fun softDelete(id: Uuid, deletedAt: Instant) {
        queries.softDeleteArticle(deletedAt = deletedAt, id = id)
    }

    override suspend fun hardDelete(id: Uuid) {
        queries.hardDeleteArticle(id)
    }
}
```

**验证方式**：编译。

---

### Step 1.5: 在 DaosModule.kt 中注册 DI

**操作**：编辑文件

**文件路径**：`core/database/src/commonMain/kotlin/com/github/woodsmarshes/chat/core/database/di/DaosModule.kt`

**变更**：

1. 在文件顶部 import 区添加：
```kotlin
import com.github.woodsmarshes.chat.core.database.dao.ArticleDao
import com.github.woodsmarshes.chat.core.database.dao.ArticleDaoImpl
```

2. 在 `daosModule` 的 `module { ... }` 块中添加（例如放在最后一个 DAO 注册之后、闭括号之前）：
```kotlin
    single<ArticleDao> {
        ArticleDaoImpl(
            dbProvider = { get<DatabaseHolder>().getActiveDatabase() },
            ioContext = get<AppDispatchers>().io
        )
    }
```

**验证方式**：`./gradlew :core:database:jvmMainClasses` 编译通过。

---

### Step 1.6: 确认 SQLDelight 配置正确生成 Article 相关代码

**操作**：运行 Gradle 任务生成代码，确认 `ArticleEntity`、`articleQueries` 等符号存在。

**命令**：
```bash
./gradlew :core:database:generateCommonMainChatDatabaseInterface
```

**预期产出**：
- `ChatDatabase` 接口新增 `articleQueries` 属性
- `ArticleEntity` data class（已在表定义时生成）
- 各查询函数生成在 `ArticleQueries` 接口中

---

### Step 1.7: 最终编译验证

**命令**：
```bash
./gradlew :core:database:jvmMainClasses
```

**通过标准**：零错误、零警告。警告可选不要严格处理，但错误必须为零。

---

### Phase 1 完成标准

| 检查项 | 验证方法 |
|---|---|
| `ChatDatabase()` 构造函数含 `ArticleEntityAdapter` | `grep "ArticleEntityAdapter" DatabaseModule.kt` |
| `Article.sq` 含 6 条命名查询 | `grep -c "^[a-z]" Article.sq` → 7 (CREATE TABLE + 6 查询) |
| `ArticleDao.kt` 接口编译通过 | `./gradlew :core:database:jvmMainClasses` |
| `ArticleDaoImpl.kt` 编译通过 | 同上 |
| `DaosModule.kt` 含 `single<ArticleDao>` | `grep "ArticleDao" DaosModule.kt` |
| 生成代码含 `ArticleEntity` | `find build/ -name "ArticleEntity.kt"` |

---

### Phase 1 风险点

1. **`ArticleEntityAdapter` 在 `ChatDatabase()` 中的位置**：SQLDelight 生成的 `ChatDatabase` 构造函数参数顺序可能与 `ArticleEntity`→各表排序有关。若插入位置不对，Kotlin 编译器会报参数名不匹配——按编译错误信息调整顺序即可。

2. **`listArticlesByAuthorAndStatus` 查询参数**：SQLDelight 生成的 Kotlin 代码中，查询参数 `status` 会使用 `ArticleStatus` enum 类型（通过 `EnumColumnAdapter()` 与数据库的 `TEXT` 列互转）。与项目中其他带 enum 参数的查询（如 `listContactsByStatus`）模式一致，无特殊风险。

3. **SQLDelight 代码生成时机**：如果在执行 `:core:database:jvmMainClasses` 之前需要先运行 `generateCommonMainChatDatabaseInterface`，按 `Step 1.6 → 1.7` 的顺序即可。

---

如果以上 Phase 1 详细计划没问题，请确认，我立刻开始实施。
