@file:JsModule("@tiptap/react")
@file:JsNonModule

package com.github.woodsmarshes.web.wrapper.tiptap

import react.FC
import react.Props

/**
 * Wraps the TipTap useEditor React hook.
 * Returns the Editor instance or null.
 */
@JsName("useEditor")
external fun useEditor(options: dynamic): dynamic

/**
 * React component that renders the TipTap editor content area.
 */
@JsName("EditorContent")
external val EditorContent: FC<EditorContentProps>

external interface EditorContentProps : Props {
    var editor: dynamic
}
