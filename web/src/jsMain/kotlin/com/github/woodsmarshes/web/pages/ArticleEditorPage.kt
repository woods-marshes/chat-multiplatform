package com.github.woodsmarshes.web.pages

import com.github.woodsmarshes.chat.core.datastore.AuthTokenDataSource
import com.github.woodsmarshes.chat.core.model.Article
import com.github.woodsmarshes.chat.core.model.ArticleStatus
import com.github.woodsmarshes.chat.core.network.api.rest.ArticleApi
import com.github.woodsmarshes.chat.core.network.dto.article.UpdateArticleRequest
import com.github.woodsmarshes.chat.core.network.serialization.ProjectJson
import com.github.woodsmarshes.web.Router
import com.github.woodsmarshes.web.storage.ArticleRepository
import com.github.woodsmarshes.web.koinInject
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
    val (loggedIn, setLoggedIn) = useState(false)

    useEffectOnce {
        koinInject<AuthTokenDataSource>().authToken.collect { token ->
            setLoggedIn(token.jwtToken != null)
        }
    }


    val (title, setTitle) = useState("")
    val (loading, setLoading) = useState(isEditing)
    val (error, setError) = useState<String?>(null)
    val (existingArticle, setExistingArticle) = useState<Article?>(null)

    // 核心状态：用一个 JS dynamic 变量存储编辑器的 JSON 内容数据流
    val (editorJson, setEditorJson) = useState<dynamic>(null)

    useEffectOnce {
        if (isEditing) {
            try {
                val article = ArticleRepository.getById(Uuid.parse(rawId))
                if (article != null) {
                    setTitle(article.title)
                    setExistingArticle(article)

                    // 将旧文章的 JsonElement 转化为 JS 识别的动态 JSON 树
                    val serializedStr = ProjectJson.encodeToString(JsonElement.serializer(), article.content)
                    val jsJson = JSON.parse<dynamic>(serializedStr)
                    setEditorJson(jsJson)
                } else {
                    setError("Article not found")
                }
            } catch (e: Exception) {
                setError("Invalid article ID")
            }
            setLoading(false)
        }
    }

    val handleSave = { newStatus: ArticleStatus ->
        val scope = MainScope()
        scope.launch {
            // 保存时，将动态 JS 运行时的 json 树安全转换为 Kotlin 类型的 JsonElement
            val contentElement: JsonElement = if (editorJson != null) {
                val stringified = JSON.stringify(editorJson)
                ProjectJson.parseToJsonElement(stringified)
            } else {
                existingArticle?.content ?: ProjectJson.parseToJsonElement("{}")
            }

            val idToUse = if (isEditing && existingArticle != null) {
                existingArticle.id
            } else {
                Uuid.generateV7()
            }

            val saved = ArticleRepository.save(
                id = idToUse,
                request = UpdateArticleRequest(
                    title = title.ifBlank { "Untitled" },
                    content = contentElement,
                    excerpt = null,
                    status = newStatus,
                )
            )
            Router.navigate("/articles/${saved.id}")
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
            if (!isEditing || editorJson != null) {
                TiptapEditorBridge {
                    this.title = title
                    this.onTitleChange = { newTitle -> setTitle(newTitle) }
                    this.content = editorJson
                    this.onChange = { newJson ->
                        setEditorJson(newJson) // 实时捕获编辑器变更
                    }
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
                        +"Delete"
                    }
                }

                button {
                    className = ClassName("btn")
                    onClick = { handleSave(ArticleStatus.DRAFT) }
                    +"Save Draft"
                }

                button {
                    className = ClassName("btn btn-primary")
                    onClick = { handleSave(ArticleStatus.PUBLISHED) }
                    +"Publish"
                }
            }
        }
    }
}