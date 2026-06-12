package com.github.woodsmarshes.chat.components.widgets.message

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.github.woodsmarshes.chat.components.widgets.IconButton
import com.github.woodsmarshes.chat.model.Message
import com.github.woodsmarshes.chat.model.viewmodel.chat.InputSelector
import com.github.woodsmarshes.chat.toSitePalette
import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.css.Overflow
import com.varabyte.kobweb.compose.css.Resize
import com.varabyte.kobweb.compose.css.TextOverflow
import com.varabyte.kobweb.compose.css.Transition
import com.varabyte.kobweb.compose.css.WhiteSpace
import com.varabyte.kobweb.compose.dom.ref
import com.varabyte.kobweb.compose.foundation.layout.Arrangement
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.foundation.layout.Row
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.modifiers.backgroundColor
import com.varabyte.kobweb.compose.ui.modifiers.border
import com.varabyte.kobweb.compose.ui.modifiers.borderRadius
import com.varabyte.kobweb.compose.ui.modifiers.borderTop
import com.varabyte.kobweb.compose.ui.modifiers.display
import com.varabyte.kobweb.compose.ui.modifiers.fillMaxSize
import com.varabyte.kobweb.compose.ui.modifiers.fillMaxWidth
import com.varabyte.kobweb.compose.ui.modifiers.fontSize
import com.varabyte.kobweb.compose.ui.modifiers.fontWeight
import com.varabyte.kobweb.compose.ui.modifiers.height
import com.varabyte.kobweb.compose.ui.modifiers.lineHeight
import com.varabyte.kobweb.compose.ui.modifiers.maxHeight
import com.varabyte.kobweb.compose.ui.modifiers.minHeight
import com.varabyte.kobweb.compose.ui.modifiers.onClick
import com.varabyte.kobweb.compose.ui.modifiers.outline
import com.varabyte.kobweb.compose.ui.modifiers.overflow
import com.varabyte.kobweb.compose.ui.modifiers.padding
import com.varabyte.kobweb.compose.ui.modifiers.resize
import com.varabyte.kobweb.compose.ui.modifiers.setVariable
import com.varabyte.kobweb.compose.ui.modifiers.size
import com.varabyte.kobweb.compose.ui.modifiers.textOverflow
import com.varabyte.kobweb.compose.ui.modifiers.transition
import com.varabyte.kobweb.compose.ui.modifiers.whiteSpace
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.silk.components.forms.Button
import com.varabyte.kobweb.silk.components.forms.InputStyle
import com.varabyte.kobweb.silk.components.forms.InputVars
import com.varabyte.kobweb.silk.components.forms.OutlinedInputVariant
import com.varabyte.kobweb.silk.components.forms.TextInput
import com.varabyte.kobweb.silk.components.icons.fa.FaFaceSmile
import com.varabyte.kobweb.silk.components.icons.fa.FaFileAudio
import com.varabyte.kobweb.silk.components.icons.fa.FaImage
import com.varabyte.kobweb.silk.components.icons.fa.FaPaperPlane
import com.varabyte.kobweb.silk.components.icons.fa.FaVideo
import com.varabyte.kobweb.silk.components.icons.fa.FaXmark
import com.varabyte.kobweb.silk.components.layout.SimpleGrid
import com.varabyte.kobweb.silk.components.layout.Surface
import com.varabyte.kobweb.silk.components.layout.numColumns
import com.varabyte.kobweb.silk.components.text.SpanText
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.selectors.hover
import com.varabyte.kobweb.silk.style.toModifier
import com.varabyte.kobweb.silk.theme.colors.ColorMode
import com.varabyte.kobweb.silk.theme.colors.palette.border
import com.varabyte.kobweb.silk.theme.colors.palette.toPalette
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.css.LineStyle
import org.jetbrains.compose.web.css.cssRem
import org.jetbrains.compose.web.css.ms
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.TextArea
import org.w3c.dom.HTMLAreaElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement

