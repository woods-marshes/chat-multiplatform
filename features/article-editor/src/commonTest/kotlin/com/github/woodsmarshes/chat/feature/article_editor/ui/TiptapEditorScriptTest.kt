package com.github.woodsmarshes.chat.feature.article_editor.ui

import com.github.woodsmarshes.chat.core.network.serialization.ProjectJson
import com.github.woodsmarshes.chat.feature.article_editor.model.CollabConfig
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalEncodingApi::class)
class TiptapEditorScriptTest {

    @Test
    fun nonCollabScriptHasBalancedParenthesesAndDecodesComplexCharacters() {
        val title = "中文标题 🚀 \"quotes\" 'single' \\backslash\nsecond line\r\nthird"
        val jsonStr = """{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"Hello 世界 🎉 \"quoted\"\nline 2"}]}]}"""

        val script = buildEditorInitializeScript(
            initialTitle = title,
            initialJsonStr = jsonStr,
        )

        assertBalancedJavaScriptCall(script, expectedArgCount = 2)

        val args = extractBase64Args(script)
        assertEquals(2, args.size)
        assertEquals(title, Base64.decode(args[0]).decodeToString())
        assertEquals(jsonStr, Base64.decode(args[1]).decodeToString())
    }

    @Test
    fun collabScriptHasBalancedParenthesesAndSerializesCollabConfigSafely() {
        val title = "协作文章 ✍️ \"Draft\""
        val jsonStr = """{"type":"doc","content":[]}"""
        val userName = "Wood \"The Reviewer\" \\ \n🌱"

        val script = buildEditorInitializeScript(
            initialTitle = title,
            initialJsonStr = jsonStr,
            collabUrl = "ws://127.0.0.1:1234",
            roomId = "room-123",
            token = "jwt.token.value",
            userInfoName = userName,
            userInfoColor = "#34d399",
        )

        assertBalancedJavaScriptCall(script, expectedArgCount = 3)

        val args = extractBase64Args(script)
        assertEquals(3, args.size)
        assertEquals(title, Base64.decode(args[0]).decodeToString())
        assertEquals(jsonStr, Base64.decode(args[1]).decodeToString())

        val collabJson = Base64.decode(args[2]).decodeToString()
        val parsedCollab = ProjectJson.decodeFromString(CollabConfig.serializer(), collabJson)
        assertEquals("ws://127.0.0.1:1234", parsedCollab.collabUrl)
        assertEquals("room-123", parsedCollab.roomId)
        assertEquals("jwt.token.value", parsedCollab.token)
        assertNotNull(parsedCollab.userInfo)
        assertEquals(userName, parsedCollab.userInfo?.name)
        assertEquals("#34d399", parsedCollab.userInfo?.color)
    }

    @Test
    fun partialCollabParametersFallBackToNonCollabTwoArgCall() {
        val scriptMissingRoom = buildEditorInitializeScript(
            initialTitle = "Title",
            initialJsonStr = "{}",
            collabUrl = "ws://127.0.0.1:1234",
            roomId = null,
        )
        assertBalancedJavaScriptCall(scriptMissingRoom, expectedArgCount = 2)

        val scriptMissingUrl = buildEditorInitializeScript(
            initialTitle = "Title",
            initialJsonStr = "{}",
            collabUrl = null,
            roomId = "room-1",
        )
        assertBalancedJavaScriptCall(scriptMissingUrl, expectedArgCount = 2)
    }

    @Test
    fun collabDefaultsAnonymousUserInfoWhenOmitted() {
        val script = buildEditorInitializeScript(
            initialTitle = "",
            initialJsonStr = "{}",
            collabUrl = "wss://example.com/collab",
            roomId = "room-abc",
            token = null,
            userInfoName = null,
            userInfoColor = null,
        )

        assertBalancedJavaScriptCall(script, expectedArgCount = 3)
        val args = extractBase64Args(script)
        val parsedCollab = ProjectJson.decodeFromString(
            CollabConfig.serializer(),
            Base64.decode(args[2]).decodeToString(),
        )
        assertNull(parsedCollab.token)
        assertEquals("Anonymous", parsedCollab.userInfo?.name)
        assertEquals("#ffcc00", parsedCollab.userInfo?.color)
    }

    private fun assertBalancedJavaScriptCall(script: String, expectedArgCount: Int) {
        assertTrue(
            script.startsWith("window.__editorShell.initialize("),
            "Script must invoke window.__editorShell.initialize",
        )
        assertTrue(script.endsWith(");"), "Script must end with ');'")

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

        val args = extractBase64Args(script)
        assertEquals(expectedArgCount, args.size, "Unexpected number of Base64 arguments in: $script")
    }

    private fun extractBase64Args(script: String): List<String> {
        val regex = Regex("""decodeURIComponent\(escape\(window\.atob\("([A-Za-z0-9+/=]*)"\)\)\)""")
        return regex.findAll(script).map { it.groupValues[1] }.toList()
    }
}
