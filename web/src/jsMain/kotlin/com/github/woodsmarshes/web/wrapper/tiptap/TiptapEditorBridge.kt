@file:JsModule("./tiptap-editor-bridge.umd.js")
@file:JsNonModule

package com.github.woodsmarshes.web.wrapper.tiptap

import react.FC
import react.Props

external interface TiptapEditorBridgeProps : Props {
    var content: dynamic
    var onChange: (dynamic) -> Unit
}


@JsName("TiptapEditorBridge")
external val TiptapEditorBridge: FC<TiptapEditorBridgeProps>