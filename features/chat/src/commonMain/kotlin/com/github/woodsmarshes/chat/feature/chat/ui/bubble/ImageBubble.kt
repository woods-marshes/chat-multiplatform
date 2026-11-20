package com.github.woodsmarshes.chat.feature.chat.ui.bubble

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.github.woodsmarshes.chat.core.model.ImageContent
import com.github.woodsmarshes.chat.core.ui.components.BlurHashPlaceholder
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

    val aspectRatio =
        if (content.width > 0 && content.height > 0) {
            content.width.toFloat() / content.height.toFloat()
        } else {
            4f / 3f
        }

    val imageHeight =
        when {
            aspectRatio > 1.5f -> 160.dp
            aspectRatio < 0.8f -> 260.dp
            else -> 200.dp
        }
    val imageWidth =
        when {
            aspectRatio > 1.5f -> 240.dp
            aspectRatio < 0.8f -> 160.dp
            else -> 200.dp
        }
    val bgColor = if (isOwnMessage) bubbleColors.ownBackground.copy(alpha = 0.85f) else bubbleColors.otherBackground

    Box(
        modifier =
            modifier
                .fillMaxWidth(MAX_IMAGE_WIDTH_FRACTION)
                .widthIn(max = MAX_IMAGE_DP)
                .clip(shape)
                .background(bgColor)
                .then(
                    if (onImageClick != null) {
                        Modifier.clickable { onImageClick(content) }
                    } else {
                        Modifier
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        // State-driven AsyncImage instead of SubcomposeAsyncImage: same loading/error
        // visuals without subcomposition cost in the chat list.
        // Keyed on the URL so a recycled row does not keep the previous image's state.
        var imageState by remember(resolvedUrl) {
            mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
        }
        Box(
            modifier =
                Modifier
                    .widthIn(max = imageWidth)
                    .height(imageHeight),
            contentAlignment = Alignment.Center,
        ) {
            BlurHashPlaceholder(
                blurHash = content.blurHash,
                modifier = Modifier.fillMaxSize(),
            )
            AsyncImage(
                model = resolvedUrl,
                contentDescription = content.fileName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onState = { imageState = it },
            )
            when {
                // Empty and Loading both need feedback; only Success draws clean.
                imageState is AsyncImagePainter.State.Error -> {
                    Text(
                        text = LocalStrings.current.imageLoadFailed,
                        color = bubbleColors.errorColor,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                }

                imageState !is AsyncImagePainter.State.Success -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = bubbleColors.iconTint,
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
    }
}
