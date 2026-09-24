package com.github.woodsmarshes.chat.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class ExcerptUtilsTest {

    private fun docOf(vararg paragraphs: String) = buildJsonObject {
        put("type", "doc")
        putJsonArray("content") {
            paragraphs.forEach { text ->
                add(
                    buildJsonObject {
                        put("type", "paragraph")
                        putJsonArray("content") {
                            add(buildJsonObject {
                                put("type", "text")
                                put("text", text)
                            })
                        }
                    }
                )
            }
        }
    }

    // ── JsonElement overload ────────────────────────────────────────────

    @Test
    fun `null content yields null excerpt`() {
        assertNull(ExcerptUtils.generateFromTipTap(null as kotlinx.serialization.json.JsonElement?))
    }

    @Test
    fun `text nodes are concatenated in document order`() {
        val excerpt = ExcerptUtils.generateFromTipTap(docOf("Hello", "World"))

        assertEquals("Hello World", excerpt)
    }

    @Test
    fun `whitespace runs collapse to a single space`() {
        val doc = buildJsonObject {
            put("type", "doc")
            putJsonArray("content") {
                add(buildJsonObject {
                    put("type", "paragraph")
                    putJsonArray("content") {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", "  Hello\t\tWorld \n\n! ")
                        })
                    }
                })
            }
        }

        assertEquals("Hello World !", ExcerptUtils.generateFromTipTap(doc))
    }

    @Test
    fun `non-text structures are ignored`() {
        val doc = buildJsonObject {
            put("type", "doc")
            putJsonArray("content") {
                add(buildJsonObject {
                    put("type", "image")
                    put("src", "pic.png")
                })
                add(buildJsonObject {
                    put("type", "paragraph")
                    putJsonArray("content") {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", "after image")
                        })
                    }
                })
            }
        }

        assertEquals("after image", ExcerptUtils.generateFromTipTap(doc))
    }

    @Test
    fun `excerpt shorter than the limit has no ellipsis`() {
        val excerpt = ExcerptUtils.generateFromTipTap(docOf("short"), maxLength = 150)

        assertEquals("short", excerpt)
    }

    @Test
    fun `excerpt exactly at the limit has no ellipsis`() {
        val text = "a".repeat(150)
        val excerpt = ExcerptUtils.generateFromTipTap(docOf(text), maxLength = 150)

        assertEquals(text, excerpt)
    }

    @Test
    fun `excerpt one over the limit is truncated with ellipsis`() {
        val text = "b".repeat(151)
        val excerpt = ExcerptUtils.generateFromTipTap(docOf(text), maxLength = 150)

        assertEquals("b".repeat(150) + "...", excerpt)
    }

    @Test
    fun `empty document yields null`() {
        val doc = buildJsonObject {
            put("type", "doc")
            putJsonArray("content") {}
        }

        assertNull(ExcerptUtils.generateFromTipTap(doc))
    }

    @Test
    fun `blank text document yields null`() {
        val doc = buildJsonObject {
            put("type", "doc")
            putJsonArray("content") {
                add(buildJsonObject {
                    put("type", "paragraph")
                    putJsonArray("content") {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", "   ")
                        })
                    }
                })
            }
        }

        assertNull(ExcerptUtils.generateFromTipTap(doc))
    }

    // ── String overload ─────────────────────────────────────────────────

    @Test
    fun `string overload parses JSON and extracts text`() {
        val doc = docOf("from string")
        val excerpt = ExcerptUtils.generateFromTipTap(Json.encodeToString(
            kotlinx.serialization.json.JsonElement.serializer(), doc
        ))

        assertEquals("from string", excerpt)
    }

    @Test
    fun `null or blank strings yield null`() {
        assertNull(ExcerptUtils.generateFromTipTap(null as String?))
        assertNull(ExcerptUtils.generateFromTipTap(""))
        assertNull(ExcerptUtils.generateFromTipTap("   "))
    }

    @Test
    fun `invalid JSON falls back to plain-text truncation`() {
        assertEquals("plain text", ExcerptUtils.generateFromTipTap("plain text"))
        assertEquals("short and *not json", ExcerptUtils.generateFromTipTap("short and *not json"))
    }

    @Test
    fun `invalid JSON structure falls back to plain-text truncation`() {
        // Malformed structural JSON (unparseable) takes the catch-all branch.
        assertEquals("{invalid", ExcerptUtils.generateFromTipTap("{invalid"))
    }

    @Test
    fun `long invalid JSON structure is truncated with ellipsis`() {
        val raw = "[" + "x".repeat(200)

        val excerpt = ExcerptUtils.generateFromTipTap(raw, maxLength = 150)

        assertTrue(excerpt!!.endsWith("..."))
        assertEquals(153, excerpt.length)
    }

    @Test
    fun `long unquoted text parses as a JSON primitive and yields null`() {
        // QUIRK: parseToJsonElement accepts unquoted primitives, so plain text
        // never reaches the plain-text fallback; extractText ignores primitives
        // and the blank result becomes null. Pins CURRENT behaviour.
        assertNull(ExcerptUtils.generateFromTipTap("x".repeat(200), maxLength = 150))
    }
}