@Composable
fun ChatInputBar(
    modifier: Modifier = Modifier,
    // --- 状态 ---
    textMessageInputted: String,
    returnMessage: Message?,
    isSendEnabled: Boolean,
    currentInputSelector: InputSelector,
    // --- 回调 ---
    onUserInputChanged: (String) -> Unit,
    onInputSelectorChanged: (InputSelector) -> Unit,
    sendTextMessage: () -> Unit,
    appendEmoji: (String) -> Unit,
    onClearReturnMessage: () -> Unit,
    sendImageMessage: (InputType.File) -> Unit,
    sendVideoMessage: (InputType.File) -> Unit,
    sendAudioMessage: (InputType.File) -> Unit,
) {

    var fileInputRef by remember { mutableStateOf<HTMLInputElement?>(null) }

    var currentFileAccept by remember { mutableStateOf("") }
    val isEmojiSelectorVisible = currentInputSelector == InputSelector.EMOJI

//    LaunchedEffect(fileInputRef) {
//        val files = fileInputRef?.files ?: return@LaunchedEffect
//
//        val listener: (Event) -> Unit = {
//            files.let { fileList ->
//                if (fileList.length > 0) {
//                    val file = fileList.item(0)!!
//                    when {
//                        currentFileAccept.contains("image") -> sendImageMessage(file)
//                        currentFileAccept.contains("video") -> sendVideoMessage(file)
//                        currentFileAccept.contains("audio") -> sendAudioMessage(file)
//                    }
//                }
//            }
//            // 重置 input 的值，以便下次可以选择相同的文件
//            fileInputRef?.value = ""
//        }
//        fileInputRef.value.addEventListener("change", listener)
//    }

    val textInputRef = remember { mutableStateOf<HTMLInputElement?>(null) }
    val textAreaRef = remember { mutableStateOf<HTMLTextAreaElement?>(null) }

    LaunchedEffect(currentInputSelector) {
        if (currentInputSelector != InputSelector.NONE) {
            textAreaRef.value?.blur()
        }
    }


    fun triggerFilePicker(acceptType: String) {
        fileInputRef?.let {
            it.accept = acceptType
            it.click()
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .borderTop(1.px, LineStyle.Solid, ColorMode.current.toPalette().border)
    ) {
        Column(Modifier.fillMaxWidth()) {
            // --- 回复预览 ---
            ReplyPreviewBar(
                message = returnMessage,
                onClear = onClearReturnMessage
            )

            InputFunctionButtons(
                onEmojiClick = {
                    val nextSelector = if (currentInputSelector == InputSelector.EMOJI) InputSelector.NONE else InputSelector.EMOJI
                    onInputSelectorChanged(nextSelector)
                },
                onImageClick = {
                    triggerFilePicker("image/*")
                },
                onVideoClick = {
                    triggerFilePicker("video/*")
                },
                onAudioClick = {
                    triggerFilePicker("audio/*")
                }
            )

            // --- 输入框和按钮 ---
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.px),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.px)
            ) {
                Box(Modifier.weight(1f)) {
                    // --- 使用底层的 TextArea 并手动应用样式 ---
                    TextArea(
                        value = textMessageInputted,
                        attrs = InputStyle.toModifier(OutlinedInputVariant) // <-- 1. 应用基础样式和变体
                            .setVariable(InputVars.BorderRadius, 20.px) // <-- 2. 自定义样式变量
                            .backgroundColor(ColorMode.current.toSitePalette().subtle)
                            .border(0.px) // 覆盖 variant 的边框，如果需要的话
                            .outline(0.px)
                            .fillMaxWidth()
                            .minHeight(40.px)
                            .maxHeight(120.px)
                            .padding(leftRight = 12.px, topBottom = 8.px)
                            .resize(Resize.None)
                            .overflow { y(Overflow.Auto) }
                            .toAttrs { // <-- 3. 设置 HTML 属性
                                placeholder("Type a message...")
                                onInput { onUserInputChanged(it.value) }
                                ref {
                                    textAreaRef.value = it;
                                    onDispose { }
                                }

                                // 实现 Enter 提交, Shift+Enter 换行 (可选，但体验更好)
                                onKeyDown { evt ->
                                    if (evt.key == "Enter" && !evt.shiftKey) {
                                        evt.preventDefault() // 阻止默认的换行行为
                                        if (isSendEnabled) sendTextMessage()
                                    }
                                }
                            }
                    )
                }

                // 发送按钮
                Button(
                    onClick = {
                        if (isSendEnabled) sendTextMessage()
                    },
                    modifier = Modifier.size(40.px).borderRadius(50.percent),
                    enabled = isSendEnabled,
                ) {
                    FaPaperPlane()
                }
            }

            SelectorExpanded(
                currentSelector = currentInputSelector,
                onEmojiClick = appendEmoji,
            )
        }
    }

