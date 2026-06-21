package com.github.woodsmarshes.web.pages

import com.github.woodsmarshes.chat.core.datastore.AuthTokenDataSource
import com.github.woodsmarshes.chat.core.model.Article
import com.github.woodsmarshes.chat.core.model.ArticleStatus
import com.github.woodsmarshes.chat.core.network.api.rest.ArticleApi
import com.github.woodsmarshes.chat.core.network.dto.article.UpdateArticleRequest
import com.github.woodsmarshes.chat.core.network.ktor.NetworkConfig
import com.github.woodsmarshes.chat.core.network.serialization.ProjectJson
import com.github.woodsmarshes.web.Router
import com.github.woodsmarshes.web.storage.ArticleRepository
import com.github.woodsmarshes.web.koinInject
import com.github.woodsmarshes.web.state.useCurrentContext
import com.github.woodsmarshes.web.wrapper.tiptap.TiptapEditorBridge
import kotlin.uuid.Uuid
import react.FC
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.p
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import react.useEffect
import react.useEffectOnce
import react.useState
import web.cssom.ClassName

val ArticleEditorPage = FC<Props> {
    val path = Router.currentPath()
    val rawId: String? = if (path.endsWith("/edit")) {
        path.removePrefix("/articles/").removeSuffix("/edit").ifEmpty { null }
    } else {
        null
    }
    val isEditing = rawId != null
    val contextStatus = useCurrentContext()
    val loggedIn = contextStatus.isLoggedIn
    val currentUser = contextStatus.user
    val jwtToken = contextStatus.jwtToken

    val (title, setTitle) = useState("")
    val (loading, setLoading) = useState(true)
    val (error, setError) = useState<String?>(null)
    val (existingArticle, setExistingArticle) = useState<Article?>(null)

    // 核心状态：用一个 JS dynamic 变量存储编辑器的 JSON 内容数据流
    val (editorJson, setEditorJson) = useState<dynamic>(null)

    val networkConfig = koinInject<NetworkConfig>()

    val (canSave, setCanSave) = useState(false)
    val (isSaving, setIsSaving) = useState(false)

    useEffect(loggedIn) {
        if (!loggedIn) {
            setLoading(false)
            return@useEffect // 如果未登录，直接拦截展示“Login Required”，不做任何数据操作
        }

        launch {
            try {
                if (isEditing) {
                    // 编辑已有文章，正常拉取内容
                    val article = ArticleRepository.getById(Uuid.parse(rawId!!))
                    if (article != null) {
                        setTitle(article.title)
                        setExistingArticle(article)

                        val serializedStr = ProjectJson.encodeToString(JsonElement.serializer(), article.content)
                        val jsJson = JSON.parse<dynamic>(serializedStr)
                        setEditorJson(jsJson)
                        setCanSave(article.author.id == currentUser?.id)
                    } else {
                        setError("Article not found")
                    }
                    setLoading(false)
                } else {
                    println("=== DEBUG: to post  ===")
                    // 新建文章 (进入 /articles/new)，且登录校验已通过。
                    val blankArticle = ArticleRepository.createBlank()
                    Router.navigate("/articles/${blankArticle.id}/edit")
                }
            } catch (e: Exception) {
                setError("Failed to initialize editor: ${e.message}")
                setLoading(false)
            }
        }
    }

    val handleSave = { newStatus: ArticleStatus ->
        val scope = MainScope()
        scope.launch {
            setIsSaving(true)
            try {
                // 保存时，将动态 JS 运行时的 json 树安全转换为 Kotlin 类型的 JsonElement
                val contentElement: JsonElement = if (editorJson != null) {
                    val stringified = JSON.stringify(editorJson)
                    ProjectJson.parseToJsonElement(stringified)
                } else {
                    existingArticle?.content ?: ProjectJson.parseToJsonElement("{}")
                }

                val saved = ArticleRepository.save(
                    id = existingArticle?.id ?: Uuid.parse(rawId!!),
                    request = UpdateArticleRequest(
                        title = title.ifBlank { "Untitled" },
                        content = contentElement,
                        excerpt = null,
                        status = newStatus,
                    )
                )
                Router.navigate("/articles/${saved.id}")
            } catch (e: Exception) {
                setError("Failed to save article: ${e.message}")
            } finally {
                setIsSaving(false)
            }
        }
    }

    if (!loggedIn) {
        div {
            className = ClassName("auth-page")
            div {
                className = ClassName("auth-card")
                h1 { className = ClassName("auth-title"); +"Login Required" }
                p { +"You need to log in to create or edit articles." }
                a {
                    href = "#/login"
                    className = ClassName("btn btn-primary auth-submit")
                    +"Go to Login"
                }
            }
        }
    } else if (loading) {
        div {
            className = ClassName("loading-container")
            div { className = ClassName("loading-spinner") }
        }
    } else if (error != null) {
        div {
            className = ClassName("error-message")
            h1 { +"Error" }
            p { +(error ?: "") }
            a {
                href = "#/"
                className = ClassName("btn")
                onClick = { event -> event.preventDefault(); Router.navigate("/") }
                +"Back to Articles"
            }
        }
    } else {
        div {
            className = ClassName("editor-page")

            // 新建文章（editorJson为null）或者编辑文章（且editorJson已在useEffect中异步加载完）时挂载
            if (!isEditing || (editorJson != null && jwtToken != null)) {
                TiptapEditorBridge {
                    this.title = title
                    this.onTitleChange = { newTitle -> setTitle(newTitle) }
                    this.content = editorJson
                    this.onChange = { newJson ->
                        setEditorJson(newJson) // 实时捕获编辑器变更
                    }
                    this.collabUrl = resolveCollabUrl(networkConfig)
                    this.roomId = rawId
                    this.token = jwtToken

                    val userInfoObj = js("{}")
                    userInfoObj.name = currentUser?.displayName ?: currentUser?.username ?: "Anonymous"
                    userInfoObj.color = getHashColor(currentUser?.id?.toString() ?: "anonymous")
                    this.userInfo = userInfoObj
                }
            }

            // 底部操作栏
            div {
                className = ClassName("editor-actions")

                button {
                    className = ClassName("btn")
                    onClick = { Router.back() }
                    +"Back"
                }

                div { className = ClassName("toolbar-spacer") }

                if (isEditing) {
                    button {
                        className = ClassName("btn btn-danger")
                        onClick = {
                            if (existingArticle != null) {
                                val delScope = MainScope()
                                delScope.launch {
                                    ArticleRepository.delete(existingArticle.id)
                                    Router.navigate("/")
                                }
                            }
                        }
                        disabled = !canSave || isSaving
                        +"Delete"
                    }
                }

                button {
                    className = ClassName("btn")
                    onClick = { handleSave(ArticleStatus.DRAFT) }
                    disabled = !canSave || isSaving
                    +"Save Draft"
                }

                button {
                    className = ClassName("btn btn-primary")
                    onClick = { handleSave(ArticleStatus.PUBLISHED) }
                    disabled = !canSave || isSaving
                    +"Publish"
                }
            }
        }
    }
}

private fun resolveCollabUrl(networkConfig: NetworkConfig): String {
    val host = networkConfig.host
    val isLocal = host == "localhost" || host == "127.0.0.1"
    val wsProtocol = if (networkConfig.useTls) "wss" else "ws"

    return if (isLocal) {
        "ws://127.0.0.1:1234"
    } else {
        val portStr = if (networkConfig.port == 80 || networkConfig.port == 443) "" else ":${networkConfig.port}"
        "$wsProtocol://$host$portStr/collab"
    }
}

private fun getHashColor(seed: String): String {
    val colors = listOf(
        "#f87171", "#fb923c", "#fbbf24", "#34d399",
        "#60a5fa", "#818cf8", "#a78bfa", "#f472b6"
    )
    val hash = seed.hashCode()
    val index = kotlin.math.abs(hash) % colors.size
    return colors[index]
}