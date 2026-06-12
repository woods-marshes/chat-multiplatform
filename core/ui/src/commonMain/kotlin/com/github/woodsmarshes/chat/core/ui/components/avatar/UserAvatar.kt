package com.github.woodsmarshes.chat.core.ui.components.avatar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import coil3.compose.SubcomposeAsyncImage
import coil3.request.crossfade
import com.github.woodsmarshes.chat.core.ui.theme.LocalBubbleColors
import top.yukonga.miuix.kmp.basic.Text

@Composable
fun UserAvatar(
    name: String?,
    avatarUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    showBorder: Boolean = false,
    showOnlineDot: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
) {
    val initials = (name?.take(1)?.uppercase() ?: "?").take(2)
    val bgColor = avatarColor(name)
    val bubbleColors = LocalBubbleColors.current

    val interactionModifier = when {
        onClick != null && onLongPress != null -> Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongPress,
        )
        onClick != null -> Modifier.clickable { onClick() }
        else -> Modifier
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(interactionModifier),
        contentAlignment = Alignment.Center,
    ) {
        if (avatarUrl != null) {
            SubcomposeAsyncImage(
                model = avatarUrl,
                contentDescription = name ?: "Avatar",
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .then(
                        if (showBorder) Modifier.border(2.dp, bubbleColors.inputFieldBackground, CircleShape)
                        else Modifier
                    ),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(
                        modifier = Modifier
                            .size(size)
                            .clip(CircleShape)
                            .background(bgColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        placeholderImage(size)
                    }
                },
                error = {
                    Box(
                        modifier = Modifier
                            .size(size)
                            .clip(CircleShape)
                            .background(bgColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        placeholderImage(size)
                    }
                },
            )
        } else {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(bgColor)
                    .then(
                        if (showBorder) Modifier.border(2.dp, bubbleColors.inputFieldBackground, CircleShape)
                        else Modifier
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initials,
                    color = Color.White,
                    fontSize = (size.value * 0.38f).sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun placeholderImage(size: Dp) {
    androidx.compose.material3.Icon(
        imageVector = Icons.Default.Person,
        contentDescription = null,
        modifier = Modifier.size(size * 0.6f),
        tint = Color.White.copy(alpha = 0.6f),
    )
}

private fun avatarColor(key: String?): Color {
    val colors = listOf(
        Color(0xFFE57373), Color(0xFF81C784), Color(0xFF64B5F6),
        Color(0xFFFFD54F), Color(0xFFBA68C8), Color(0xFF4DB6AC),
        Color(0xFFFF8A65), Color(0xFF7986CB),
    )
    val hash = key?.hashCode() ?: 0
    val index = hash.mod(colors.size).let { if (it < 0) it + colors.size else it }
    return colors[index]
}
