package com.github.woodsmarshes.chat.core.ui.components.avatar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
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
import androidx.compose.material3.Text
import coil3.compose.SubcomposeAsyncImage
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import com.github.woodsmarshes.chat.core.ui.theme.LocalBubbleColors

val LocalServerBaseUrl = staticCompositionLocalOf { "http://127.0.0.1:9051" }

fun resolveMediaUrl(url: String?, baseUrl: String): String? {
    val trimmed = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return when {
        trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("file://") -> trimmed
        trimmed.startsWith("//") -> "https:$trimmed"
        trimmed.startsWith("/") -> "${baseUrl.trimEnd('/')}$trimmed"
        else -> "${baseUrl.trimEnd('/')}/$trimmed"
    }
}

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
    val initials = name?.trim()?.take(2)?.uppercase()?.ifEmpty { null } ?: "?"
    val bgColor = avatarColor(name)
    val bubbleColors = LocalBubbleColors.current
    val resolvedAvatarUrl = resolveMediaUrl(avatarUrl, LocalServerBaseUrl.current)
    val strings = LocalStrings.current

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
            .then(interactionModifier),
        contentAlignment = Alignment.Center,
    ) {
        if (resolvedAvatarUrl != null) {
            SubcomposeAsyncImage(
                model = resolvedAvatarUrl,
                contentDescription = name ?: strings.avatarCd,
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

        if (showOnlineDot) {
            val dotSize = (size * 0.25f).coerceIn(8.dp, 14.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(Color(0xFF2E7D32))
                    .border(1.5.dp, bubbleColors.surfaceColor, CircleShape),
            )
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
        Color(0xFFD32F2F), Color(0xFF388E3C), Color(0xFF1976D2),
        Color(0xFFF57C00), Color(0xFF8E24AA), Color(0xFF00796B),
        Color(0xFFE64A19), Color(0xFF3949AB),
    )
    val hash = key?.hashCode() ?: 0
    val index = hash.mod(colors.size).let { if (it < 0) it + colors.size else it }
    return colors[index]
}
