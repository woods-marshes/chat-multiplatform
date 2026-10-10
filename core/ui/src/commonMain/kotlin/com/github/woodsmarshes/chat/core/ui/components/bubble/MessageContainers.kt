package com.github.woodsmarshes.chat.core.ui.components.bubble

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.woodsmarshes.chat.core.model.ConversationRole
import com.github.woodsmarshes.chat.core.model.DarkThemeConfig
import com.github.woodsmarshes.chat.core.model.ui.MessageState
import com.github.woodsmarshes.chat.core.model.ui.MessageUiModel
import com.github.woodsmarshes.chat.core.ui.components.avatar.UserAvatar
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import com.github.woodsmarshes.chat.core.ui.theme.LocalBubbleColors
import com.github.woodsmarshes.chat.core.ui.theme.LocalThemeConfig
import com.github.woodsmarshes.chat.core.ui.theme.isMiuixTheme
import com.github.woodsmarshes.chat.core.ui.utils.formatMessageTime
import com.github.woodsmarshes.chat.resources.Res
import com.github.woodsmarshes.chat.resources.ic_reply
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.roundToInt

val REPLY_THRESHOLD_DP = 56.dp

/**
 * Computes a Telegram-style rubber-banded horizontal offset (<= 0f) for swipe-to-reply.
 */
private fun calculateDampedSwipeOffset(rawDragX: Float, thresholdPx: Float): Float {
    val clamped = rawDragX.coerceAtMost(0f)
    val absDrag = -clamped
    return if (absDrag <= thresholdPx) {
        clamped
    } else {
        val extra = absDrag - thresholdPx
        -(thresholdPx + extra * 0.25f).coerceAtMost(thresholdPx * 1.35f)
    }
}

/**
 * Telegram-style reply indicator anchored to the right edge of the message row.
 *
 * Uses [graphicsLayer] for scale, alpha, and slide-in translation so it never
 * enters/leaves composition or alters sibling layout bounds during a swipe.
 */
