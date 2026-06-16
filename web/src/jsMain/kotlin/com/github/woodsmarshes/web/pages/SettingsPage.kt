package com.github.woodsmarshes.web.pages

import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.p
import web.cssom.ClassName

/**
 * 设置页（/settings）。占位，功能待后续填充。
 */
val SettingsPage = FC<Props> {
    div {
        className = ClassName("settings-page")
        div {
            className = ClassName("settings-card")
            h1 { +"设置" }
            p {
                className = ClassName("settings-placeholder")
                +"即将推出"
            }
        }
    }
}
