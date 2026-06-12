package com.github.woodsmarshes.chat.core.database.room

import androidx.room3.TypeConverter
import com.github.woodsmarshes.chat.core.model.MessageCategory
import com.github.woodsmarshes.chat.core.model.MessageContent
import com.github.woodsmarshes.chat.core.model.MessageRenderType
import com.github.woodsmarshes.chat.core.model.UserRole
import kotlin.time.Instant
import kotlin.uuid.Uuid

object RoomTypeConverters {
    @TypeConverter
    fun uuidFromString(value: String): Uuid = Uuid.parse(value)

    @TypeConverter
    fun uuidToString(uuid: Uuid): String = uuid.toString()

    @TypeConverter
    fun instantFromLong(value: Long): Instant = Instant.fromEpochMilliseconds(value)

    @TypeConverter
    fun instantToLong(instant: Instant): Long = instant.toEpochMilliseconds()

    @TypeConverter
    fun instantFromLongNullable(value: Long?): Instant? = value?.let { Instant.fromEpochMilliseconds(it) }

    @TypeConverter
    fun instantToLongNullable(instant: Instant?): Long? = instant?.toEpochMilliseconds()

    @TypeConverter
    fun userRoleToString(role: UserRole): String = role.name

    @TypeConverter
    fun userRoleFromString(value: String): UserRole = UserRole.valueOf(value)

    @TypeConverter
    fun messageCategoryToString(category: MessageCategory): String = category.name

    @TypeConverter
    fun messageCategoryFromString(value: String): MessageCategory = MessageCategory.valueOf(value)

    @TypeConverter
    fun messageRenderTypeToString(renderType: MessageRenderType): String = renderType.name

    @TypeConverter
    fun messageRenderTypeFromString(value: String): MessageRenderType = MessageRenderType.valueOf(value)

    @TypeConverter
    fun messageContentToJson(content: MessageContent): String =
        kotlinx.serialization.json.Json.encodeToString(MessageContent.serializer(), content)

    @TypeConverter
    fun messageContentFromJson(value: String): MessageContent =
        kotlinx.serialization.json.Json.decodeFromString(MessageContent.serializer(), value)

    @TypeConverter
    fun messageContentToJsonNullable(content: MessageContent?): String? =
        content?.let { kotlinx.serialization.json.Json.encodeToString(MessageContent.serializer(), it) }

    @TypeConverter
    fun messageContentFromJsonNullable(value: String?): MessageContent? =
        value?.let { kotlinx.serialization.json.Json.decodeFromString(MessageContent.serializer(), it) }
}
