package com.github.woodsmarshes.web

import kotlinx.browser.window

/** Minimal hash-based router for the MVP. */
object Router {
    /** Navigate to a hash path, e.g. "/articles/abc-123" */
    fun navigate(path: String) {
        window.location.hash = "#$path"
    }

    /** Go back in browser history. */
    fun back() {
        window.history.back()
    }

    /** Get the current path from the hash (without the leading "#"), defaults to "/". */
    fun currentPath(): String {
        val hash = window.location.hash
        return if (hash.isNotEmpty() && hash.startsWith("#")) {
            hash.removePrefix("#").ifEmpty { "/" }
        } else {
            "/"
        }
    }
}
