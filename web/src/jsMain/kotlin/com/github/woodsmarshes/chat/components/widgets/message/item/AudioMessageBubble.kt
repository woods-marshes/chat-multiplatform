package com.github.woodsmarshes.chat.components.widgets.message.item

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.github.woodsmarshes.chat.components.widgets.CircularProgressIndicator
import com.github.woodsmarshes.chat.components.widgets.message.MessageStatusIndicator
import com.github.woodsmarshes.chat.components.widgets.message.messageColors
import com.github.woodsmarshes.chat.model.AudioMessage
import com.github.woodsmarshes.chat.model.ReplyPreview
import com.varabyte.kobweb.browser.dom.observers.IntersectionObserver
import com.varabyte.kobweb.compose.dom.ref
import com.varabyte.kobweb.compose.dom.registerRefScope
import com.varabyte.kobweb.compose.foundation.layout.Arrangement
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.foundation.layout.Row
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.attrsModifier
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.modifiers.backgroundColor
import com.varabyte.kobweb.compose.ui.modifiers.borderRadius
import com.varabyte.kobweb.compose.ui.modifiers.borderTop
import com.varabyte.kobweb.compose.ui.modifiers.display
import com.varabyte.kobweb.compose.ui.modifiers.fontSize
import com.varabyte.kobweb.compose.ui.modifiers.height
import com.varabyte.kobweb.compose.ui.modifiers.margin
import com.varabyte.kobweb.compose.ui.modifiers.minWidth
import com.varabyte.kobweb.compose.ui.modifiers.onClick
import com.varabyte.kobweb.compose.ui.modifiers.opacity
import com.varabyte.kobweb.compose.ui.modifiers.padding
import com.varabyte.kobweb.compose.ui.modifiers.size
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.silk.components.forms.Button
import com.varabyte.kobweb.silk.components.forms.Input
import com.varabyte.kobweb.silk.components.icons.fa.FaPause
import com.varabyte.kobweb.silk.components.icons.fa.FaPlay
import com.varabyte.kobweb.silk.components.text.SpanText
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.em
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.Audio
import org.w3c.dom.HTMLAudioElement
import org.w3c.dom.HTMLElement
import kotlin.time.Instant

private object GlobalAudioPlayer {
    var currentController: AudioPlayerController? = null
    var currentMessageId: Any? = null

    fun play(messageId: Any, controller: AudioPlayerController) {
        if (currentMessageId != messageId) {
            currentController?.pause()
            currentController = controller
            currentMessageId = messageId
            controller.play()
        } else {
            controller.play()
        }
    }

    fun pause(messageId: Any, controller: AudioPlayerController) {
        if (currentMessageId == messageId) {
            controller.pause()
        }
    }

    fun togglePlayPause(messageId: Any, controller: AudioPlayerController, isPlaying: Boolean) {
        if (isPlaying) {
            pause(messageId, controller)
        } else {
            play(messageId, controller)
        }
    }
}

@Composable
fun AudioMessageBubble(
    modifier: Modifier = Modifier,
    message: AudioMessage,
    replyMessage: ReplyPreview? = null,
    onClickQuotedMessage: (replyMessageId: Long) -> Unit,
    formatDateTime: (instant: Instant) -> String,
) {
    val formattedTime = remember(message.detail.timestamp) { formatDateTime(message.detail.timestamp) }
    val messageColors = messageColors(message.detail.isOwnMessage)
    val audioUrl = remember(message.audioUrl) { message.audioUrl }

    var playbackState by remember { mutableStateOf(AudioPlaybackState()) }

    val audioController = remember {
        AudioPlayerController { newState -> playbackState = newState }
    }

    // --- 可见性检测 ---
    val bubbleRef = remember { mutableStateOf<HTMLElement?>(null) }
    DisposableEffect(bubbleRef.value) {
        val element = bubbleRef.value ?: return@DisposableEffect onDispose {}

        val callback: (List<IntersectionObserver.Entry>) -> Unit = { entries ->
            entries.forEach { entry ->
                if (!entry.isIntersecting && playbackState.isPlaying) {
                    audioController.pause()
                }
            }
        }
        val options = IntersectionObserver.Options(thresholds = listOf(0.1))
        val observer = IntersectionObserver(options, callback)
        observer.observe(element)
        onDispose { observer.disconnect() }
    }

    DisposableEffect(Unit) {
        onDispose {
            audioController.detach()
        }
    }

    // 气泡根容器
    Box(
        modifier = modifier
            .backgroundColor(messageColors.bubbleColor)
            .borderRadius(8.px)
            .padding(4.px),
        ref = ref { bubbleRef.value = it }
    ) {
        Column {
            replyMessage?.let {
                Box(
                    Modifier
                        .backgroundColor(messageColors.replyBackgroundColor)
                        .borderRadius(6.px)
                        .onClick { onClickQuotedMessage(replyMessage.msgId) }
                ) {
                    com.github.woodsmarshes.chat.components.widgets.message.ReplyPreview(
                        replyToMessage = it,
                        contentColor = messageColors.replyContentColor
                    )
                }
            }

            // 使用 Flexbox 实现“小尾巴”布局
            Row(
                modifier = Modifier.padding(leftRight = 8.px, topBottom = 4.px),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 音频播放器 UI
                AudioPlayerControls(
                    modifier = Modifier.minWidth(200.px).weight(1),
                    isPlaying = playbackState.isPlaying,
                    isLoading = playbackState.isLoading,
                    progress = playbackState.progressFraction,
                    onPlayPauseClick = {
                        GlobalAudioPlayer.togglePlayPause(message.detail.msgId, audioController, playbackState.isPlaying)
                    },
                    onSeek = { progress -> audioController.seekTo(progress) }
                )

                // "小尾巴"
                Row(
                    modifier = Modifier.margin(left = 8.px),
                    horizontalArrangement = Arrangement.spacedBy(4.px),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SpanText(formattedTime, Modifier.fontSize(0.75.em).opacity(0.7))
                    if (message.detail.isOwnMessage) {
                        MessageStatusIndicator(state = message.detail.state)
                    }
                }
            }
        }
    }

    // 在 UI 树中放置一个不可见的 <audio> 元素
    Audio(attrs = Modifier.display(DisplayStyle.None).toAttrs { attr("src", audioUrl) }) {
        registerRefScope(ref { element: HTMLAudioElement ->
            audioController.attach(element)
        })
    }
}

@Composable
private fun AudioPlayerControls(
    modifier: Modifier = Modifier,
    isPlaying: Boolean,
    isLoading: Boolean,
    progress: Float,
    onPlayPauseClick: () -> Unit,
    onSeek: (Float) -> Unit,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.px)
    ) {
        Button(
            onClick = { onPlayPauseClick() },
            modifier = Modifier
                .size(36.px)
                .borderRadius(50.percent)
        ) {
            if (isLoading) {
                CircularProgressIndicator(Modifier.size(20.px).borderTop(color = Colors.White))
            } else {
                if (isPlaying) FaPause() else FaPlay()
            }
        }

        Input(
            type = InputType.Range,
            value = (progress * 100).toInt(),
            onValueChange = { onSeek(it?.toFloat()?.div(100f) ?: 0f) },
            modifier = Modifier
                .weight(1)
                .height(24.px)
                .attrsModifier {
                    attr("min", "0");
                    attr("max", "100");
                    attr("step", "0.1")
                }
        )
    }
}
