package com.github.woodsmarshes.chat.components.widgets.message.item

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.github.woodsmarshes.chat.components.widgets.CircularProgressIndicator
import com.github.woodsmarshes.chat.components.widgets.IconButton
import com.github.woodsmarshes.chat.components.widgets.message.MessageStatusIndicator
import com.github.woodsmarshes.chat.components.widgets.message.messageColors
import com.github.woodsmarshes.chat.model.ReplyPreview
import com.github.woodsmarshes.chat.model.VideoMessage
import com.github.woodsmarshes.chat.network.api.Endpoints.toFullUrl
import com.varabyte.kobweb.browser.dom.observers.IntersectionObserver
import com.varabyte.kobweb.compose.css.Appearance
import com.varabyte.kobweb.compose.css.ObjectFit
import com.varabyte.kobweb.compose.css.Overflow
import com.varabyte.kobweb.compose.css.Transition
import com.varabyte.kobweb.compose.css.functions.clamp
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
import com.varabyte.kobweb.compose.ui.modifiers.appearance
import com.varabyte.kobweb.compose.ui.modifiers.aspectRatio
import com.varabyte.kobweb.compose.ui.modifiers.background
import com.varabyte.kobweb.compose.ui.modifiers.backgroundColor
import com.varabyte.kobweb.compose.ui.modifiers.borderRadius
import com.varabyte.kobweb.compose.ui.modifiers.color
import com.varabyte.kobweb.compose.ui.modifiers.fillMaxSize
import com.varabyte.kobweb.compose.ui.modifiers.fillMaxWidth
import com.varabyte.kobweb.compose.ui.modifiers.fontSize
import com.varabyte.kobweb.compose.ui.modifiers.height
import com.varabyte.kobweb.compose.ui.modifiers.margin
import com.varabyte.kobweb.compose.ui.modifiers.maxHeight
import com.varabyte.kobweb.compose.ui.modifiers.maxWidth
import com.varabyte.kobweb.compose.ui.modifiers.minWidth
import com.varabyte.kobweb.compose.ui.modifiers.objectFit
import com.varabyte.kobweb.compose.ui.modifiers.onClick
import com.varabyte.kobweb.compose.ui.modifiers.onContextMenu
import com.varabyte.kobweb.compose.ui.modifiers.onMouseEnter
import com.varabyte.kobweb.compose.ui.modifiers.onMouseLeave
import com.varabyte.kobweb.compose.ui.modifiers.opacity
import com.varabyte.kobweb.compose.ui.modifiers.outline
import com.varabyte.kobweb.compose.ui.modifiers.overflow
import com.varabyte.kobweb.compose.ui.modifiers.padding
import com.varabyte.kobweb.compose.ui.modifiers.size
import com.varabyte.kobweb.compose.ui.modifiers.transition
import com.varabyte.kobweb.compose.ui.modifiers.width
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.silk.components.forms.Button
import com.varabyte.kobweb.silk.components.forms.Input
import com.varabyte.kobweb.silk.components.graphics.Image
import com.varabyte.kobweb.silk.components.icons.fa.FaExpand
import com.varabyte.kobweb.silk.components.icons.fa.FaPause
import com.varabyte.kobweb.silk.components.icons.fa.FaPlay
import com.varabyte.kobweb.silk.components.text.SpanText
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.base
import com.varabyte.kobweb.silk.style.toModifier
import kotlinx.browser.window
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.css.AnimationTimingFunction
import org.jetbrains.compose.web.css.em
import org.jetbrains.compose.web.css.ms
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.vh
import org.jetbrains.compose.web.css.vw
import org.jetbrains.compose.web.dom.Video
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLVideoElement
import org.w3c.dom.events.Event
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.time.Instant

private data class VideoPlaybackState(
    val isPlaying: Boolean = false,
    val isLoading: Boolean = true,
    val progressFraction: Float = 0f,
    val durationMillis: Long = 0L,
    val isFinished: Boolean = false
)

