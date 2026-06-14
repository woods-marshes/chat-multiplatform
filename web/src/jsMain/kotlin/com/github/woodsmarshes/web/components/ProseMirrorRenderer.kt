package com.github.woodsmarshes.web.components

import com.github.woodsmarshes.chat.core.network.serialization.ProjectJson
import com.github.woodsmarshes.web.wrapper.tiptap.renderToReact
import kotlinx.serialization.json.JsonElement
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import web.cssom.ClassName

external interface ProseMirrorRendererProps : Props {
    var content: JsonElement
}

val ProseMirrorRenderer = FC<ProseMirrorRendererProps> { props ->
    div {
        className = ClassName("article-content")
        try {
            val serializedStr = ProjectJson.encodeToString(JsonElement.serializer(), props.content)

            val jsJson = JSON.parse<dynamic>(serializedStr)

            val renderedElement = renderToReact(jsJson)

            +renderedElement
        } catch (e: Exception) {
            +("Failed to render content: ${e.message}")
        }
    }
}