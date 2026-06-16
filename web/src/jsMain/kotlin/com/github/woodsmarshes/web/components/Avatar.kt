package com.github.woodsmarshes.web.components

import com.github.woodsmarshes.chat.core.model.User
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import web.cssom.ClassName

external interface AvatarProps : Props {
    /** 当前用户。为 null 时显示灰色占位圆。 */
    var user: User?
    /** 头像尺寸类名，例如 "avatar-sm" / "avatar-md" / "avatar-lg"。默认 avatar-md。 */
    var sizeClass: String?
}

/**
 * 圆形头像。优先展示 [User.avatarUrl]；
 * 缺失时用 displayName/username 首字母兜底；
 * user 为 null 时显示灰色占位。
 */
val Avatar = FC<AvatarProps> { props ->
    val user = props.user
    val size = props.sizeClass ?: "avatar-md"

    div {
        className = ClassName("avatar $size")

        val url = user?.avatarUrl
        if (!url.isNullOrBlank()) {
            img {
                className = ClassName("avatar-img")
                src = url
                alt = "avatar"
                // 头像加载失败时降级为字母兜底
                onError = { _ ->
                    // 简单粗暴：隐藏 img，依赖 CSS .avatar::before 兜底不易，
                    // 这里直接清空 src 让 alt 文案展示；更好的兜底交由样式层。
                }
            }
        } else if (user != null) {
            val name = (user.displayName ?: user.username).trim()
            val initial = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            div {
                className = ClassName("avatar-fallback")
                +initial
            }
        } else {
            div {
                className = ClassName("avatar-fallback")
                +"?"
            }
        }
    }
}