@Composable
private fun VideoPlayer(
    src: String,
    modifier: Modifier = Modifier,
    showControls: Boolean,
    autoPlay: Boolean = false,
    onStateChange: (VideoPlaybackState) -> Unit,
    onElementReady: (HTMLVideoElement) -> Unit
) {
    var videoElement by remember { mutableStateOf<HTMLVideoElement?>(null) }
    val currentState = remember { mutableStateOf(VideoPlaybackState()) }

    val latestOnStateChange by rememberUpdatedState(onStateChange)

    fun updateState(builder: VideoPlaybackState.() -> VideoPlaybackState) {
        val newState = currentState.value.builder()
        if (newState != currentState.value) {
            currentState.value = newState
            latestOnStateChange(newState)
        }
    }

    DisposableEffect(videoElement) {
        val video = videoElement ?: return@DisposableEffect onDispose {}

        val onPlay: (Event) -> Unit = { updateState { copy(isPlaying = true, isLoading = false, isFinished = false) } }
        val onPause: (Event) -> Unit = { updateState { copy(isPlaying = false) } }
        val onEnded: (Event) -> Unit = { updateState { copy(isPlaying = false, isFinished = true, progressFraction = 1f) } }
        val onWaiting: (Event) -> Unit = { updateState { copy(isLoading = true) } }
        val onPlaying: (Event) -> Unit = { updateState { copy(isLoading = false) } }
        val onLoadedData: (Event) -> Unit = {
            updateState { copy(durationMillis = (video.duration * 1000).roundToLong(), isLoading = false) }
        }
        val onTimeUpdate: (Event) -> Unit = {
            if (video.duration > 0) {
                updateState { copy(progressFraction = (video.currentTime / video.duration).toFloat()) }
            }
        }

        val events = mapOf(
            "play" to onPlay, "pause" to onPause, "ended" to onEnded,
            "waiting" to onWaiting, "playing" to onPlaying, "loadeddata" to onLoadedData,
            "timeupdate" to onTimeUpdate
        )
        events.forEach { (name, listener) -> video.addEventListener(name, listener) }

        onDispose {
            events.forEach { (name, listener) -> video.removeEventListener(name, listener) }
        }
    }

    Video(
        attrs = modifier.toAttrs { // 1. 将所有外部传入的样式应用到 Video 标签
            attr("src", src)
            if (autoPlay) {
                attr("autoplay", "")
                attr("muted", "")
            }
            attr("playsinline", "")
            if (showControls) {
                attr("controls", "") // 可以显示浏览器原生控件，但我们用自定义的
            }
            attr("preload", "metadata")
        }
    ) {
        registerRefScope(
            ref { newVideoElement: HTMLVideoElement ->
                videoElement = newVideoElement
                onElementReady(newVideoElement)
            }
        )
    }
}