//    Input(
//        type = InputType.File,
//        attrs = Modifier
//            .display(DisplayStyle.None)
//            .ref { fileInputRef = it }
//            .onInput { event ->
//                val inputElement = event.nativeEvent.target as HTMLInputElement
//                inputElement.files?.let { fileList ->
//                    if (fileList.length > 0) {
//                        val file = fileList.item(0)!!
//                        sendFileMessage(file)
//                    }
//                }
//                // 重置 input 的值，以便下次可以选择相同的文件
//                inputElement.value = ""
//                // 选择文件后自动关闭可能打开的面板
//                onInputSelectorChanged(InputSelector.NONE)
//            }
//            .toAttrs()
//    )


}



@Composable
private fun InputFunctionButtons(
    onEmojiClick: () -> Unit,
    onImageClick: () -> Unit,
    onVideoClick: () -> Unit,
    onAudioClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.px)
    ) {
        IconButton(onClick = onEmojiClick) { FaFaceSmile() }
        IconButton(onClick = onImageClick) { FaImage() }
        IconButton(onClick = onVideoClick) { FaVideo() }
        IconButton(onClick = onAudioClick) { FaFileAudio() }
    }
}


@Composable
private fun SelectorExpanded(
    currentSelector: InputSelector,
    onEmojiClick: (String) -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (currentSelector == InputSelector.EMOJI) 250.px else 0.px)
            .transition(Transition.of("height", duration = 200.ms))
            .overflow(Overflow.Hidden)
    ) {
        when (currentSelector) {
            InputSelector.EMOJI -> EmojiTable(onEmojiClick = onEmojiClick)
            else -> {

            }
        }
    }
}

val EmojiCellStyle = CssStyle {
    base {
        Modifier
            .padding(4.px)
            .borderRadius(4.px)
            .transition(Transition.of("background-color", 200.ms))
    }
    hover {
        Modifier.backgroundColor(Colors.LightGray.copyf(alpha = 0.5f))
    }
}
@Composable
private fun EmojiTable(onEmojiClick: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .overflow { y(Overflow.Auto) }
            .padding(8.px),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SimpleGrid(numColumns(8)) {
            emojis.forEach { emoji ->
                Box(
                    EmojiCellStyle.toModifier()
                        .onClick { onEmojiClick(emoji) },
                    contentAlignment = Alignment.Center
                ) {
                    SpanText(emoji, modifier = Modifier.fontSize(1.5.cssRem))
                }
            }
        }
    }
}

@Composable
private fun ReplyPreviewBar(message: Message?, onClear: () -> Unit) {
    if (message == null) return
    Row(
        Modifier
            .fillMaxWidth()
            .padding(leftRight = 8.px, topBottom = 4.px)
            .overflow {
                x(Overflow.Hidden)
                y(Overflow.Auto)
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            SpanText(
                text = "Replying to ${message.detail.sender?.showName}",
                modifier = Modifier
                    .fontWeight(FontWeight.Bold)
            )
            SpanText(
                text = message.formatMessage,
                modifier = Modifier
                    .whiteSpace(WhiteSpace.NoWrap)
                    .overflow(Overflow.Hidden)
                    .textOverflow(TextOverflow.Ellipsis)
            )
        }
        IconButton(onClick = onClear) { FaXmark() }
    }
}

