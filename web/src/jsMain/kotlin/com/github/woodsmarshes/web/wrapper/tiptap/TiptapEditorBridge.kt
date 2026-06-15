@file:JsModule("./tiptap-editor-bridge.umd.js")
@file:JsNonModule

package com.github.woodsmarshes.web.wrapper.tiptap

import react.FC
import react.Props
import react.ReactElement

external interface TiptapEditorBridgeProps : Props {
    var title: String
    var onTitleChange: (String) -> Unit
    var content: dynamic
    var onChange: (dynamic) -> Unit
}


@JsName("TiptapEditorBridge")
external val TiptapEditorBridge: FC<TiptapEditorBridgeProps>

/**
 * 将 Tiptap JSON 文档渲染为 React 元素（无需编辑器实例）。
 * 实现位于 tiptap-bridge UMD 产物，内部使用 @tiptap/static-renderer/pm/react
 * 的 renderToReactElement 并注入与编辑器一致的扩展列表。
 *
 * @param json Tiptap JSON 文档（来自 editor.getJSON()，Kotlin 侧为已 parse 的 dynamic）
 */
@JsName("renderArticleContent")
external fun renderArticleContent(json: dynamic): ReactElement<Props>