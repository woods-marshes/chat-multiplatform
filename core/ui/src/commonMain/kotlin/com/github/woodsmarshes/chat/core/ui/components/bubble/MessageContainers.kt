package com.github.woodsmarshes.chat.core.ui.components.bubble

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.woodsmarshes.chat.core.model.ui.MessageState
import com.github.woodsmarshes.chat.core.model.ui.MessageUiModel
import com.github.woodsmarshes.chat.core.ui.components.avatar.UserAvatar
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import com.github.woodsmarshes.chat.core.ui.theme.LocalBubbleColors
import com.github.woodsmarshes.chat.core.ui.utils.formatMessageTime
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

val REPLY_THRESHOLD_DP = 80.dp

/**
 * Container for the user's own messages.
 *
 * Right-aligned. Swipe-left triggers reply. Handles send-status display.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OwnMessageContainer(
    message: MessageUiModel,
    onReply: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { REPLY_THRESHOLD_DP.toPx() }
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    var showReplyHint by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        // Reply icon visible when swiped past threshold
        if (showReplyHint && onReply != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = LocalStrings.current.reply,
                tint = LocalBubbleColors.current.iconTint,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(24.dp)
                    .offset(x = -(thresholdPx / 2).dp, y = 0.dp),
            )
        }

        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .then(
                    if (onReply != null) {
                        Modifier.pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    coroutineScope.launch {
                                        if (abs(offsetX.value) > thresholdPx) {
                                            onReply()
                                        }
                                        offsetX.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessLow,
                                            ),
                                        )
                                        showReplyHint = false
                                    }
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    coroutineScope.launch {
                                        offsetX.snapTo(
                                            (offsetX.value + dragAmount)
                                                .coerceIn(-thresholdPx * 2f, 0f)
                                        )
                                        showReplyHint = abs(offsetX.value) > thresholdPx
                                    }
                                },
                            )
                        }
                    } else Modifier
                ),
        ) {
            content()
            MessageTimestamp(
                createdAt = message.createdAt,
                sendStatus = message.sendStatus,
                isOwnMessage = true,
            )
        }
    }
}

/**
 * Container for other users' messages.
 *
 * Left-aligned with avatar and sender name. Swipe-right triggers reply.
 * On desktop (no touch), right-click triggers reply.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OtherMessageContainer(
    message: MessageUiModel,
    showAvatar: Boolean = true,
    showSenderName: Boolean = true,
    onReply: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { REPLY_THRESHOLD_DP.toPx() }
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    var showReplyHint by remember { mutableStateOf(false) }

    val sender = message.sender
    val bubbleColors = LocalBubbleColors.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (showAvatar) {
            UserAvatar(
                name = sender?.displayName ?: sender?.username ?: "?",
                avatarUrl = sender?.avatarUrl,
                size = 36.dp,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Box(modifier = Modifier.weight(1f, fill = false)) {
            // Reply icon when swiped
            if (showReplyHint && onReply != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = LocalStrings.current.reply,
                    tint = bubbleColors.iconTint,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(24.dp)
                        .offset(x = (thresholdPx / 2).dp, y = 0.dp),
                )
            }

            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                    .then(
                        if (onReply != null) {
                            Modifier.pointerInput(Unit) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        coroutineScope.launch {
                                            if (abs(offsetX.value) > thresholdPx) {
                                                onReply()
                                            }
                                            offsetX.animateTo(
                                                targetValue = 0f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessLow,
                                                ),
                                            )
                                            showReplyHint = false
                                        }
                                    },
                                    onHorizontalDrag = { _, dragAmount ->
                                        coroutineScope.launch {
                                            offsetX.snapTo(
                                                (offsetX.value + dragAmount)
                                                    .coerceIn(0f, thresholdPx * 2f)
                                            )
                                            showReplyHint = abs(offsetX.value) > thresholdPx
                                        }
                                    },
                                )
                            }
                        } else Modifier
                    )
                    // Right-click for reply on desktop
                    .then(
                        if (onReply != null) {
                            Modifier.combinedClickable(
                                onClick = {},
                                onLongClick = { onReply() },
                            )
                        } else Modifier
                    ),
            ) {
                if (showSenderName && sender != null) {
                    val s = sender
                    MessageSenderName(
                        name = s.displayName ?: s.username,
                        role = s.role?.name,
                    )
                }
                content()
                MessageTimestamp(
                    createdAt = message.createdAt,
                    sendStatus = message.sendStatus,
                    isOwnMessage = false,
                )
            }
        }
    }
}

@Composable
fun MessageSenderName(name: String, role: String? = null) {
    val bubbleColors = LocalBubbleColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = name,
            color = bubbleColors.senderNameColor,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 2.dp, start = 4.dp),
        )
        if (role != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = role,
                color = bubbleColors.ownBackground,
                fontSize = 10.sp,
                modifier = Modifier
                    .padding(bottom = 2.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                    .background(bubbleColors.ownBackground.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
fun MessageTimestamp(
    createdAt: kotlin.time.Instant,
    sendStatus: MessageState,
    isOwnMessage: Boolean,
) {
    val bubbleColors = LocalBubbleColors.current
    val timeStr = formatMessageTime(createdAt)

    val statusStr = when (sendStatus) {
        is MessageState.Sending -> LocalStrings.current.sending
        is MessageState.SendFailed -> LocalStrings.current.sendFailed
        is MessageState.Completed -> if (isOwnMessage) LocalStrings.current.sendCompleted else ""
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
    ) {
        if (sendStatus is MessageState.SendFailed) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = LocalStrings.current.failedCd,
                tint = bubbleColors.errorColor,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(2.dp))
        }
        if (statusStr.isNotEmpty()) {
            Text(
                text = statusStr,
                color = if (sendStatus is MessageState.SendFailed) bubbleColors.errorColor
                else bubbleColors.timestampColor,
                fontSize = 11.sp,
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = timeStr,
            color = bubbleColors.timestampColor,
            fontSize = 11.sp,
        )
    }
}
