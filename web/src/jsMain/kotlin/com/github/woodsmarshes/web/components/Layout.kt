package com.github.woodsmarshes.web.components

import com.github.woodsmarshes.web.Router
import web.cssom.ClassName
import react.FC
import react.PropsWithChildren
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.main
import react.dom.html.ReactHTML.nav

val Layout = FC<PropsWithChildren> { props ->
    val path = Router.currentPath()
    val isHome = path == "/" || path.isEmpty()

    nav {
        className = ClassName("app-nav")
        div {
            className = ClassName("app-nav-container")
            // 返回按钮：仅在非首页显示，调用浏览器历史回退
            if (!isHome) {
                a {
                    href = "#"
                    className = ClassName("app-nav-back")
                    onClick = { event ->
                        event.preventDefault()
                        Router.back()
                    }
                    +("← Back")
                }
            }
            a {
                href = "#/"
                className = ClassName("app-nav-title")
                onClick = { event ->
                    event.preventDefault()
                    Router.navigate("/")
                }
                +"Writing Platform"
            }
        }
    }
    main {
        className = ClassName("app-main")
        +props.children
    }
}
