package com.github.woodsmarshes.web.components

import com.github.woodsmarshes.chat.core.model.ArticleStatus
import com.github.woodsmarshes.chat.core.network.dto.article.ArticleListResponse
import com.github.woodsmarshes.web.Router
import com.github.woodsmarshes.web.state.useCurrentContext
import com.github.woodsmarshes.web.storage.ArticleRepository
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid
import react.FC
import react.Key
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h3
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import react.useEffectOnce
import react.useState
import web.cssom.ClassName

external interface SidebarProps : Props {
    /** null → shows current user's own articles; non-null → shows this author's articles. */
    var authorId: Uuid?
}

/**
 * Left sidebar:
 * - If [authorId] is set → list articles by that author.
 * - Otherwise, when logged in → list the current user's own articles.
 * - Not logged in and no authorId → show login prompt.
 */
val Sidebar = FC<SidebarProps> { props ->
    val user = useCurrentContext().user
    val loggedIn = useCurrentContext().isLoggedIn

    var articles: List<ArticleListResponse> by useState(emptyList())
    var loading: Boolean by useState(true)
    var lastId: Uuid? by useState(null)
    var hasMore: Boolean by useState(false)

    val loadPage: (Uuid?) -> Unit = { cursor ->
        val scope = MainScope()
        scope.launch {
            loading = true
            val result = if (props.authorId != null) {
                ArticleRepository.listByAuthor(
                    authorId = props.authorId!!,
                    beforeId = cursor,
                    limit = 10,
                )
            } else if (loggedIn) {
                ArticleRepository.listMy(
                    beforeId = cursor,
                    limit = 10,
                )
            } else {
                emptyList()
            }
            if (cursor == null) {
                articles = result
            } else {
                articles = articles + result
            }
            lastId = result.lastOrNull()?.id
            hasMore = result.size >= 10
            loading = false
        }
    }

    useEffectOnce {
        if (props.authorId != null || loggedIn) {
            loadPage(null)
        } else {
            loading = false
        }
    }

    val sidebarTitle = if (props.authorId != null) "作者的其他文章" else "我的文章"

    div {
        className = ClassName("sidebar")

        if (user == null && props.authorId == null) {
            // 未登录且无指定作者 — 登录卡片
            div {
                className = ClassName("sidebar-not-logged-in")
                p { +"未登录" }
                p {
                    className = ClassName("sidebar-not-logged-in-hint")
                    +"登录后可以查看你的文章、资料和设置。"
                }
                button {
                    className = ClassName("btn btn-primary btn-sm")
                    onClick = { Router.navigate("/login") }
                    +"去登录"
                }
            }
        } else {
            // 用户卡片（仅登录 + 无指定作者时展示自己的信息）
            if (props.authorId == null && user != null) {
                div {
                    className = ClassName("sidebar-user-card")
                    Avatar.invoke {
                        this.user = user!!
                        this.sizeClass = "avatar-lg"
                    }
                    div {
                        className = ClassName("sidebar-user-info")
                        div {
                            className = ClassName("sidebar-user-name")
                            +(user!!.displayName ?: user!!.username)
                        }
                        div {
                            className = ClassName("sidebar-user-username")
                            +("@${user!!.username}")
                        }
                        if (!user!!.bio.isNullOrBlank()) {
                            div {
                                className = ClassName("sidebar-user-bio")
                                +user!!.bio!!
                            }
                        }
                    }
                }
            }

            // 文章列表
            div {
                className = ClassName("sidebar-my-articles")
                h3 { +sidebarTitle }
                if (loading && articles.isEmpty()) {
                    div {
                        className = ClassName("sidebar-loading")
                        +"加载中..."
                    }
                } else if (articles.isEmpty()) {
                    p {
                        className = ClassName("sidebar-empty")
                        +"还没有文章"
                    }
                } else {
                    articles.forEach { article ->
                        a {
                            key = Key(article.id.toString())
                            href = "#/articles/${article.id}"
                            className = ClassName("sidebar-article-item")
                            onClick = { event ->
                                event.preventDefault()
                                Router.navigate("/articles/${article.id}")
                            }
                            div {
                                className = ClassName("sidebar-article-title")
                                +article.title
                            }
                            div {
                                className = ClassName("sidebar-article-meta")
                                span {
                                    className = when (article.status) {
                                        ArticleStatus.DRAFT -> ClassName("badge badge-draft")
                                        ArticleStatus.PUBLISHED -> ClassName("badge badge-published")
                                    }
                                    +(if (article.status == ArticleStatus.DRAFT) "草稿" else "已发布")
                                }
                                span { +formatSidebarTimestamp(article.updatedAt) }
                            }
                        }
                    }

                    if (hasMore) {
                        a {
                            className = ClassName("sidebar-load-more")
                            onClick = { event ->
                                event.preventDefault()
                                loadPage(lastId)
                            }
                            +"加载更多"
                        }
                    }
                }
            }
        }
    }
}

private fun formatSidebarTimestamp(instant: kotlin.time.Instant): String = try {
    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    "${dt.year}-${dt.month.number.toString().padStart(2, '0')}-${dt.day.toString().padStart(2, '0')}"
} catch (e: Exception) {
    ""
}
