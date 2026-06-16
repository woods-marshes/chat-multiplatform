package com.github.woodsmarshes.web

import com.github.woodsmarshes.web.components.Layout
import com.github.woodsmarshes.web.pages.ArticleEditorPage
import com.github.woodsmarshes.web.pages.ArticleViewPage
import com.github.woodsmarshes.web.pages.AuthPage
import com.github.woodsmarshes.web.pages.HomePage
import com.github.woodsmarshes.web.pages.MyArticlesPage
import com.github.woodsmarshes.web.pages.ProfilePage
import com.github.woodsmarshes.web.pages.SettingsPage
import com.github.woodsmarshes.web.state.ContextProvider
import kotlinx.browser.window
import react.FC
import react.Props
import react.useEffectOnce
import react.useState

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
        routePath == "/my/articles" -> MyArticlesPage
        routePath == "/articles/new" -> ArticleEditorPage
        routePath.startsWith("/articles/") && routePath.endsWith("/edit") -> ArticleEditorPage
        routePath.startsWith("/articles/") -> ArticleViewPage
        else -> HomePage
    }

    // UserProvider 必须在 Layout 外层，确保导航栏的 UserMenu 也能读到当前用户
    ContextProvider.invoke {
        Layout.invoke {
            pageElement()
        }
    }
}
