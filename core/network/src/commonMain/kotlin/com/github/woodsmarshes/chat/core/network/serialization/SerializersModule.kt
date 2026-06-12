package com.github.woodsmarshes.chat.core.network.serialization

import com.github.woodsmarshes.chat.core.model.AudioContent
import com.github.woodsmarshes.chat.core.model.ConversationMetadata
import com.github.woodsmarshes.chat.core.model.FileContent
import com.github.woodsmarshes.chat.core.model.FormattingEntity
import com.github.woodsmarshes.chat.core.model.GroupMetadata
import com.github.woodsmarshes.chat.core.model.ImageContent
import com.github.woodsmarshes.chat.core.model.JoinGroupContent
import com.github.woodsmarshes.chat.core.model.MessageContent
import com.github.woodsmarshes.chat.core.model.PrivateMetadata
import com.github.woodsmarshes.chat.core.model.TextContent
import com.github.woodsmarshes.chat.core.model.VideoContent
import com.github.woodsmarshes.chat.core.network.dto.events.ContactEventResponse
import com.github.woodsmarshes.chat.core.network.dto.events.ConversationEventResponse
import com.github.woodsmarshes.chat.core.network.dto.events.MessageEventResponse
import com.github.woodsmarshes.chat.core.network.dto.events.MessageRequest
import com.github.woodsmarshes.chat.core.network.dto.events.RealtimeEvent
import com.github.woodsmarshes.chat.core.network.dto.events.SocketErrorResponse
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlinx.serialization.modules.subclassesOfSealed

val ProjectSerializersModule = SerializersModule {
    polymorphic(MessageContent::class) {
        subclass(TextContent::class)
        subclass(ImageContent::class)
        subclass(VideoContent::class)
        subclass(AudioContent::class)
        subclass(FileContent::class)
        subclass(JoinGroupContent::class)
    }
    polymorphic(ConversationMetadata::class) {
        subclass(GroupMetadata::class)
        subclass(PrivateMetadata::class)
    }
    polymorphic(FormattingEntity::class) {
        subclass(FormattingEntity.Bold::class)
        subclass(FormattingEntity.Italic::class)
        subclass(FormattingEntity.Strikethrough::class)
        subclass(FormattingEntity.Spoiler::class)
        subclass(FormattingEntity.Code::class)
        subclass(FormattingEntity.Url::class)
        subclass(FormattingEntity.Mention::class)
        subclass(FormattingEntity.MentionAll::class)
        subclass(FormattingEntity.Hashtag::class)
        subclass(FormattingEntity.BotCommand::class)
        subclass(FormattingEntity.BlockQuote::class)
    }
    polymorphic(RealtimeEvent::class) {
        subclassesOfSealed<MessageEventResponse>()
        subclassesOfSealed<MessageRequest>()
        subclassesOfSealed<ContactEventResponse>()
        subclassesOfSealed<ConversationEventResponse>()
        subclass(SocketErrorResponse::class)
    }
    polymorphic(MessageEventResponse::class) {
        subclassesOfSealed<MessageEventResponse>()
    }
    polymorphic(MessageRequest::class) {
        subclassesOfSealed<MessageRequest>()
    }
    polymorphic(ContactEventResponse::class) {
        subclassesOfSealed<ContactEventResponse>()
    }
    polymorphic(ConversationEventResponse::class) {
        subclassesOfSealed<ConversationEventResponse>()
    }
}
