package com.github.woodsmarshes.chat.core.ui.components.bubble

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.woodsmarshes.chat.core.model.FormattingEntity
import com.github.woodsmarshes.chat.core.model.TextContent
import com.github.woodsmarshes.chat.core.model.ui.MessageState
import com.github.woodsmarshes.chat.core.ui.theme.LocalBubbleColors
import com.github.woodsmarshes.chat.core.ui.theme.LocalBubbleShapes

@Composable
fun TextBubble(
    content: TextContent,
    isOwnMessage: Boolean,
    sendStatus: MessageState,
    formatter: MessageFormatter = rememberFormatter(),
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val bubbleColors = LocalBubbleColors.current
    val bubbleShapes = LocalBubbleShapes.current

    val bgColor = if (isOwnMessage) bubbleColors.ownBackground else bubbleColors.otherBackground
    val textColor = if (isOwnMessage) bubbleColors.ownContent else bubbleColors.otherContent
    val shape = if (isOwnMessage) bubbleShapes.ownBubble else bubbleShapes.otherBubble

    val annotatedText = remember(content.text, content.entities, textColor) {
        formatter.buildRichText(content.text, content.entities, textColor)
    }

    val textStyle = TextStyle(fontSize = 15.sp, lineHeight = 22.sp)

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    val combinedModifier = modifier
        .clip(shape)
        .background(bgColor)
        .then(
            if (sendStatus is MessageState.SendFailed && onRetry != null) {
                Modifier.clickable { onRetry() }
            } else {
                Modifier
            }
        )
        .then(
            if (needsClickHandling(annotatedText)) {
                Modifier.pointerInput(formatter) {
                    detectTapGestures { tapOffset ->
                        val layout = textLayoutResult ?: return@detectTapGestures
                        val position = layout.getOffsetForPosition(tapOffset)
                        if (position != -1) {
                            handleAnnotationClick(annotatedText, position, formatter)
                        }
                    }
                }
            } else {
                Modifier
            }
        )
        .padding(horizontal = 12.dp, vertical = 8.dp)

    BasicText(
        text = annotatedText,
        style = textStyle.copy(color = textColor),
        modifier = combinedModifier,
        onTextLayout = { textLayoutResult = it },
    )
}

private fun needsClickHandling(text: AnnotatedString): Boolean {
    return text.getStringAnnotations(ANNOTATION_URL, 0, text.length).isNotEmpty() ||
        text.getStringAnnotations(ANNOTATION_MENTION, 0, text.length).isNotEmpty() ||
        text.getStringAnnotations(ANNOTATION_HASHTAG, 0, text.length).isNotEmpty()
}

private fun handleAnnotationClick(
    text: AnnotatedString,
    position: Int,
    formatter: MessageFormatter,
) {
    text.getStringAnnotations(ANNOTATION_URL, position, position)
        .firstOrNull()?.item?.let { formatter.onUrlClicked?.invoke(it) }
    text.getStringAnnotations(ANNOTATION_MENTION, position, position)
        .firstOrNull()?.item?.let { formatter.onMentionClicked?.invoke(it) }
    text.getStringAnnotations(ANNOTATION_HASHTAG, position, position)
        .firstOrNull()?.item?.let { formatter.onHashtagClicked?.invoke(it) }
}

@Composable
fun rememberFormatter(
    onMentionClicked: ((userId: String) -> Unit)? = null,
    onUrlClicked: ((url: String) -> Unit)? = null,
    onHashtagClicked: ((tag: String) -> Unit)? = null,
): MessageFormatter = remember(onMentionClicked, onUrlClicked, onHashtagClicked) {
    MessageFormatter(
        onMentionClicked = onMentionClicked,
        onUrlClicked = onUrlClicked,
        onHashtagClicked = onHashtagClicked,
    )
}