private val emojis = listOf(
    // Smileys & Emotion
    "\uD83D\uDE00", // 😀 Grinning Face
    "\uD83D\uDE01", // 😁 Beaming Face with Smiling Eyes
    "\uD83D\uDE02", // 😂 Face with Tears of Joy
    "\uD83D\uDE03", // 😃 Grinning Face with Big Eyes
    "\uD83D\uDE04", // 😄 Grinning Face with Smiling Eyes
    "\uD83D\uDE05", // 😅 Grinning Face with Sweat
    "\uD83D\uDE06", // 😆 Grinning Squinting Face
    "\uD83D\uDE07", // 😇 Smiling Face with Halo
    "\uD83D\uDE08", // 😈 Smiling Face with Horns
    "\uD83D\uDE09", // 😉 Winking Face
    "\uD83D\uDE0A", // 😊 Smiling Face with Smiling Eyes
    "\uD83D\uDE0B", // 😋 Face Savoring Food
    "\uD83D\uDE0C", // 😌 Relieved Face
    "\uD83D\uDE0D", // 😍 Smiling Face with Heart-Eyes
    "\uD83D\uDE0E", // 😎 Smiling Face with Sunglasses
    "\uD83D\uDE0F", // 😏 Smirking Face
    "\uD83D\uDE12", // 😒 Unamused Face
    "\uD83D\uDE14", // 😔 Pensive Face
    "\uD83D\uDE16", // 😖 Confounded Face
    "\uD83D\uDE18", // 😘 Face Blowing a Kiss
    "\uD83D\uDE1A", // 😚 Kissing Face with Closed Eyes
    "\uD83D\uDE1C", // 😜 Winking Face with Tongue
    "\uD83D\uDE1D", // 😝 Squinting Face with Tongue
    "\uD83D\uDE1E", // 😞 Disappointed Face
    "\uD83D\uDE20", // 😠 Angry Face
    "\uD83D\uDE21", // 😡 Pouting Face
    "\uD83D\uDE22", // 😢 Crying Face
    "\uD83D\uDE23", // 😣 Persevering Face
    "\uD83D\uDE24", // 😤 Face with Steam From Nose
    "\uD83D\uDE25", // 😥 Sad but Relieved Face
    "\uD83D\uDE28", // 😨 Fearful Face
    "\uD83D\uDE29", // 😩 Weary Face
    "\uD83D\uDE2A", // 😪 Sleepy Face
    "\uD83D\uDE2B", // 😫 Tired Face
    "\uD83D\uDE2D", // 😭 Loudly Crying Face
    "\uD83D\uDE30", // 😰 Anxious Face with Sweat
    "\uD83D\uDE31", // 😱 Face Screaming in Fear
    "\uD83D\uDE32", // 😲 Astonished Face
    "\uD83D\uDE33", // 😳 Flushed Face
    "\uD83D\uDE35", // 😵 Dizzy Face
    "\uD83D\uDE37", // 😷 Face with Medical Mask
    "\uD83D\uDE38", // 😸 Grinning Cat with Smiling Eyes
    "\uD83D\uDE39", // 😹 Cat with Tears of Joy
    "\uD83D\uDE3A", // 😺 Smiling Cat with Open Mouth
    "\uD83D\uDE3B", // 😻 Smiling Cat with Heart-Eyes
    "\uD83D\uDE3C", // 😼 Cat with Wry Smile
    "\uD83D\uDE3D", // 😽 Kissing Cat
    "\uD83D\uDE3E", // 😾 Pouting Cat
    "\uD83D\uDE3F", // 😿 Crying Cat
    "\uD83D\uDE40", // 🙀 Weary Cat

    // People & Body
    "\uD83D\uDC66", // 👦 Boy
    "\uD83D\uDC67", // 👧 Girl
    "\uD83D\uDC68", // 👨 Man
    "\uD83D\uDC69", // 👩 Woman
    "\uD83D\uDC6A", // 👪 Family
    "\uD83D\uDC6B", // 👫 Man and Woman Holding Hands
    "\uD83D\uDC6C", // 👬 Two Men Holding Hands
    "\uD83D\uDC6D", // 👭 Two Women Holding Hands
    "\uD83D\uDC6E", // 👮 Police Officer
    "\uD83D\uDC6F", // 👯 People with Bunny Ears
    "\uD83D\uDC70", // 👰 Bride with Veil
    "\uD83D\uDC71", // 👱 Person with Blond Hair
    "\uD83D\uDC72", // 👲 Man with Chinese Cap
    "\uD83D\uDC73", // 👳 Person Wearing Turban
    "\uD83D\uDC74", // 👴 Old Man
    "\uD83D\uDC75", // 👵 Old Woman
    "\uD83D\uDC76", // 👶 Baby
    "\uD83D\uDC77", // 👷 Construction Worker
    "\uD83D\uDC78", // 👸 Princess
    "\uD83D\uDC7C", // 👼 Baby Angel
    "\uD83D\uDC7D", // 👽 Alien
    "\uD83D\uDC7E", // 👾 Alien Monster
    "\uD83D\uDC7F", // 👿 Imp
    "\uD83D\uDC80", // 💀 Skull
    "\uD83D\uDC81", // 💁 Information Desk Person
    "\uD83D\uDC82", // 💂 Guardsman
    "\uD83D\uDC83", // 💃 Dancer
    "\uD83D\uDC84", // 💄 Lipstick
    "\uD83D\uDC85", // 💅 Nail Polish
    "\uD83D\uDC86", // 💆 Person Getting Massage
    "\uD83D\uDC87", // 💇 Person Getting Haircut
    "\uD83D\uDC88", // 💈 Barber Pole
    "\uD83D\uDC89", // 💉 Syringe
    "\uD83D\uDC8A", // 💊 Pill
)