@Composable
fun VideoMessageBubble(
    modifier: Modifier = Modifier,
    message: VideoMessage,
    replyMessage: ReplyPreview? = null,
    onClick: () -> Unit = {},
    onLongPress: () -> Unit = {},
    onClickQuotedMessage: (replyMessageId: Long) -> Unit,
    onNavigateToFullScreen: (messageId: Long, videoUriString: String) -> Unit = { _,_-> },
    onContextMenu: () -> Unit = {},
    formatDateTime: (instant: Instant) -> String,
) {
    val formattedTime = remember(message.detail.timestamp) { formatDateTime(message.detail.timestamp) }
    val messageColors = messageColors(message.detail.isOwnMessage)
    val videoUrl = remember(message.videoUrl) { message.videoUrl.toFullUrl() }

    var showPlayer by remember { mutableStateOf(false) }
    var playbackState by remember { mutableStateOf(VideoPlaybackState()) }
    var videoElement by remember { mutableStateOf<HTMLVideoElement?>(null) }

    val bubbleRef = remember { mutableStateOf<HTMLElement?>(null) }

    var isHovered by remember { mutableStateOf(false) }

    var controlsVisible by remember { mutableStateOf(true) }
    var hideControlsTimeout by remember { mutableStateOf(-1) }

    DisposableEffect(bubbleRef.value, showPlayer) {
        val element = bubbleRef.value
        if (element == null || !showPlayer) return@DisposableEffect onDispose {}

        val observer = IntersectionObserver(
            options = IntersectionObserver.Options(thresholds = listOf(0.1))
        ) { entries ->
            entries.forEach { entry ->
                if (!entry.isIntersecting && playbackState.isPlaying) {
                    videoElement?.pause()
                }
            }
        }

        observer.observe(element)
        onDispose { observer.disconnect() }
    }

    LaunchedEffect(playbackState.isPlaying, isHovered) {
        window.clearTimeout(hideControlsTimeout)
        if (playbackState.isPlaying && !isHovered) {
            hideControlsTimeout = window.setTimeout({ controlsVisible = false }, 2000)
        } else {
            controlsVisible = true
        }
    }

    Box(
        modifier = modifier
            .borderRadius(12.px)
            .overflow(Overflow.Hidden)
            .onContextMenu {
                it.preventDefault();
                onContextMenu()
            },
        ref = ref { bubbleRef.value = it }
    ) {
        Column(
            modifier = Modifier.padding(4.px),
            verticalArrangement = Arrangement.spacedBy(4.px)
        ) {

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

            Box(
                modifier = Modifier
                    .width(clamp(150.px, 65.vw, 400.px))
                    .maxHeight(50.vh)
                    .aspectRatio((message.width?.toFloat() ?: 16f) / (message.height?.toFloat() ?: 9f))
                    .borderRadius(8.px)
                    .backgroundColor(Colors.Black)
                    .overflow(Overflow.Hidden)
                    .onMouseEnter { isHovered = true }
                    .onMouseLeave { isHovered = false }
                    .onClick {
                        if (showPlayer) {
                            controlsVisible = !controlsVisible
                        } else {
                            showPlayer = true
                        }
                    }
            ) {
                VideoPlayer(
                    src = videoUrl,
                    autoPlay = showPlayer,
                    showControls = false,
                    modifier = Modifier.fillMaxSize().objectFit(ObjectFit.Contain),
                    onStateChange = { playbackState = it },
                    onElementReady = { videoElement = it }
                )
                if (!showPlayer) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(56.px)
                            .backgroundColor(Colors.Black.copyf(alpha = 0.6f))
                            .borderRadius(50.percent),
                        contentAlignment = Alignment.Center
                    ) {
                        FaPlay(Modifier.fontSize(28.px).color(Colors.White))
                    }
                }

                if (showPlayer) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .opacity(if (controlsVisible) 1.0 else 0.0)
                            .transition(Transition.of("opacity", duration = 200.ms, timingFunction = AnimationTimingFunction.EaseOut))
                    ) {
                        VideoPlaybackSimpleControls(
                            modifier = Modifier.align(Alignment.Center),
                            isPlaying = playbackState.isPlaying,
                            isLoading = playbackState.isLoading,
                            onPlayPauseToggle = {
                                if (playbackState.isPlaying) videoElement?.pause() else videoElement?.play()
                            }
                        )

                        // 底部控制条
                        BottomControls(
                            modifier = Modifier.align(Alignment.BottomCenter),
                            progress = playbackState.progressFraction,
                            durationMillis = playbackState.durationMillis,
                            onSeek = { newProgress ->
                                videoElement?.let {
                                    it.currentTime = it.duration * newProgress
                                }
                            },
                            onFullScreenClick = {
                                videoElement?.requestFullscreen()
                            }
                        )
                    }
                }

                // --- 加载指示器 (如果正在加载此消息) ---
                if (playbackState.isLoading && showPlayer) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).size(48.px),
                    )
                }

                // --- 右下角的时间和状态 ---
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .margin(8.px)
                        .backgroundColor(Colors.Black.copyf(alpha = 0.6f))
                        .borderRadius(12.px)
                        .padding(leftRight = 8.px, topBottom = 4.px),
                    horizontalArrangement = Arrangement.spacedBy(4.px),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SpanText(
                        text = formattedTime,
                        modifier = Modifier.color(Colors.White.copyf(alpha = 0.95f)).fontSize(0.75.em)
                    )
                    if (message.detail.isOwnMessage) {
                        MessageStatusIndicator(state = message.detail.state, color = Colors.White.copyf(alpha = 0.95f))
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoPlaybackSimpleControls(
    modifier: Modifier = Modifier,
    isPlaying: Boolean,
    isLoading: Boolean,
    onPlayPauseToggle: () -> Unit
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (!isLoading) {
            Button(
                onClick = { onPlayPauseToggle() },
                modifier = Modifier
                    .size(56.px)
                    .backgroundColor(Colors.Black.copyf(alpha = 0.6f))
            ) {
                if (isPlaying) {
                    FaPause(Modifier.fontSize(28.px).color(Colors.White))
                } else {
                    FaPlay(Modifier.fontSize(28.px).color(Colors.White))
                }
            }
        }
    }
}

val VideoProgressStyle = CssStyle.base {
    Modifier
        .height(4.px)
        .borderRadius(2.px)
        .outline(0.px)
        .appearance(Appearance.None) // 移除默认样式
        .backgroundColor(Colors.White.copyf(alpha = 0.3f))
}
@Composable
private fun BottomControls(
    modifier: Modifier = Modifier,
    progress: Float,
    durationMillis: Long,
    onSeek: (Float) -> Unit,
    onFullScreenClick: () -> Unit
) {
    val durationText = remember(durationMillis) {
        if (durationMillis == 0L) "00:00" else {
            val totalSeconds = durationMillis / 1000
            val minutes = floor(totalSeconds / 60.0).toInt()
            val seconds = (totalSeconds % 60).toInt()
            "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Colors.Black.copyf(alpha = 0.4f))
            .padding(leftRight = 8.px, topBottom = 4.px),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.px)
    ) {
        SpanText(durationText, Modifier.color(Colors.White).fontSize(0.8.em))

        Input(
            type = InputType.Range,
            value = (progress * 100).toInt(),
            onValueChange = { onSeek(it?.toFloat()?.div(100f) ?: 0f) },
            modifier = VideoProgressStyle.toModifier()
                .weight(1)
                .height(24.px)
                .attrsModifier {
                    attr("min", "0")
                    attr("max", "100")
                    attr("step", "0.1")
                }
        )

        IconButton(onClick = onFullScreenClick) {
            FaExpand(Modifier.color(Colors.White))
        }
    }
}