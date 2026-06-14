package com.github.woodsmarshes.web.components

import com.github.woodsmarshes.web.Router
import web.cssom.ClassName
import react.FC
import react.PropsWithChildren
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.main
import react.dom.html.ReactHTML.nav

val Layout = FC<PropsWithChildren> { props ->
    nav {
        className = ClassName("app-nav") // 🟢 恢复强类型安全的 className
        a {
            href = "#/"
            onClick = { event -> // 🟢 恢复强类型的事件监听
                event.preventDefault()
                Router.navigate("/")
            }
            +"Writing Platform"
        }
    }
    main {
        className = ClassName("app-main")
        // 🟢 声明式地渲染子级，告别 asDynamic()
        +props.children
    }
}
