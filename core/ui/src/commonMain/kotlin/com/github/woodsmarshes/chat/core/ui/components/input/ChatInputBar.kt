package com.github.woodsmarshes.chat.core.ui.components.input

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.woodsmarshes.chat.core.ui.theme.LocalBubbleColors
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.AddCircle
import top.yukonga.miuix.kmp.icon.extended.Mic
import top.yukonga.miuix.kmp.icon.extended.Send

@Composable
fun ChatInputBar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    onImageClick: (() -> Unit)? = null,
    onFileClick: (() -> Unit)? = null,
    onVoiceClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val bubbleColors = LocalBubbleColors.current
    var currentSelector by remember { mutableStateOf(InputSelector.NONE) }
    val textFieldFocusRequester = remember { FocusRequester() }

    LaunchedEffect(currentSelector) {
        if (currentSelector != InputSelector.NONE) {
            textFieldFocusRequester.freeFocus()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(bubbleColors.inputBarBackground),
    ) {
        AnimatedVisibility(
            visible = currentSelector != InputSelector.NONE,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            when (currentSelector) {
                InputSelector.EMOJI -> EmojiPanel(
                    onEmojiSelected = { emoji ->
                        val newText = value.text.substring(0, value.selection.start) +
                            emoji +
                            value.text.substring(value.selection.end)
                        val newCursor = value.selection.start + emoji.length
                        onValueChange(value.copy(text = newText, selection = androidx.compose.ui.text.TextRange(newCursor)))
                    },
                )
                InputSelector.IMAGE -> MediaActionPanel(
                    onActionClick = { selector ->
                        when (selector) {
                            InputSelector.IMAGE -> onImageClick?.invoke()
                            InputSelector.FILE -> onFileClick?.invoke()
                            InputSelector.AUDIO -> onVoiceClick?.invoke()
                            else -> {}
                        }
                        currentSelector = InputSelector.NONE
                    },
                )
                InputSelector.FILE -> MediaActionPanel(
                    onActionClick = { selector ->
                        if (selector == InputSelector.FILE) onFileClick?.invoke()
                        currentSelector = InputSelector.NONE
                    },
                )
                InputSelector.AUDIO -> MediaActionPanel(
                    onActionClick = { selector ->
                        if (selector == InputSelector.AUDIO) onVoiceClick?.invoke()
                        currentSelector = InputSelector.NONE
                    },
                )
                InputSelector.NONE -> { /* unreachable */ }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    currentSelector = if (currentSelector == InputSelector.NONE) InputSelector.IMAGE
                    else InputSelector.NONE
                },
                enabled = enabled,
            ) {
                Icon(
                    imageVector = MiuixIcons.AddCircle,
                    contentDescription = "附件",
                    tint = if (currentSelector != InputSelector.NONE) bubbleColors.inputSendIconTint
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
                            text = "输入消息...",
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
                    contentDescription = if (hasText) "发送" else "语音",
                    tint = if (hasText) bubbleColors.inputSendIconTint
                    else bubbleColors.inputIconTint,
                )
            }
        }
    }
}
