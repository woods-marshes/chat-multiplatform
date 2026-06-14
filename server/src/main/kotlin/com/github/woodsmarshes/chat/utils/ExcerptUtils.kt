package com.github.woodsmarshes.chat.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

object ExcerptUtils {
    fun generateFromTipTap(contentJson: JsonElement?, maxLength: Int = 150): String? {
        if (contentJson == null) return null
        return try {
            val sb = StringBuilder()
            extractText(contentJson, sb)
            val plainText = sb.toString().replace(Regex("\\s+"), " ").trim()
            if (plainText.length <= maxLength) {
                plainText.takeIf { it.isNotBlank() }
            } else {
                plainText.take(maxLength).trim() + "..."
            }
        } catch (e: Exception) {
            // 失败时降级为 null
            null
        }
    }

    fun generateFromTipTap(contentJson: String?, maxLength: Int = 150): String? {
        if (contentJson.isNullOrBlank()) return null
        return try {
            val element = kotlinx.serialization.json.Json.parseToJsonElement(contentJson)
            generateFromTipTap(element, maxLength)
        } catch (e: Exception) {
            // 如果不是有效的 JSON，当作普通文本截取
            if (contentJson.length <= maxLength) contentJson.trim().takeIf { it.isNotBlank() }
            else contentJson.take(maxLength).trim() + "..."
        }
    }

    private fun extractText(element: JsonElement, sb: StringBuilder) {
        when (element) {
            is JsonObject -> {
                val type = element["type"]?.jsonPrimitive?.contentOrNull
                if (type == "text") {
                    element["text"]?.jsonPrimitive?.contentOrNull?.let { sb.append(it) }
                } else {
                    element.values.forEach { extractText(it, sb) }
                }
                if (type == "paragraph" || type == "heading") {
                    sb.append(" ")
                }
            }
            is JsonArray -> {
                element.forEach { extractText(it, sb) }
            }
            else -> { /* 忽略其他类型 */ }
        }
    }
}