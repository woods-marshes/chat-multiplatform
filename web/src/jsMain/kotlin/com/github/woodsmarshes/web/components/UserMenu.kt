package com.github.woodsmarshes.web.components

import com.github.woodsmarshes.chat.core.datastore.AuthTokenDataSource
import com.github.woodsmarshes.chat.core.model.UserRole
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

    // Close the dropdown when clicking outside it.
    // The effect callback runs in a suspend CoroutineScope that is cancelled on
    // unmount/dependency change, so use awaitCancellation() + try/finally.
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
                    // One atomic write: token, profile and generation clear together.
                    koinInject<AuthTokenDataSource>().clearLoginSession()
                } catch (e: Exception) {
                    console.log("Logout failed: ${e.message}")
                }
                // UserProvider observes the Flow and updates to logged-out state.
                Router.navigate("/login")
                setLoggingOut(false)
            }
        }
    }

    div {
        className = ClassName("user-menu")

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

                if (user.role == UserRole.ADMIN) {
                    a {
                        href = "#/admin"
                        className = ClassName("user-menu-item")
                        onClick = { event ->
                            event.preventDefault()
                            setOpen(false)
                            Router.navigate("/admin")
                        }
                        +"管理后台"
                    }
                }

                div { className = ClassName("user-menu-divider") }

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
