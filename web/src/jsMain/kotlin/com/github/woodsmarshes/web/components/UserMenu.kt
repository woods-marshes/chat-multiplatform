package com.github.woodsmarshes.web.components

import com.github.woodsmarshes.chat.core.datastore.AuthTokenDataSource
import com.github.woodsmarshes.chat.core.datastore.UserSettingDataSource
import com.github.woodsmarshes.web.Router
import com.github.woodsmarshes.web.koinInject
import com.github.woodsmarshes.web.state.useCurrentContext
import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.promise
import react.FC
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.useEffect
import react.useState
import web.cssom.ClassName

val UserMenu = FC<Props> {
    val user = useCurrentContext().user
    val (open, setOpen) = useState(false)
    val (loggingOut, setLoggingOut) = useState(false)

    // 点击菜单外部时关闭下拉。
    // 新版 useEffect 把回调放在 suspend CoroutineScope 里，卸载/依赖变化时该 scope
    // 会被取消，因此用 awaitCancellation() + try/finally 来挂载与清理副作用。
    useEffect(open) {
        if (!open) return@useEffect

        val handler: (org.w3c.dom.events.Event) -> Unit = { event ->
            val target = event.target
            if (target is org.w3c.dom.HTMLElement && target.closest(".user-menu") == null) {
                setOpen(false)
            }
        }
        document.addEventListener("click", handler)
        try {
            awaitCancellation()
        } finally {
            document.removeEventListener("click", handler)
        }
    }

    if (user == null) {
        // 未登录：直接展示登录按钮
        button {
            className = ClassName("btn btn-primary btn-sm user-menu-login")
            onClick = { Router.navigate("/login") }
            +"登录"
        }
        return@FC
    }

    val handleLogout = {
        if (!loggingOut) {
            setLoggingOut(true)
            setOpen(false)
            koinInject<CoroutineScope>().promise {
                try {
                    koinInject<AuthTokenDataSource>().clearAuthToken()
                    koinInject<UserSettingDataSource>().clearUserSetting()
                } catch (e: Exception) {
                    console.log("Logout failed: ${e.message}")
                }
                // UserProvider 订阅了 Flow，会自动更新为未登录态
                Router.navigate("/login")
                setLoggingOut(false)
            }
        }
    }

    div {
        className = ClassName("user-menu")

        // 触发器：头像 + 名字
        button {
            className = ClassName("user-menu-trigger")
            onClick = { event ->
                event.stopPropagation()
                setOpen(!open)
            }
            Avatar.invoke {
                this.user = user
                this.sizeClass = "avatar-sm"
            }
            span {
                className = ClassName("user-menu-name")
                +(user.displayName ?: user.username)
            }
        }

        if (open) {
            div {
                className = ClassName("user-menu-dropdown")

                // Profile
                a {
                    href = "#/me"
                    className = ClassName("user-menu-item")
                    onClick = { event ->
                        event.preventDefault()
                        setOpen(false)
                        Router.navigate("/me")
                    }
                    +"Profile"
                }

                // 我的文章
                a {
                    href = "#/my/articles"
                    className = ClassName("user-menu-item")
                    onClick = { event ->
                        event.preventDefault()
                        setOpen(false)
                        Router.navigate("/my/articles")
                    }
                    +"文章"
                }

                // 设置
                a {
                    href = "#/settings"
                    className = ClassName("user-menu-item")
                    onClick = { event ->
                        event.preventDefault()
                        setOpen(false)
                        Router.navigate("/settings")
                    }
                    +"设置"
                }

                div { className = ClassName("user-menu-divider") }

                // 退出登录
                a {
                    href = "#"
                    className = ClassName("user-menu-item user-menu-item-danger")
                    onClick = { event ->
                        event.preventDefault()
                        handleLogout()
                    }
                    +if (loggingOut) "退出中..." else "退出登录"
                }
            }
        }
    }
}
