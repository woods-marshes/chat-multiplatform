package com.github.woodsmarshes.chat.core.ui.components.input

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigationevent.compose.NavigationBackHandler
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
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val textFieldFocusRequester = remember { FocusRequester() }
    val localDensity = LocalDensity.current
    val ime = WindowInsets.ime

    var keyboardHeightDp by remember {
        mutableStateOf(0.dp)
    }
    var currentSelector by remember { mutableStateOf<InputSelector>(InputSelector.NONE) }

    LaunchedEffect(key1 = localDensity) {
        snapshotFlow {
            ime.getBottom(density = localDensity)
        }.collect { bottomInset ->
            val realtimeKeyboardHeightDp = (bottomInset / localDensity.density).dp
            // 记录最大键盘高度
            keyboardHeightDp = maxOf(realtimeKeyboardHeightDp, keyboardHeightDp)

            if (realtimeKeyboardHeightDp == keyboardHeightDp) {
                currentSelector = InputSelector.NONE
                keyboardController?.show()
            }
        }
    }


    val panelMaxHeight = if (keyboardHeightDp <= 0.dp) {
        270.dp
    } else {
        keyboardHeightDp
    }

    Column(
        modifier = modifier
            .background(bubbleColors.inputBarBackground),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            space = 12.dp,
            alignment = Alignment.Top
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    if (currentSelector == InputSelector.IMAGE) {
                        // 如果已经是附件面板，点击则关闭面板并重新聚焦输入框
                        // 重新聚焦输入框，让键盘自动升起
                        currentSelector = InputSelector.NONE
                    } else {
                        // 否则展示附件面板，关闭键盘并清除焦点
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                        currentSelector = InputSelector.IMAGE
                    }
                },
                enabled = enabled,
            ) {
                Icon(
                    imageVector = MiuixIcons.AddCircle,
                    contentDescription = "附件",
                    tint = if (currentSelector == InputSelector.IMAGE) bubbleColors.inputSendIconTint
                    else bubbleColors.inputIconTint,
                )
            }

            // 表情面板切换按钮（新增，原版漏掉了 Emoji 触发入口）
            IconButton(
                onClick = {
                    if (currentSelector == InputSelector.EMOJI) {
                        currentSelector = InputSelector.NONE
                    } else {
                        // 否则展示表情面板，清除焦点并隐藏键盘
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                        currentSelector = InputSelector.EMOJI
                    }
                },
                enabled = enabled,
            ) {
                Icon(
                    imageVector = Icons.Default.SentimentSatisfiedAlt,
                    contentDescription = "表情",
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

        when (currentSelector) {
            InputSelector.NONE -> {
                // 面板不显示时，使用 Spacer 占据键盘+导航栏的空间
                KeyboardSpace(modifier = Modifier)
            }
            InputSelector.EMOJI -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(
                            min = keyboardHeightDp,
                            max = panelMaxHeight
                        )
                        .background(bubbleColors.panelBackground)
                        .navigationBarsPadding()
                ) {
                    EmojiPanel(
                        onEmojiSelected = { emoji ->
                            val newText = value.text.substring(0, value.selection.start) +
                                    emoji +
                                    value.text.substring(value.selection.end)
                            val newCursor = value.selection.start + emoji.length
                            onValueChange(
                                value.copy(
                                    text = newText,
                                    selection = TextRange(newCursor)
                                )
                            )
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            InputSelector.IMAGE, InputSelector.FILE, InputSelector.AUDIO -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(
                            min = keyboardHeightDp,
                            max = panelMaxHeight
                        )
                        .background(bubbleColors.panelBackground)
                        .navigationBarsPadding()
                ) {
                    MediaActionPanel(
                        onActionClick = { selector ->
                            when (selector) {
                                InputSelector.IMAGE -> onImageClick?.invoke()
                                InputSelector.FILE -> onFileClick?.invoke()
                                InputSelector.AUDIO -> onVoiceClick?.invoke()
                                else -> {}
                            }
                            // 点击功能后关闭面板
                            currentSelector = InputSelector.NONE
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyboardSpace(modifier: Modifier) {
    Spacer(
        modifier = modifier
            .windowInsetsPadding(
                insets = WindowInsets.navigationBars
                    .union(insets = WindowInsets.ime)
            )
    )
}
