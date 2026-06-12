package com.github.woodsmarshes.chat.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
sealed interface FormattingEntity {
    /**
     * 大多数样式都基于文本范围（偏移量和长度）
     * 建议：单位统一为 UTF-16 Code Units (等同于 Kotlin String.length)
     */
    abstract class TextRange : FormattingEntity {
        abstract val offset: Int
        abstract val length: Int
    }

    // --- 基础样式 ---

    @Serializable
    @SerialName("BOLD")
    data class Bold(
        @ProtoNumber(1) override val offset: Int,
        @ProtoNumber(2) override val length: Int
    ) : TextRange()

    @Serializable
    @SerialName("ITALIC")
    data class Italic(
        @ProtoNumber(1) override val offset: Int,
        @ProtoNumber(2) override val length: Int
    ) : TextRange()

    @Serializable
    @SerialName("STRIKETHROUGH") // 删除线
    data class Strikethrough(
        @ProtoNumber(1) override val offset: Int,
        @ProtoNumber(2) override val length: Int
    ) : TextRange()

    @Serializable
    @SerialName("SPOILER") // 剧透内容，点击后显示
    data class Spoiler(
        @ProtoNumber(1) override val offset: Int,
        @ProtoNumber(2) override val length: Int
    ) : TextRange()

    // --- 链接与代码 ---

    @Serializable
    @SerialName("CODE") // 行内代码，如 `println()`
    data class Code(
        @ProtoNumber(1) override val offset: Int,
        @ProtoNumber(2) override val length: Int
    ) : TextRange()

    @Serializable
    @SerialName("URL")
    data class Url(
        @ProtoNumber(1) override val offset: Int,
        @ProtoNumber(2) override val length: Int,
        @ProtoNumber(3) val url: String // 实际跳转的链接
    ) : TextRange()

    // --- 社交与交互 ---

    @Serializable
    @SerialName("MENTION")
    data class Mention(
        @ProtoNumber(1) override val offset: Int,
        @ProtoNumber(2) override val length: Int,
        @ProtoNumber(3) val userId: Uuid // 被提到的人的 ID
    ) : TextRange()

    @Serializable
    @SerialName("MENTION_ALL") // @所有人
    data class MentionAll(
        @ProtoNumber(1) override val offset: Int,
        @ProtoNumber(2) override val length: Int
    ) : TextRange()

    @Serializable
    @SerialName("HASHTAG") // #话题
    data class Hashtag(
        @ProtoNumber(1) override val offset: Int,
        @ProtoNumber(2) override val length: Int,
        @ProtoNumber(3) val tag: String
    ) : TextRange()

    @Serializable
    @SerialName("BOT_COMMAND") // /commands
    data class BotCommand(
        @ProtoNumber(1) override val offset: Int,
        @ProtoNumber(2) override val length: Int
    ) : TextRange()

    // --- 结构性内容 (不基于特定文本范围，可选) ---

    @Serializable
    @SerialName("BLOCK_QUOTE") // 消息正文中的引用块 (类似 Markdown 的 >)
    data class BlockQuote(
        @ProtoNumber(1) val text: String,
        @ProtoNumber(2) val authorName: String? = null
    ) : FormattingEntity
}