package com.github.woodsmarshes.chat.core.ui.components.avatar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.github.woodsmarshes.chat.core.model.ui.SenderUser
import com.github.woodsmarshes.chat.core.ui.theme.LocalBubbleColors
import com.github.woodsmarshes.chat.resources.Res
import org.jetbrains.compose.resources.painterResource
import top.yukonga.miuix.kmp.basic.Text

/**
 * Group conversation avatar showing up to 4 participant avatars in a grid layout.
 */
@Composable
fun ConversationAvatar(
    participants: List<SenderUser>,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    onClick: (() -> Unit)? = null,
) {
    val bubbleColors = LocalBubbleColors.current
    val baseModifier = modifier
        .size(size)
        .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)

    val displayParticipants = participants.take(4)

    when (displayParticipants.size) {
        0 -> EmptyAvatar(baseModifier)
        1 -> {
            val p = displayParticipants[0]
            UserAvatar(
                name = p.displayName ?: p.username,
                avatarUrl = p.avatarUrl,
                size = size,
            )
        }
        else -> {
            val smallSize = size * 0.55f
            val offsetAmount = size * 0.22f

            Box(modifier = baseModifier) {
                displayParticipants.forEachIndexed { index, participant ->
                    val (xOffset, yOffset) = offsetsForIndex(index, offsetAmount)
                    UserAvatar(
                        name = participant.displayName ?: participant.username,
                        avatarUrl = participant.avatarUrl,
                        size = smallSize,
                        showBorder = true,
                        modifier = Modifier.offset(x = xOffset, y = yOffset),
                    )
                }
            }
        }
    }
}

private fun offsetsForIndex(index: Int, offset: Dp): Pair<Dp, Dp> = when (index) {
    0 -> Pair(-offset, -offset)
    1 -> Pair(offset, -offset)
    2 -> Pair(-offset, offset)
    3 -> Pair(offset, offset)
    else -> Pair(0.dp, 0.dp)
}

@Composable
private fun EmptyAvatar(modifier: Modifier) {
    val bubbleColors = LocalBubbleColors.current
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(bubbleColors.otherBackground),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "#",
            color = bubbleColors.otherContent,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
