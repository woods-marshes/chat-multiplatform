package com.github.woodsmarshes.chat.core.database.room

import androidx.room3.ColumnTypeConverter
import com.github.woodsmarshes.chat.core.model.MessageCategory
import com.github.woodsmarshes.chat.core.model.MessageContent
import com.github.woodsmarshes.chat.core.model.MessageRenderType
import com.github.woodsmarshes.chat.core.model.UserRole
import kotlinx.serialization.json.Json
import kotlin.time.Instant
import kotlin.uuid.Uuid

object RoomTypeConverters {
    /**
     * DB-side mirror of the wire format (core:network ProjectJson) with the
     * same class discriminator. `ignoreUnknownKeys` is essential: a model field
     * added in a newer app version must not make previously persisted rows
     * undecodable. Kept local so core:database-room does not depend on
     * core:network.
     */
    private val DbJson = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
        isLenient = true
    }

    @ColumnTypeConverter
    fun uuidFromString(value: String): Uuid = Uuid.parse(value)

    @ColumnTypeConverter
    fun uuidToString(uuid: Uuid): String = uuid.toString()

    @ColumnTypeConverter
    fun instantFromLong(value: Long): Instant = Instant.fromEpochMilliseconds(value)

    @ColumnTypeConverter
    fun instantToLong(instant: Instant): Long = instant.toEpochMilliseconds()

    @ColumnTypeConverter
    fun instantFromLongNullable(value: Long?): Instant? = value?.let { Instant.fromEpochMilliseconds(it) }

    @ColumnTypeConverter
    fun instantToLongNullable(instant: Instant?): Long? = instant?.toEpochMilliseconds()

    // Enums: unknown wire values (e.g. a role added server-side) fall back to
    // the *_UNKNOWN variant instead of throwing and wedging the DB sync.
    @ColumnTypeConverter
    fun userRoleToString(role: UserRole): String = role.name

    @ColumnTypeConverter
    fun userRoleFromString(value: String): UserRole =
        UserRole.entries.firstOrNull { it.name == value } ?: UserRole.GUEST

    @ColumnTypeConverter
    fun messageCategoryToString(category: MessageCategory): String = category.name

    @ColumnTypeConverter
    fun messageCategoryFromString(value: String): MessageCategory =
        MessageCategory.entries.firstOrNull { it.name == value } ?: MessageCategory.NORMAL

    @ColumnTypeConverter
    fun messageRenderTypeToString(renderType: MessageRenderType): String = renderType.name

    @ColumnTypeConverter
    fun messageRenderTypeFromString(value: String): MessageRenderType =
        MessageRenderType.entries.firstOrNull { it.name == value } ?: MessageRenderType.OTHER

    @ColumnTypeConverter
    fun messageContentToJson(content: MessageContent): String =
        DbJson.encodeToString(MessageContent.serializer(), content)

    @ColumnTypeConverter
    fun messageContentFromJson(value: String): MessageContent =
        DbJson.decodeFromString(MessageContent.serializer(), value)

    @ColumnTypeConverter
    fun messageContentToJsonNullable(content: MessageContent?): String? =
        content?.let { DbJson.encodeToString(MessageContent.serializer(), it) }

    @ColumnTypeConverter
    fun messageContentFromJsonNullable(value: String?): MessageContent? =
        value?.let { DbJson.decodeFromString(MessageContent.serializer(), it) }
}
