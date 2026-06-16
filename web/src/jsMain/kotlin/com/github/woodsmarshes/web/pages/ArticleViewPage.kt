package com.github.woodsmarshes.web.pages

import com.github.woodsmarshes.chat.core.model.Article
import com.github.woodsmarshes.chat.core.model.ArticleStatus
import com.github.woodsmarshes.web.Router
import com.github.woodsmarshes.web.components.ArticleContentRenderer
import com.github.woodsmarshes.web.components.Sidebar
import com.github.woodsmarshes.web.state.useCurrentContext
import com.github.woodsmarshes.web.storage.ArticleRepository
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import react.FC
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import react.useEffectOnce
import react.useState
import web.cssom.ClassName

val ArticleViewPage = FC<Props> {
    val path = Router.currentPath()
    val idPart = path.removePrefix("/articles/").removeSuffix("/edit")
    val articleId = idPart.ifBlank { null }

    val currentUser = useCurrentContext().user

    var article: Article? by useState(null)
    var loading: Boolean by useState(true)
    var isOwn: Boolean by useState(false)

    useEffectOnce {
        if (articleId != null) {
            val id = try { Uuid.parse(articleId) } catch (e: Exception) { null }
            if (id != null) {
                // Try own-article API first (most useful for drafts + edit access)
                val own = ArticleRepository.getMy(id)
                if (own != null) {
                    article = own
                    isOwn = true
                } else {
                    // Fall back to public API
                    article = ArticleRepository.getById(id)
                    isOwn = false
                }
            }
        }
        loading = false
    }

    // 双栏布局：左栏显示作者文章列表，右栏显示文章正文
    div {
        className = ClassName("layout-with-sidebar")

        Sidebar.invoke {
            authorId = article?.author?.id
        }

        div {
            className = ClassName("layout-main article-view")

            if (loading) {
                div {
                    className = ClassName("loading-container")
                    div { className = ClassName("loading-spinner") }
                }
            } else if (article == null) {
                div {
                    className = ClassName("error-message")
                    h1 { +"Article Not Found" }
                    p {
                        className = ClassName("auth-text")
                        +"The article you're looking for doesn't exist or has been deleted."
                    }
                    a {
                        href = "#/"
                        className = ClassName("btn")
                        onClick = { event: dynamic ->
                            event.preventDefault()
                            Router.navigate("/")
                        }
                        +"Back to Articles"
                    }
                }
            } else {
                val a = article!!

                h1 { +a.title }

                div {
                    className = ClassName("article-meta")
                    span {
                        className = when (a.status) {
                            ArticleStatus.DRAFT -> ClassName("badge badge-draft")
                            ArticleStatus.PUBLISHED -> ClassName("badge badge-published")
                        }
                        +(if (a.status == ArticleStatus.DRAFT) "Draft" else "Published")
                    }
                    span { +(a.author.displayName ?: a.author.username) }
                    span { +formatTimestamp(a.updatedAt) }
                    if (isOwn) {
                        a {
                            href = "#/articles/${a.id}/edit"
                            className = ClassName("article-edit-link")
                            onClick = { event ->
                                event.preventDefault()
                                Router.navigate("/articles/${a.id}/edit")
                            }
                            +"Edit"
                        }
                    }
                }

                ArticleContentRenderer.invoke {
                    content = a.content
                }
            }
        }
    }
}

private fun formatTimestamp(instant: Instant): String {
    return try {
        val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        "${dt.year}-${dt.month.number.toString().padStart(2, '0')}-${dt.day.toString().padStart(2, '0')}"
    } catch (e: Exception) {
        ""
    }
}
