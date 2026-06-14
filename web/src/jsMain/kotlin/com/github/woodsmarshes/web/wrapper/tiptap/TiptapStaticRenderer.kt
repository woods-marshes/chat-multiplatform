@file:JsModule("@tiptap/static-renderer")
@file:JsNonModule

package com.github.woodsmarshes.web.wrapper.tiptap

import react.ReactElement

/**
 * 🟢 对接官方 @tiptap/static-renderer 的绑定
 */
@JsName("renderJSONContentToReactElement")
external fun renderToReact(
    json: dynamic,
    options: dynamic = definedExternally
): ReactElement<*>