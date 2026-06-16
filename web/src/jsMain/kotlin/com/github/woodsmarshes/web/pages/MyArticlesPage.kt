package com.github.woodsmarshes.web.pages

import com.github.woodsmarshes.chat.core.network.dto.article.ArticleListResponse
import com.github.woodsmarshes.web.Router
import com.github.woodsmarshes.web.components.ArticleCard
import com.github.woodsmarshes.web.components.Sidebar
import com.github.woodsmarshes.web.state.useCurrentContext
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

/**
 * 我的文章页（/my/articles）。
 * 主区只显示当前登录用户自己的文章（来自 listMyArticles）。
 * 左栏复用 Sidebar。
 */
val MyArticlesPage = FC<Props> {
    val user = useCurrentContext().user
    var articles: List<ArticleListResponse> by useState(emptyList())
    var loading: Boolean by useState(true)

    useEffectOnce {
        if (user != null) {
            articles = try {
                ArticleRepository.listMy().sortedByDescending { it.updatedAt }
            } catch (e: Exception) {
                emptyList()
            }
        }
        loading = false
    }

    div {
        className = ClassName("layout-with-sidebar")

        Sidebar.invoke()

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
                // 未登录提示
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
            }
        }
    }
}
