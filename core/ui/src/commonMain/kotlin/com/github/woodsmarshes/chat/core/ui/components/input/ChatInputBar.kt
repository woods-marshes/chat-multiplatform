package com.github.woodsmarshes.chat.core.ui.components.input

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.onConsumedWindowInsetsChanged
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.navigationevent.NavigationEventInfo
import com.github.woodsmarshes.chat.core.model.AudioContent
import com.github.woodsmarshes.chat.core.model.FileContent
import com.github.woodsmarshes.chat.core.model.ImageContent
import com.github.woodsmarshes.chat.core.model.TextContent
import com.github.woodsmarshes.chat.core.model.VideoContent
import com.github.woodsmarshes.chat.core.model.ui.MessageUiModel
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import com.github.woodsmarshes.chat.core.ui.theme.LocalBubbleColors
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.icon.extended.AddCircle
import top.yukonga.miuix.kmp.icon.extended.Mic
import top.yukonga.miuix.kmp.icon.extended.Send
import top.yukonga.miuix.kmp.icon.MiuixIcons
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun ChatInputBar(
    value: TextFieldValue,
    modifier: Modifier = Modifier,
    onValueChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    replyTo: MessageUiModel? = null,
    onClearReply: (() -> Unit)? = null,
    onImageClick: (() -> Unit)? = null,
    onFileClick: (() -> Unit)? = null,
    onVoiceClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val bubbleColors = LocalBubbleColors.current
    val strings = LocalStrings.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val textFieldFocusRequester = remember { FocusRequester() }
    val localDensity = LocalDensity.current
    val ime = WindowInsets.ime
    val navigationBars = WindowInsets.navigationBars
    val animation = rememberImeAnimationInsets()
    var windowSize by remember { mutableStateOf(IntSize.Zero) }
    val panelState = remember(localDensity, windowSize) { InputPanelState() }
    val currentSelector = panelState.selector

    LaunchedEffect(panelState, ime, animation, localDensity) {
        snapshotFlow {
            Triple(
                ime.getBottom(localDensity),
                animation.source.getBottom(localDensity),
                animation.target.getBottom(localDensity),
            )
        }.collect { (current, source, target) ->
            panelState.observeIme(current, source, target)
        }
    }

    LaunchedEffect(panelState, panelState.awaitingKeyboard) {
        if (panelState.awaitingKeyboard) {
            textFieldFocusRequester.requestFocus()
            keyboardController?.show()
            // Failure guard only: successful handoff is driven by actual IME geometry.
            delay(1500.milliseconds)
            panelState.abandonKeyboardRequest()
        }
    }

    fun openPanel(selector: InputSelector) {
        panelState.openPanel(
            selector = selector,
            currentImePx = maxOf(
                ime.getBottom(localDensity),
                animation.target.getBottom(localDensity),
            ),
            fallbackPx = with(localDensity) { 270.dp.roundToPx() },
        )
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = currentSelector != InputSelector.NONE || panelState.awaitingKeyboard,
        onBackCompleted = {
            panelState.close()
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        },
    )
    Column(
        modifier = modifier
            .background(bubbleColors.inputBarBackground)
            .onGloballyPositioned { coordinates ->
                windowSize = coordinates.findRootCoordinates().size
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        replyTo?.let { reply ->
            ReplyPreview(
                message = reply,
                onClear = { onClearReply?.invoke() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    if (currentSelector == InputSelector.IMAGE) {
                        panelState.requestKeyboard()
                    } else {
                        openPanel(InputSelector.IMAGE)
                    }
                },
                enabled = enabled,
            ) {
                Icon(
                    imageVector = MiuixIcons.AddCircle,
                    contentDescription = strings.attachmentCd,
                    tint = if (currentSelector == InputSelector.IMAGE) bubbleColors.inputSendIconTint
                    else bubbleColors.inputIconTint,
                )
            }

            // 表情面板切换按钮（新增，原版漏掉了 Emoji 触发入口）
            IconButton(
                onClick = {
                    if (currentSelector == InputSelector.EMOJI) {
                        panelState.requestKeyboard()
                    } else {
                        openPanel(InputSelector.EMOJI)
                    }
                },
                enabled = enabled,
            ) {
                Icon(
                    imageVector = Icons.Default.SentimentSatisfiedAlt,
                    contentDescription = strings.emojiCd,
                    tint = if (currentSelector == InputSelector.EMOJI) bubbleColors.inputSendIconTint
                    else bubbleColors.inputIconTint,
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(textFieldFocusRequester)
                    .onFocusChanged { if (it.isFocused) panelState.requestKeyboard() }
                    .clip(RoundedCornerShape(20.dp))
                    .background(bubbleColors.inputFieldBackground)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                textStyle = TextStyle(
                    color = bubbleColors.inputFieldContent,
                    fontSize = 15.sp,
                ),
                cursorBrush = SolidColor(bubbleColors.inputSendIconTint),
                enabled = enabled,
                maxLines = 5,
                decorationBox = { innerTextField ->
                    if (value.text.isEmpty()) {
                        androidx.compose.material3.Text(
                            text = strings.inputPlaceholder,
                            style = TextStyle(
                                color = bubbleColors.inputFieldPlaceholder,
                                fontSize = 15.sp,
                            ),
                        )
                    }
                    innerTextField()
                },
            )

            Spacer(modifier = Modifier.width(4.dp))

            val hasText = value.text.isNotBlank()
            IconButton(
                onClick = {
                    if (value.text.isNotBlank()) onSend() else onVoiceClick?.invoke()
                },
                enabled = enabled,
            ) {
                Icon(
                    imageVector = if (hasText) MiuixIcons.Send else MiuixIcons.Mic,
                    contentDescription = if (hasText) strings.sendCd else strings.voiceCd,
                    tint = if (hasText) bubbleColors.inputSendIconTint
                    else bubbleColors.inputIconTint,
                )
            }
        }

        InputAreaHost(
            state = panelState,
            ime = ime,
            animation = animation,
            navigationBars = navigationBars,
            modifier = Modifier.fillMaxWidth().background(bubbleColors.panelBackground),
        ) {
            when (currentSelector) {
                InputSelector.NONE -> Unit
                InputSelector.EMOJI -> EmojiPanel(
                    onEmojiSelected = { emoji ->
                        val start = value.selection.min
                        val end = value.selection.max
                        onValueChange(
                            value.copy(
                                text = value.text.replaceRange(start, end, emoji),
                                selection = TextRange(start + emoji.length),
                                composition = null,
                            )
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                else -> MediaActionPanel(
                    onActionClick = { selector ->
                        panelState.close()
                        when (selector) {
                            InputSelector.IMAGE -> onImageClick?.invoke()
                            InputSelector.FILE -> onFileClick?.invoke()
                            InputSelector.AUDIO -> onVoiceClick?.invoke()
                            else -> Unit
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** Reads animated geometry during measurement rather than recomposing the message screen. */
@Composable
private fun InputAreaHost(
    state: InputPanelState,
    ime: WindowInsets,
    animation: ImeAnimationInsets,
    navigationBars: WindowInsets,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var consumedBottom by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    Layout(
        content = content,
        modifier = modifier.onConsumedWindowInsetsChanged {
            consumedBottom = it.getBottom(density)
        },
    ) { measurables, constraints ->
        val total = state.occupiedHeight(
            current = ime.getBottom(this),
            source = animation.source.getBottom(this),
            target = animation.target.getBottom(this),
            navigation = navigationBars.getBottom(this),
        )
        val height = constraints.constrainHeight((total - consumedBottom).coerceAtLeast(0))
        val navigation = (navigationBars.getBottom(this) - consumedBottom).coerceAtLeast(0)
        val contentHeight = (height - navigation).coerceAtLeast(0)
        val width = constraints.maxWidth
        val children = measurables.map {
            it.measure(Constraints.fixed(width, contentHeight))
        }
        layout(width, height) {
            children.forEach { it.placeRelative(0, 0) }
        }
    }
}

/** Compact preview of the message being replied to, shown above the input field. */
@Composable
private fun ReplyPreview(
    message: MessageUiModel,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bubbleColors = LocalBubbleColors.current
    val strings = LocalStrings.current
    val senderName = message.sender?.displayName ?: message.sender?.username ?: ""
    val excerpt = when (val content = message.content) {
        is TextContent -> content.text
        is ImageContent -> strings.messagePreviewImage
        is VideoContent -> strings.messagePreviewVideo
        is AudioContent -> strings.messagePreviewAudio
        is FileContent -> strings.messagePreviewFile
        else -> ""
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bubbleColors.inputFieldBackground)
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${strings.reply} $senderName",
                style = TextStyle(
                    color = bubbleColors.inputFieldContent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = excerpt,
                style = TextStyle(
                    color = bubbleColors.inputFieldPlaceholder,
                    fontSize = 12.sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onClear) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = strings.dismiss,
                tint = bubbleColors.inputIconTint,
            )
        }
    }
}