@Composable
private fun SwipeReplyIndicator(
    progress: Float,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val slideOffsetPx = with(density) { 16.dp.toPx() }
    val clamped = progress.coerceIn(0f, 1f)
    val scale = if (clamped <= 0.01f) 0f else (0.4f + 0.6f * clamped)
    val isMiuix = isMiuixTheme()

    val bgColor = when {
        active && isMiuix -> MiuixTheme.colorScheme.primary
        active -> MaterialTheme.colorScheme.primary
        isMiuix -> MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
    }
    val iconColor = when {
        active && isMiuix -> MiuixTheme.colorScheme.onPrimary
        active -> MaterialTheme.colorScheme.onPrimary
        isMiuix -> MiuixTheme.colorScheme.onSurfaceVariantActions
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .size(34.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = clamped
                translationX = (1f - clamped) * slideOffsetPx
            }
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_reply),
            contentDescription = LocalStrings.current.reply,
            tint = iconColor,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * Container for the user's own messages.
 *
 * Right-aligned. Swipe-left triggers reply (Telegram style).
 * Entire row is selectable in multi-select mode.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OwnMessageContainer(
    message: MessageUiModel,
    modifier: Modifier = Modifier,
    onReply: (() -> Unit)? = null,
    selectionActive: Boolean = false,
    selected: Boolean = false,
    onToggleSelection: (() -> Unit)? = null,
    menuContent: (@Composable (dismiss: () -> Unit) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { REPLY_THRESHOLD_DP.toPx() }
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    var rawDragX by remember { mutableFloatStateOf(0f) }
    var hasHapticPlayed by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var pressOffset by remember { mutableStateOf(Offset.Zero) }

    val currentMenuContent by rememberUpdatedState(menuContent)
    val currentToggleSelection by rememberUpdatedState(onToggleSelection)
    val currentOnReply by rememberUpdatedState(onReply)

    val isMiuix = isMiuixTheme()
    val selectedBg = if (isMiuix) {
        MiuixTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    }

    val rowModifier = modifier
        .fillMaxWidth()
        .background(if (selected) selectedBg else Color.Transparent)
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Press) {
                        event.changes.firstOrNull()?.position?.let { pos ->
                            pressOffset = pos
                        }
                    }
                }
            }
        }
        .then(
            if (selectionActive) {
                Modifier
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { currentToggleSelection?.invoke() },
                    )
            } else if (onReply != null) {
                Modifier.pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            rawDragX = offsetX.value
                            hasHapticPlayed = false
                        },
                        onDragEnd = {
                            val triggered = abs(offsetX.value) >= thresholdPx
                            rawDragX = 0f
                            hasHapticPlayed = false
                            coroutineScope.launch {
                                if (triggered) {
                                    currentOnReply?.invoke()
                                }
                                offsetX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium,
                                    ),
                                )
                            }
                        },
                        onDragCancel = {
                            rawDragX = 0f
                            hasHapticPlayed = false
                            coroutineScope.launch {
                                offsetX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium,
                                    ),
                                )
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            rawDragX = (rawDragX + dragAmount).coerceAtMost(0f)
                            val targetOffset = calculateDampedSwipeOffset(rawDragX, thresholdPx)
                            val passed = abs(targetOffset) >= thresholdPx
                            if (passed && !hasHapticPlayed) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                hasHapticPlayed = true
                            } else if (!passed && hasHapticPlayed) {
                                hasHapticPlayed = false
                            }
                            coroutineScope.launch {
                                offsetX.snapTo(targetOffset)
                            }
                        },
                    )
                }
            } else {
                Modifier
            }
        )
        .padding(horizontal = 12.dp, vertical = 4.dp)

    val progress = (-offsetX.value / thresholdPx).coerceIn(0f, 1f)
    val reachedThreshold = -offsetX.value >= thresholdPx

    Box(
        modifier = rowModifier,
    ) {
        if (selectionActive) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp)
                    .size(22.dp),
            )
        }

        // Background layer: Reply indicator anchored to the right edge of the row
        if (onReply != null && !selectionActive) {
            SwipeReplyIndicator(
                progress = progress,
                active = reachedThreshold,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp),
            )
        }

        // Foreground layer: Own message bubble translated purely in graphicsLayer
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .widthIn(max = 420.dp)
                .graphicsLayer {
                    translationX = offsetX.value
                }
                .then(
                    if (!selectionActive && (menuContent != null || onToggleSelection != null)) {
                        Modifier
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { /* Keep left click clean on desktop */ },
                                onLongClick = {
                                    if (menuContent != null) menuExpanded = true
                                    else currentToggleSelection?.invoke()
                                },
                            )
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.type == PointerEventType.Press) {
                                            when {
                                                // Desktop right-click: message menu at cursor
                                                event.buttons.isSecondaryPressed && currentMenuContent != null -> {
                                                    menuExpanded = true
                                                    event.changes.forEach { it.consume() }
                                                }
                                                // Desktop Ctrl+click: toggle selection
                                                event.changes.firstOrNull()?.pressed == true &&
                                                    event.keyboardModifiers.isCtrlPressed &&
                                                    currentToggleSelection != null -> {
                                                    currentToggleSelection?.invoke()
                                                    event.changes.forEach { it.consume() }
                                                }
                                            }
                                        }
                                    }
                                }
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

        if (menuContent != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        IntOffset(
                            x = pressOffset.x.roundToInt(),
                            y = pressOffset.y.roundToInt(),
                        )
                    },
            ) {
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    menuContent { menuExpanded = false }
                }
            }
        }
    }
}

