package com.github.woodsmarshes.web.components

import com.github.woodsmarshes.chat.core.model.UserRole
import com.github.woodsmarshes.web.Router
import com.github.woodsmarshes.web.state.useCurrentContext
import react.FC
import react.PropsWithChildren
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.h3
import react.dom.html.ReactHTML.p
import web.cssom.ClassName

/**
 * Admin area shell: sidebar layout plus a cosmetic role check.
 *
 * The children lambda is evaluated inside each page component, so this guard
 * alone cannot prevent page effects from running; [AdminArea] is the real
 * mount gate that keeps admin pages (and their requests) unmounted for
 * non-admin users. The server re-checks ADMIN on every call regardless.
 */
val AdminShell = FC<PropsWithChildren> { props ->
    val status = useCurrentContext()
    val path = Router.currentPath().substringBefore('?').ifEmpty { "/" }

    if (!status.isLoggedIn || status.user == null) {
        div {
            className = ClassName("empty-state")
            h2 { +"请先登录" }
            p { +"管理员需要登录后访问。" }
            button {
                className = ClassName("btn btn-primary")
                onClick = { Router.navigate("/login") }
                +"去登录"
            }
        }
        return@FC
    }

    if (status.user.role != UserRole.ADMIN) {
        div {
            className = ClassName("empty-state")
            h2 { +"没有权限" }
            p { +"该页面仅管理员可访问。" }
        }
        return@FC
    }

    div {
        className = ClassName("layout-with-sidebar")

        AdminSidebar.invoke { currentPath = path }

        div {
            className = ClassName("layout-main")
            +props.children
        }
    }
}

private external interface AdminSidebarProps : react.Props {
    var currentPath: String
}

private val AdminSidebar = FC<AdminSidebarProps> { props ->
    div {
        className = ClassName("sidebar admin-sidebar")
        div {
            className = ClassName("sidebar-my-articles")
            h3 { +"管理后台" }
            div {
                className = ClassName("admin-nav")
                AdminNavLink.invoke { linkPath = "/admin"; label = "概览"; active = props.currentPath == "/admin"; exact = true }
                AdminNavLink.invoke { linkPath = "/admin/users"; label = "用户"; active = props.currentPath.startsWith("/admin/users") }
                AdminNavLink.invoke { linkPath = "/admin/conversations"; label = "会话"; active = props.currentPath.startsWith("/admin/conversations") }
                AdminNavLink.invoke { linkPath = "/admin/logs"; label = "日志"; active = props.currentPath == "/admin/logs" }
                AdminNavLink.invoke { linkPath = "/admin/audit-logs"; label = "审计日志"; active = props.currentPath == "/admin/audit-logs" }
            }
        }
    }
}

private external interface AdminNavLinkProps : react.Props {
    var linkPath: String
    var label: String
    var active: Boolean
    var exact: Boolean
}

private val AdminNavLink = FC<AdminNavLinkProps> { props ->
    a {
        href = "#${props.linkPath}"
        className = ClassName("admin-nav-link" + if (props.active) " active" else "")
        onClick = { event ->
            event.preventDefault()
            Router.navigate(props.linkPath)
        }
        +props.label
    }
}

/** Route-level admin guard: children are only mounted for ADMIN users. */
val AdminArea = FC<PropsWithChildren> { props ->
    val status = useCurrentContext()

    if (!status.isLoggedIn || status.user == null) {
        div {
            className = ClassName("empty-state")
            h2 { +"请先登录" }
            p { +"管理员需要登录后访问。" }
            button {
                className = ClassName("btn btn-primary")
                onClick = { Router.navigate("/login") }
                +"去登录"
            }
        }
        return@FC
    }

    if (status.user.role != UserRole.ADMIN) {
        div {
            className = ClassName("empty-state")
            h2 { +"没有权限" }
            p { +"该页面仅管理员可访问。" }
        }
        return@FC
    }

    +props.children
}
