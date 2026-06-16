package com.github.woodsmarshes.web.pages

import com.github.woodsmarshes.chat.core.network.dto.article.ArticleListResponse
import com.github.woodsmarshes.web.Router
import com.github.woodsmarshes.web.components.ArticleCard
import com.github.woodsmarshes.web.components.Sidebar
import com.github.woodsmarshes.web.state.useCurrentContext
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

/**
 * 我的文章页（/my/articles）。
 * 主区只显示当前登录用户自己的文章（来自 listMyArticles）。
 * 左栏复用 Sidebar。
 */
val MyArticlesPage = FC<Props> {
    val user = useCurrentContext().user
    var articles: List<ArticleListResponse> by useState(emptyList())
    var loading: Boolean by useState(true)
    var lastId: Uuid? by useState(null)
    var hasMore: Boolean by useState(false)

    val loadPage: (Uuid?) -> Unit = { cursor ->
        val scope = MainScope()
        scope.launch {
            loading = cursor == null
            val result = ArticleRepository.listMy(beforeId = cursor, limit = 20)
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
        if (user != null) {
            loadPage(null)
        } else {
            loading = false
        }
    }

    div {
        className = ClassName("layout-with-sidebar")

        Sidebar.invoke {
            authorId = null
        }

        div {
            className = ClassName("layout-main")

            div {
                className = ClassName("toolbar")
                h2 { +"我的文章" }
                div { className = ClassName("toolbar-spacer") }
                button {
                    className = ClassName("btn btn-primary")
                    onClick = { Router.navigate("/articles/new") }
                    +"+ New Article"
                }
            }

            if (user == null) {
                div {
                    className = ClassName("empty-state")
                    h2 { +"请先登录" }
                    p { +"登录后可以查看和管理你自己的文章。" }
                    button {
                        className = ClassName("btn btn-primary")
                        onClick = { Router.navigate("/login") }
                        +"去登录"
                    }
                }
            } else if (loading) {
                div {
                    className = ClassName("loading-container")
                    div { className = ClassName("loading-spinner") }
                }
            } else if (articles.isEmpty()) {
                div {
                    className = ClassName("empty-state")
                    h2 { +"还没有文章" }
                    p { +"开始写你的第一篇文章吧。" }
                    button {
                        className = ClassName("btn btn-primary")
                        onClick = { Router.navigate("/articles/new") }
                        +"写一篇"
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