/**
 * Container for other users' messages.
 *
 * Left-aligned with avatar and sender name. Swipe-left triggers reply (Telegram style).
 * Entire row is selectable in multi-select mode.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OtherMessageContainer(
    message: MessageUiModel,
    modifier: Modifier = Modifier,
    showAvatar: Boolean = true,
    showSenderName: Boolean = true,
    onReply: (() -> Unit)? = null,
    onAvatarClick: (() -> Unit)? = null,
    selectionActive: Boolean = false,
    selected: Boolean = false,
    onToggleSelection: (() -> Unit)? = null,
    menuContent: (@Composable (dismiss: () -> Unit) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { REPLY_THRESHOLD_DP.toPx() }
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    var rawDragX by remember { mutableFloatStateOf(0f) }
    var hasHapticPlayed by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var pressOffset by remember { mutableStateOf(Offset.Zero) }

    val currentMenuContent by rememberUpdatedState(menuContent)
    val currentToggleSelection by rememberUpdatedState(onToggleSelection)
    val currentOnReply by rememberUpdatedState(onReply)

    val sender = message.sender
    val isMiuix = isMiuixTheme()
    val selectedBg = if (isMiuix) {
        MiuixTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    }

    val rowModifier = modifier
        .fillMaxWidth()
        .background(if (selected) selectedBg else Color.Transparent)
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Press) {
                        event.changes.firstOrNull()?.position?.let { pos ->
                            pressOffset = pos
                        }
                    }
                }
            }
        }
        .then(
            if (selectionActive) {
                Modifier
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { currentToggleSelection?.invoke() },
                    )
            } else if (onReply != null) {
                Modifier.pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            rawDragX = offsetX.value
                            hasHapticPlayed = false
                        },
                        onDragEnd = {
                            val triggered = abs(offsetX.value) >= thresholdPx
                            rawDragX = 0f
                            hasHapticPlayed = false
                            coroutineScope.launch {
                                if (triggered) {
                                    currentOnReply?.invoke()
                                }
                                offsetX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium,
                                    ),
                                )
                            }
                        },
                        onDragCancel = {
                            rawDragX = 0f
                            hasHapticPlayed = false
                            coroutineScope.launch {
                                offsetX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium,
                                    ),
                                )
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            rawDragX = (rawDragX + dragAmount).coerceAtMost(0f)
                            val targetOffset = calculateDampedSwipeOffset(rawDragX, thresholdPx)
                            val passed = abs(targetOffset) >= thresholdPx
                            if (passed && !hasHapticPlayed) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                hasHapticPlayed = true
                            } else if (!passed && hasHapticPlayed) {
                                hasHapticPlayed = false
                            }
                            coroutineScope.launch {
                                offsetX.snapTo(targetOffset)
                            }
                        },
                    )
                }
            } else {
                Modifier
            }
        )
        .padding(horizontal = 12.dp, vertical = 4.dp)

    val progress = (-offsetX.value / thresholdPx).coerceIn(0f, 1f)
    val reachedThreshold = -offsetX.value >= thresholdPx

    Box(
        modifier = rowModifier,
    ) {
        // Background layer: Reply indicator anchored to the right edge of the row
        if (onReply != null && !selectionActive) {
            SwipeReplyIndicator(
                progress = progress,
                active = reachedThreshold,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp),
            )
        }

        // Foreground layer: Avatar + Bubble move left together as one unit
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .graphicsLayer {
                    translationX = offsetX.value
                },
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Top,
        ) {
            if (selectionActive) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .padding(end = 8.dp)
                        .size(22.dp),
                )
            }

            if (showAvatar) {
                UserAvatar(
                    name = sender?.displayName ?: sender?.username ?: "?",
                    avatarUrl = sender?.avatarUrl,
                    size = 36.dp,
                    onClick = if (!selectionActive) onAvatarClick else null,
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .then(
                        if (!selectionActive && (menuContent != null || onToggleSelection != null)) {
                            Modifier
                                .combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { /* Keep left click clean on desktop */ },
                                    onLongClick = {
                                        if (menuContent != null) menuExpanded = true
                                        else currentToggleSelection?.invoke()
                                    },
                                )
                                .pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            if (event.type == PointerEventType.Press) {
                                                when {
                                                    // Desktop right-click: message menu at cursor
                                                    event.buttons.isSecondaryPressed && currentMenuContent != null -> {
                                                        menuExpanded = true
                                                        event.changes.forEach { it.consume() }
                                                    }
                                                    // Desktop Ctrl+click: toggle selection
                                                    event.changes.firstOrNull()?.pressed == true &&
                                                        event.keyboardModifiers.isCtrlPressed &&
                                                        currentToggleSelection != null -> {
                                                        currentToggleSelection?.invoke()
                                                        event.changes.forEach { it.consume() }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                        } else Modifier
                    ),
            ) {
                if (showSenderName && sender != null) {
                    val s = sender
                    Box(
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = !selectionActive && onAvatarClick != null,
                            onClick = { onAvatarClick?.invoke() },
                        )
                    ) {
                        MessageSenderName(
                            name = s.displayName ?: s.username,
                            role = s.role,
                        )
                    }
                }
                content()
                MessageTimestamp(
                    createdAt = message.createdAt,
                    sendStatus = message.sendStatus,
                    isOwnMessage = false,
                )
            }
        }

        if (menuContent != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        IntOffset(
                            x = pressOffset.x.roundToInt(),
                            y = pressOffset.y.roundToInt(),
                        )
                    },
            ) {
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    menuContent { menuExpanded = false }
                }
            }
        }
    }
}

