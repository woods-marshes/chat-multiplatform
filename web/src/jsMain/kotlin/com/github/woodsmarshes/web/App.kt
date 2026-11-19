package com.github.woodsmarshes.web

import com.github.woodsmarshes.web.components.AdminArea
import com.github.woodsmarshes.web.components.Layout
import com.github.woodsmarshes.web.pages.ArticleEditorPage
import com.github.woodsmarshes.web.pages.ArticleViewPage
import com.github.woodsmarshes.web.pages.AuthPage
import com.github.woodsmarshes.web.pages.HomePage
import com.github.woodsmarshes.web.pages.MyArticlesPage
import com.github.woodsmarshes.web.pages.ProfilePage
import com.github.woodsmarshes.web.pages.SettingsPage
import com.github.woodsmarshes.web.pages.admin.AdminAuditLogsPage
import com.github.woodsmarshes.web.pages.admin.AdminConversationDetailPage
import com.github.woodsmarshes.web.pages.admin.AdminConversationsPage
import com.github.woodsmarshes.web.pages.admin.AdminLogsPage
import com.github.woodsmarshes.web.pages.admin.AdminOverviewPage
import com.github.woodsmarshes.web.pages.admin.AdminUserDetailPage
import com.github.woodsmarshes.web.pages.admin.AdminUsersPage
import com.github.woodsmarshes.web.state.ContextProvider
import kotlinx.browser.window
import react.FC
import react.Props
import react.useEffectOnce
import react.useState

private val AdminOverviewRoute = FC<Props> { AdminArea { AdminOverviewPage() } }
private val AdminUsersRoute = FC<Props> { AdminArea { AdminUsersPage() } }
private val AdminUserDetailRoute = FC<Props> { AdminArea { AdminUserDetailPage() } }
private val AdminConversationsRoute = FC<Props> { AdminArea { AdminConversationsPage() } }
private val AdminConversationDetailRoute = FC<Props> { AdminArea { AdminConversationDetailPage() } }
private val AdminLogsRoute = FC<Props> { AdminArea { AdminLogsPage() } }
private val AdminAuditLogsRoute = FC<Props> { AdminArea { AdminAuditLogsPage() } }

val App = FC<Props> {
    var currentPath by useState(Router.currentPath())

    useEffectOnce {
        window.onhashchange = {
            currentPath = Router.currentPath()
        }
    }

    // Strip query params for route matching
    val routePath = currentPath.substringBefore('?').ifEmpty { "/" }

    val pageElement = when {
        routePath == "/" -> HomePage
        routePath == "/login" -> AuthPage
        routePath == "/me" -> ProfilePage
        routePath == "/settings" -> SettingsPage
        routePath == "/admin" -> AdminOverviewRoute
        routePath == "/admin/users" -> AdminUsersRoute
        routePath.startsWith("/admin/users/") -> AdminUserDetailRoute
        routePath == "/admin/conversations" -> AdminConversationsRoute
        routePath.startsWith("/admin/conversations/") -> AdminConversationDetailRoute
        routePath == "/admin/logs" -> AdminLogsRoute
        routePath == "/admin/audit-logs" -> AdminAuditLogsRoute
        routePath == "/my/articles" -> MyArticlesPage
        routePath == "/articles/new" -> ArticleEditorPage
        routePath.startsWith("/articles/") && routePath.endsWith("/edit") -> ArticleEditorPage
        routePath.startsWith("/articles/") -> ArticleViewPage
        else -> HomePage
    }

    // UserProvider must wrap Layout so the nav UserMenu can read the current user.
    ContextProvider.invoke {
        Layout.invoke {
            pageElement()
        }
    }
}
