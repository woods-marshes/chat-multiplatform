package com.github.woodsmarshes.chat.core.ui.components.bubble

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.github.woodsmarshes.chat.core.model.FormattingEntity

// Annotation tags used for clickable spans
const val ANNOTATION_URL = "URL"
const val ANNOTATION_MENTION = "MENTION"
const val ANNOTATION_HASHTAG = "HASHTAG"

/**
 * Platform-agnostic rich-text formatter for chat messages.
 *
 * Builds an [AnnotatedString] from raw text and [FormattingEntity] metadata.
 * Clickable elements (URLs, @mentions, #hashtags) are tagged via
 * [pushStringAnnotation] so that the rendering composable can resolve clicks.
 */
class MessageFormatter(
    val onMentionClicked: ((userId: String) -> Unit)? = null,
    val onUrlClicked: ((url: String) -> Unit)? = null,
    val onHashtagClicked: ((tag: String) -> Unit)? = null,
) {
    /**
     * Build an [AnnotatedString] with styles and clickable annotations.
     *
     * @param text       Raw message text
     * @param entities   List of formatting entities (bold, italic, mention, etc.)
     * @param defaultColor Default text color for unstyled regions
     */
    fun buildRichText(
        text: String,
        entities: List<FormattingEntity>,
        defaultColor: Color,
    ): AnnotatedString {
        if (entities.isEmpty()) {
            return AnnotatedString(text, SpanStyle(color = defaultColor))
        }

        return buildAnnotatedString {
            withStyle(SpanStyle(color = defaultColor)) {
                append(text)
            }

            entities.forEach { entity ->
                val range = when (entity) {
                    is FormattingEntity.TextRange -> entity.offset to (entity.offset + entity.length)
                    is FormattingEntity.BlockQuote -> return@forEach // handled at bubble level
                }
                val start = range.first.coerceIn(0, text.length)
                val end = range.second.coerceIn(start, text.length)
                if (start >= end) return@forEach

                when (entity) {
                    is FormattingEntity.Bold -> {
                        addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                    }
                    is FormattingEntity.Italic -> {
                        addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                    }
                    is FormattingEntity.Strikethrough -> {
                        addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, end)
                    }
                    is FormattingEntity.Spoiler -> {
                        addStyle(SpanStyle(background = defaultColor, color = defaultColor), start, end)
                    }
                    is FormattingEntity.Code -> {
                        addStyle(SpanStyle(fontFamily = FontFamily.Monospace), start, end)
                    }
                    is FormattingEntity.Url -> {
                        pushStringAnnotation(tag = ANNOTATION_URL, annotation = entity.url)
                        addStyle(
                            SpanStyle(
                                color = Color(0xFF64B5F6),
                                textDecoration = TextDecoration.Underline,
                            ),
                            start,
                            end,
                        )
                        pop()
                    }
                    is FormattingEntity.Mention -> {
                        pushStringAnnotation(
                            tag = ANNOTATION_MENTION,
                            annotation = entity.userId.toString(),
                        )
                        addStyle(SpanStyle(fontWeight = FontWeight.Medium), start, end)
                        pop()
                    }
                    is FormattingEntity.MentionAll -> {
                        pushStringAnnotation(tag = ANNOTATION_MENTION, annotation = "all")
                        addStyle(SpanStyle(fontWeight = FontWeight.Medium), start, end)
                        pop()
                    }
                    is FormattingEntity.Hashtag -> {
                        pushStringAnnotation(tag = ANNOTATION_HASHTAG, annotation = entity.tag)
                        addStyle(SpanStyle(fontWeight = FontWeight.Medium), start, end)
                        pop()
                    }
                    is FormattingEntity.BotCommand -> {
                        addStyle(SpanStyle(fontFamily = FontFamily.Monospace), start, end)
                    }
                    else -> {} // no-op for unrecognized entities
                }
            }
        }
    }
}