@Composable
fun MessageSenderName(
    name: String,
    modifier: Modifier = Modifier,
    role: ConversationRole? = null,
) {
    val bubbleColors = LocalBubbleColors.current
    Row(
        modifier = modifier.padding(bottom = 3.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            color = bubbleColors.senderNameColor,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (role == ConversationRole.OWNER || role == ConversationRole.ADMIN) {
            Spacer(Modifier.width(5.dp))
            RoleMicroBadge(role = role)
        }
    }
}

/**
 * Refined micro-pill badge for group Owner ("群主") and Admin ("管理员").
 * Ordinary members (`MEMBER` / `PARTICIPANT`) are intentionally not badged.
 */
@Composable
fun RoleMicroBadge(
    role: ConversationRole?,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val label = when (role) {
        ConversationRole.OWNER -> strings.roleOwner
        ConversationRole.ADMIN -> strings.roleAdmin
        else -> return
    }

    val themeConfig = LocalThemeConfig.current
    val isDark = when (themeConfig.darkThemeConfig) {
        DarkThemeConfig.LIGHT -> false
        DarkThemeConfig.DARK -> true
        DarkThemeConfig.FOLLOW_SYSTEM -> isSystemInDarkTheme()
    }

    val (bgColor, textColor) = when (role) {
        ConversationRole.OWNER -> {
            val bg = Color(0xFFF59E0B).copy(alpha = if (isDark) 0.20f else 0.14f)
            val fg = if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706)
            bg to fg
        }
        ConversationRole.ADMIN -> {
            val primary = MaterialTheme.colorScheme.primary
            primary.copy(alpha = if (isDark) 0.18f else 0.12f) to primary
        }
        else -> return
    }

    Text(
        text = label,
        color = textColor,
        style = LocalTextStyle.current.copy(
            fontSize = 10.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Medium,
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
        ),
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .padding(horizontal = 5.dp, vertical = 1.5.dp),
    )
}

@Composable
fun MessageTimestamp(
    createdAt: kotlin.time.Instant,
    sendStatus: MessageState,
    isOwnMessage: Boolean,
    modifier: Modifier = Modifier,
) {
    val bubbleColors = LocalBubbleColors.current
    val strings = LocalStrings.current
    val timeStr = formatMessageTime(createdAt, strings)

    val statusStr = when (deliveryPresentation(sendStatus, LocalMessageConnection.current, isOwnMessage)) {
        DeliveryPresentation.Waiting -> strings.messageWaitingConnection
        DeliveryPresentation.Sending -> strings.messageSending
        DeliveryPresentation.Failed -> strings.messageSendFailed
        DeliveryPresentation.Sent -> strings.sendCompleted
        DeliveryPresentation.Hidden -> ""
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
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


