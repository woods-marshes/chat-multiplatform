package com.github.woodsmarshes.chat.core.ui.components.bubble

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.github.woodsmarshes.chat.core.model.ImageContent
import com.github.woodsmarshes.chat.core.ui.components.avatar.LocalServerBaseUrl
import com.github.woodsmarshes.chat.core.ui.components.avatar.resolveMediaUrl
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import com.github.woodsmarshes.chat.core.ui.theme.LocalBubbleColors
import com.github.woodsmarshes.chat.core.ui.theme.LocalBubbleShapes

private val MAX_IMAGE_WIDTH_FRACTION = 0.7f
private val MAX_IMAGE_DP = 260.dp

@Composable
fun ImageBubble(
    content: ImageContent?,
    isOwnMessage: Boolean,
    modifier: Modifier = Modifier,
    onImageClick: ((ImageContent) -> Unit)? = null,
) {
    if (content == null) return

    val bubbleColors = LocalBubbleColors.current
    val bubbleShapes = LocalBubbleShapes.current
    val resolvedUrl = resolveMediaUrl(content.url, LocalServerBaseUrl.current)

    val shape = bubbleShapes.mediaBubble

    val aspectRatio = if (content.width > 0 && content.height > 0) {
        content.width.toFloat() / content.height.toFloat()
    } else {
        4f / 3f
    }

    val imageHeight = when {
        aspectRatio > 1.5f -> 160.dp
        aspectRatio < 0.8f -> 260.dp
        else -> 200.dp
    }
    val imageWidth = when {
        aspectRatio > 1.5f -> 240.dp
        aspectRatio < 0.8f -> 160.dp
        else -> 200.dp
    }
    val bgColor = if (isOwnMessage) bubbleColors.ownBackground.copy(alpha = 0.85f) else bubbleColors.otherBackground

    Box(
        modifier = modifier
            .fillMaxWidth(MAX_IMAGE_WIDTH_FRACTION)
            .widthIn(max = MAX_IMAGE_DP)
            .clip(shape)
            .background(bgColor)
            .then(
                if (onImageClick != null) Modifier.clickable { onImageClick(content) }
                else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        SubcomposeAsyncImage(
            model = resolvedUrl,
            contentDescription = content.fileName,
            modifier = Modifier
                .widthIn(max = imageWidth)
                .height(imageHeight),
            contentScale = ContentScale.Crop,
            loading = {
                Box(
                    modifier = Modifier.fillMaxWidth().height(imageHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = bubbleColors.iconTint,
                        strokeWidth = 2.dp,
                    )
                }
            },
            error = {
                Box(
                    modifier = Modifier.fillMaxWidth().height(imageHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = LocalStrings.current.imageLoadFailed,
                        color = bubbleColors.errorColor,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            },
        )
    }
}
