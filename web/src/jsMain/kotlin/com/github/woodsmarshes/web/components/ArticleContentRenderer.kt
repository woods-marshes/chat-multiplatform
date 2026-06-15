package com.github.woodsmarshes.web.components

import com.github.woodsmarshes.chat.core.network.serialization.ProjectJson
import com.github.woodsmarshes.web.wrapper.tiptap.renderArticleContent
import kotlinx.serialization.json.JsonElement
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import web.cssom.ClassName

external interface ArticleContentRendererProps : Props {
    var content: JsonElement
}

val ArticleContentRenderer = FC<ArticleContentRendererProps> { props ->
    div {
        className = ClassName("article-content")
        try {
            // 将 Kotlin JsonElement 序列化为字符串后再 parse 成 JS 动态对象，
            // 交给 tiptap-bridge 的静态渲染器（内部注入与编辑器一致的扩展列表）。
            val serializedStr = ProjectJson.encodeToString(JsonElement.serializer(), props.content)
            val jsJson = JSON.parse<dynamic>(serializedStr)

            +renderArticleContent(jsJson)
        } catch (e: Exception) {
            +("Failed to render content: ${e.message}")
        }
    }
}