package com.github.woodsmarshes.web.pages

import com.github.woodsmarshes.chat.core.model.Article
import com.github.woodsmarshes.chat.core.model.ArticleStatus
import com.github.woodsmarshes.web.Router
import com.github.woodsmarshes.web.components.ProseMirrorRenderer
import com.github.woodsmarshes.web.storage.ArticleRepository
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import react.FC
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import kotlinx.datetime.number
import react.useEffectOnce
import react.useState
import web.cssom.ClassName

val ArticleViewPage = FC<Props> {
    val path = Router.currentPath()
    val idPart = path.removePrefix("/articles/").removeSuffix("/edit")
    val articleId = idPart.ifBlank { null }

    var article: Article? by useState(null)
    var loading: Boolean by useState(true)

    useEffectOnce {
        if (articleId != null) {
            article = try {
                ArticleRepository.getById(Uuid.parse(articleId))
            } catch (e: Exception) {
                null
            }
        }
        loading = false
    }

    div {
        className = ClassName("article-view")

        if (loading) {
            div {
                className = ClassName("loading-container")
                div { className = ClassName("loading-spinner") }
            }
        } else if (article == null) {
            div {
                className = ClassName("error-message")
                h1 { +"Article Not Found" }
                p { +"The article you're looking for doesn't exist or has been deleted." }
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

            div {
                className = ClassName("toolbar")
                div { className = ClassName("toolbar-spacer") }
                button {
                    className = ClassName("btn")
                    onClick = { Router.navigate("/articles/${a.id}/edit") }
                    +"Edit"
                }
            }

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
            }

            ProseMirrorRenderer.invoke {
                content = a.content
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
