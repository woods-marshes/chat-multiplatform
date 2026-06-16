package com.github.woodsmarshes.web.pages

import com.github.woodsmarshes.chat.core.network.dto.article.ArticleListResponse
import com.github.woodsmarshes.web.Router
import com.github.woodsmarshes.web.components.ArticleCard
import com.github.woodsmarshes.web.components.Sidebar
import com.github.woodsmarshes.web.storage.ArticleRepository
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid
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
    var articles: List<ArticleListResponse> by useState(emptyList())
    var loading: Boolean by useState(true)
    var lastId: Uuid? by useState(null)
    var hasMore: Boolean by useState(false)

    val loadPage: (Uuid?) -> Unit = { cursor ->
        val scope = MainScope()
        scope.launch {
            loading = cursor == null
            val result = ArticleRepository.listAll(beforeId = cursor, limit = 20)
            if (cursor == null) {
                articles = result.sortedByDescending { it.updatedAt }
            } else {
                articles = articles + result.sortedByDescending { it.updatedAt }
            }
            lastId = articles.lastOrNull()?.id
            hasMore = result.size >= 20
            loading = false
        }
    }

    useEffectOnce {
        loadPage(null)
    }

    // 双栏布局：左侧边栏 + 右主区
    div {
        className = ClassName("layout-with-sidebar")

        Sidebar.invoke {
            authorId = null
        }

        div {
            className = ClassName("layout-main")

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
                    div { className = ClassName("loading-spinner") }
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
                if (hasMore) {
                    button {
                        className = ClassName("btn load-more")
                        onClick = { loadPage(lastId) }
                        +"加载更多"
                    }
                }
            }
        }
    }
}
