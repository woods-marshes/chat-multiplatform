package com.github.woodsmarshes.web.components

import com.github.woodsmarshes.chat.core.model.ArticleStatus
import com.github.woodsmarshes.chat.core.network.dto.article.ArticleListResponse
import com.github.woodsmarshes.web.Router
import com.github.woodsmarshes.web.state.useCurrentContext
import com.github.woodsmarshes.web.storage.ArticleRepository
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
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

/**
 * 复用左栏：
 * - 未登录：提示未登录 + 跳转登录按钮。
 * - 已登录：顶部用户卡片（头像、名字、简介）+ 我的文章列表（点击跳转到文章查看页）。
 *
 * 内部通过 [useCurrentContext] 读取用户，并各自加载一次我的文章。
 */
val Sidebar = FC<Props> {
    val user = useCurrentContext().user
    val loggedIn = useCurrentContext().isLoggedIn
    var articles: List<ArticleListResponse> by useState(emptyList())
    var loading: Boolean by useState(true)

    useEffectOnce {
        // 只有登录态才去拉取我的文章，避免 401 噪音
        if (loggedIn) {
            articles = try {
                ArticleRepository.listMy().sortedByDescending { it.updatedAt }
            } catch (e: Exception) {
                emptyList()
            }
        }
        loading = false
    }

    div {
        className = ClassName("sidebar")

        if (user == null) {
            // 未登录卡片
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
            // 用户卡片
            div {
                className = ClassName("sidebar-user-card")
                Avatar.invoke {
                    this.user = user
                    this.sizeClass = "avatar-lg"
                }
                div {
                    className = ClassName("sidebar-user-info")
                    div {
                        className = ClassName("sidebar-user-name")
                        +(user.displayName ?: user.username)
                    }
                    div {
                        className = ClassName("sidebar-user-username")
                        +("@${user.username}")
                    }
                    if (!user.bio.isNullOrBlank()) {
                        div {
                            className = ClassName("sidebar-user-bio")
                            +user.bio!!
                        }
                    }
                }
            }

            // 我的文章列表
            div {
                className = ClassName("sidebar-my-articles")
                h3 { +"我的文章" }
                if (loading) {
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
