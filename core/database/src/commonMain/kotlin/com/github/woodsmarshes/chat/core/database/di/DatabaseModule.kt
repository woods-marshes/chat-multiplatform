package com.github.woodsmarshes.chat.core.database.di

import app.cash.sqldelight.EnumColumnAdapter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import com.github.woodsmarshes.chat.core.common.di.PlatformContext
import com.github.woodsmarshes.chat.core.database.utils.articleStatsAdapter
import com.github.woodsmarshes.chat.core.database.utils.conversationMetadataAdapter
import com.github.woodsmarshes.chat.core.database.utils.groupSettingsAdapter
import com.github.woodsmarshes.chat.core.database.utils.instantAdapter
import com.github.woodsmarshes.chat.core.database.utils.jsonElementAdapter
import com.github.woodsmarshes.chat.core.database.utils.messageContentAdapter
import com.github.woodsmarshes.chat.core.database.utils.participantSettingsAdapter
import com.github.woodsmarshes.chat.core.database.utils.uuidAdapter
import io.github.woodsmarshes.chat.db.Article
import io.github.woodsmarshes.chat.db.ChatDatabase
import io.github.woodsmarshes.chat.db.ContactEntity
import io.github.woodsmarshes.chat.db.ConversationEntity
import io.github.woodsmarshes.chat.db.GroupProfileEntity
import io.github.woodsmarshes.chat.db.MessageEntity
import io.github.woodsmarshes.chat.db.ParticipantEntity
import io.github.woodsmarshes.chat.db.UserEntity
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import kotlin.uuid.Uuid

val databaseModule = module {
    singleOf(::DatabaseHolder)
}

suspend fun createDatabase(driverFactory: suspend (SqlSchema<QueryResult.AsyncValue<Unit>>) -> SqlDriver): ChatDatabase {
    val driver = driverFactory(ChatDatabase.Schema)
    return ChatDatabase(
        driver = driver,
        ArticleAdapter = Article.Adapter(
            idAdapter = uuidAdapter,
            contentAdapter = jsonElementAdapter,
            author_idAdapter = uuidAdapter,
            statusAdapter = EnumColumnAdapter(),
            created_atAdapter = instantAdapter,
            updated_atAdapter = instantAdapter,
            published_atAdapter = instantAdapter,
            deleted_atAdapter = instantAdapter,
            statsAdapter = articleStatsAdapter,
        ),
        ContactEntityAdapter = ContactEntity.Adapter(
            contact_idAdapter = uuidAdapter,
            statusAdapter = EnumColumnAdapter(),
            created_atAdapter = instantAdapter,
            updated_atAdapter = instantAdapter,
        ),
        ConversationEntityAdapter = ConversationEntity.Adapter(
            idAdapter = uuidAdapter,
            typeAdapter = EnumColumnAdapter(),
            last_message_idAdapter = uuidAdapter,
            metadataAdapter = conversationMetadataAdapter,
            created_atAdapter = instantAdapter,
            updated_atAdapter = instantAdapter,
            deleted_atAdapter = instantAdapter,
        ),
        GroupProfileEntityAdapter = GroupProfileEntity.Adapter(
            conversation_idAdapter = uuidAdapter,
            owner_idAdapter = uuidAdapter,
            settingsAdapter = groupSettingsAdapter,
            created_atAdapter = instantAdapter,
            updated_atAdapter = instantAdapter,
        ),
        MessageEntityAdapter = MessageEntity.Adapter(
            idAdapter = uuidAdapter,
            conversation_idAdapter = uuidAdapter,
            user_idAdapter = uuidAdapter,
            categoryAdapter = EnumColumnAdapter(),
            render_typeAdapter = EnumColumnAdapter(),
            contentAdapter = messageContentAdapter,
            reply_to_message_idAdapter = uuidAdapter,
            created_atAdapter = instantAdapter,
            revoked_atAdapter = instantAdapter,
            local_send_statusAdapter = EnumColumnAdapter(),
        ),
        ParticipantEntityAdapter = ParticipantEntity.Adapter(
            conversation_idAdapter = uuidAdapter,
            user_idAdapter = uuidAdapter,
            roleAdapter = EnumColumnAdapter(),
            last_read_message_idAdapter = uuidAdapter,
            joined_atAdapter = instantAdapter,
            muted_untilAdapter = instantAdapter,
            settingsAdapter = participantSettingsAdapter,
        ),
        UserEntityAdapter = UserEntity.Adapter(
            idAdapter = uuidAdapter,
            created_atAdapter = instantAdapter,
            updated_atAdapter = instantAdapter,
            deleted_atAdapter = instantAdapter,
            roleAdapter = EnumColumnAdapter(),
        )
    )
}

expect suspend fun provideDbDriver(
    schema: SqlSchema<QueryResult.AsyncValue<Unit>>,
    context: PlatformContext,
    dbName: String,
): SqlDriver

class DatabaseHolder(
    private val platformContext: PlatformContext,
) {
    private var currentDb: ChatDatabase? = null
    private var currentDriver: SqlDriver? = null
    private var currentUserId: Uuid? = null

    suspend fun getOrCreateDatabase(userId: Uuid): ChatDatabase {
        if (currentUserId == userId && currentDb != null) {
            return currentDb!!
        }

        closeDatabase()

        val dbName = "chat_${userId}.db"
        currentDb = createDatabase { schema ->
            provideDbDriver(schema, platformContext, dbName).also { currentDriver = it }
        }
        currentUserId = userId
        return currentDb!!
    }

    fun closeDatabase() {
        currentDriver?.close()
        currentDriver = null
        currentDb = null
        currentUserId = null
    }

    fun getActiveDatabase(): ChatDatabase {
        return currentDb ?: throw IllegalStateException("Database is not initialized! User is not logged in.")
    }
}
