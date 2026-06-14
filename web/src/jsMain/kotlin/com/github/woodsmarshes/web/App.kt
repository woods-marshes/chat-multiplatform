package com.github.woodsmarshes.web

import com.github.woodsmarshes.web.components.Layout
import com.github.woodsmarshes.web.pages.ArticleEditorPage
import com.github.woodsmarshes.web.pages.ArticleViewPage
import com.github.woodsmarshes.web.pages.AuthPage
import com.github.woodsmarshes.web.pages.HomePage
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
        routePath == "/articles/new" -> ArticleEditorPage
        routePath.startsWith("/articles/") && routePath.endsWith("/edit") -> ArticleEditorPage
        routePath.startsWith("/articles/") -> ArticleViewPage
        else -> HomePage
    }

    Layout.invoke {
        pageElement()
    }
}
