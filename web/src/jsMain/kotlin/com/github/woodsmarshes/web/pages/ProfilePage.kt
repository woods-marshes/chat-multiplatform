package com.github.woodsmarshes.web.pages

import com.github.woodsmarshes.web.Router
import com.github.woodsmarshes.web.components.Avatar
import com.github.woodsmarshes.web.state.useCurrentContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import react.FC
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.p
import web.cssom.ClassName

/**
 * 个人资料页（/me）。只读展示当前登录用户信息。
 */
val ProfilePage = FC<Props> {
    val user = useCurrentContext().user

    div {
        className = ClassName("profile-page")

        if (user == null) {
            div {
                className = ClassName("empty-state")
                h1 { +"请先登录" }
                p { +"登录后可以查看你的个人资料。" }
                button {
                    className = ClassName("btn btn-primary")
                    onClick = { Router.navigate("/login") }
                    +"去登录"
                }
            }
        } else {
            div {
                className = ClassName("profile-card")

                Avatar.invoke {
                    this.user = user
                    this.sizeClass = "avatar-xl"
                }

                h1 {
                    className = ClassName("profile-name")
                    +(user.displayName ?: user.username)
                }

                div {
                    className = ClassName("profile-username")
                    +("@${user.username}")
                }

                div {
                    className = ClassName("profile-field")
                    div {
                        className = ClassName("profile-field-label")
                        +"邮箱"
                    }
                    div {
                        className = ClassName("profile-field-value")
                        +(user.email ?: "—")
                    }
                }

                div {
                    className = ClassName("profile-field")
                    div {
                        className = ClassName("profile-field-label")
                        +"简介"
                    }
                    div {
                        className = ClassName("profile-field-value")
                        +(if (user.bio.isNullOrBlank()) "—" else user.bio!!)
                    }
                }

                div {
                    className = ClassName("profile-field")
                    div {
                        className = ClassName("profile-field-label")
                        +"加入时间"
                    }
                    div {
                        className = ClassName("profile-field-value")
                        +formatProfileTimestamp(user.createdAt)
                    }
                }

                div {
                    className = ClassName("profile-field")
                    div {
                        className = ClassName("profile-field-label")
                        +"角色"
                    }
                    div {
                        className = ClassName("profile-field-value")
                        +user.role.name
                    }
                }
            }
        }
    }
}

private fun formatProfileTimestamp(instant: kotlin.time.Instant): String = try {
    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    "${dt.year}-${dt.month.number.toString().padStart(2, '0')}-${dt.day.toString().padStart(2, '0')}"
} catch (e: Exception) {
    "—"
}
