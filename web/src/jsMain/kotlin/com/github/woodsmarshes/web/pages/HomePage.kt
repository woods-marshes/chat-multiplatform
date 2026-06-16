package com.github.woodsmarshes.web.pages

import com.github.woodsmarshes.chat.core.model.Article
import com.github.woodsmarshes.web.Router
import com.github.woodsmarshes.web.components.ArticleCard
import com.github.woodsmarshes.web.storage.ArticleRepository
import react.FC
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.p
import react.useEffectOnce
import react.useState
import web.cssom.ClassName

val HomePage = FC<Props> {
    var articles: List<Article> by useState(emptyList())
    var loading: Boolean by useState(true)

    useEffectOnce{
        val result = ArticleRepository.listAll()
        articles = result
            .sortedByDescending { it.updatedAt }
        loading = false
    }

    div {
        className = ClassName("toolbar")
        h2 { +"Articles" }
        div { className = ClassName("toolbar-spacer") }
        button {
            className = ClassName("btn btn-primary")
            onClick = { Router.navigate("/articles/new") }
            +"+ New Article"
        }
    }

    if (loading) {
        div {
            className = ClassName("loading-container")
            div { className = ClassName("loading-spinner")}
        }
    } else if (articles.isEmpty()) {
        div {
            className = ClassName("empty-state")
            h2 { +"No articles yet" }
            p { +"Start writing your first article." }
            button {
                className = ClassName("btn btn-primary")
                onClick = { Router.navigate("/articles/new") }
                +"Create Your First Article"
            }
        }
    } else {
        articles.forEach { article ->
            ArticleCard.invoke {
                this.article = article
            }
        }
    }
}
