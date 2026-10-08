package com.github.woodsmarshes.chat.feature.article.ui

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalEncodingApi::class)
class TiptapViewerScriptTest {

    @Test
    fun renderScriptWithoutRequestIdHasBalancedParenthesesAndDecodesComplexCharacters() {
        val jsonStr = """{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"中文内容 🚀 \"quotes\" 'single' \\backslash\nline 2\r\nline 3"}]}]}"""

        val script = buildViewerRenderScript(jsonContentStr = jsonStr)

        assertBalancedJavaScriptCall(script)
        assertTrue(script.startsWith("window.__viewerShell.renderContent("))
        assertTrue(script.endsWith(");"))

        val encoded = Regex("""window\.atob\("([A-Za-z0-9+/=]*)"\)""")
            .find(script)
            ?.groupValues
            ?.get(1)
        val decoded = Base64.decode(requireNotNull(encoded)).decodeToString()
        assertEquals(jsonStr, decoded)
    }

    @Test
    fun renderScriptWithRequestIdIncludesEscapedCorrelationId() {
        val jsonStr = """{"type":"doc","content":[]}"""
        val requestId = "req-123-\"quoted\"-\\slash"

        val script = buildViewerRenderScript(
            jsonContentStr = jsonStr,
            requestId = requestId,
        )

        assertBalancedJavaScriptCall(script)
        assertTrue(script.contains("\"req-123-\\\"quoted\\\"-\\\\slash\""))
    }

    private fun assertBalancedJavaScriptCall(script: String) {
        var parenDepth = 0
        var inDoubleQuote = false
        var escaped = false

        for (ch in script) {
            if (escaped) {
                escaped = false
                continue
            }
            when (ch) {
                '\\' -> if (inDoubleQuote) escaped = true
                '"' -> inDoubleQuote = !inDoubleQuote
                '(' -> if (!inDoubleQuote) parenDepth++
                ')' -> if (!inDoubleQuote) {
                    parenDepth--
                    assertTrue(parenDepth >= 0, "Closing parenthesis before opening in: $script")
                }
            }
        }

        assertEquals(false, inDoubleQuote, "Unclosed string literal in: $script")
        assertEquals(0, parenDepth, "Unbalanced parentheses (net depth=$parenDepth) in: $script")
    }
}
