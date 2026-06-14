package com.github.woodsmarshes.web.components

import com.github.woodsmarshes.chat.core.model.Article
import com.github.woodsmarshes.chat.core.model.ArticleStatus
import com.github.woodsmarshes.web.Router
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import react.FC
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import web.cssom.ClassName

external interface ArticleCardProps : Props {
    var article: Article
}

val ArticleCard = FC<ArticleCardProps> { props ->
    val article = props.article

    a {
        href = "#/articles/${article.id}"
        className = ClassName("article-card")
        onClick = { event ->
            event.preventDefault()
            Router.navigate("/articles/${article.id}")
        }

        div {
            className = ClassName("article-card-title")
            +article.title
        }

        if (!article.excerpt.isNullOrBlank()) {
            div {
                className = ClassName("article-card-excerpt")
                +article.excerpt!!
            }
        }

        div {
            className = ClassName("article-card-meta")

            span {
                className = when (article.status) {
                    ArticleStatus.DRAFT -> ClassName("badge badge-draft")
                    ArticleStatus.PUBLISHED -> ClassName("badge badge-published")
                }
                +(if (article.status == ArticleStatus.DRAFT) "Draft" else "Published")
            }
            span { +(article.author.displayName ?: article.author.username) }
            span { +formatTimestamp(article.updatedAt) }
        }
    }
}

private fun formatTimestamp(instant: kotlin.time.Instant): String {
    return try {
        // 🟢 kotlinx-datetime 在最新版本中已能原生直接处理 kotlin.time.Instant，省去繁琐转换
        val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        "${dt.year}-${dt.month.number.toString().padStart(2, '0')}-${dt.day.toString().padStart(2, '0')}"
    } catch (e: Exception) {
        ""
    }
}
