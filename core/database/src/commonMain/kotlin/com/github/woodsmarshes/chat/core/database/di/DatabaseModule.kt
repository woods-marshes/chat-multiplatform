package com.github.woodsmarshes.chat.core.database.di

import app.cash.sqldelight.EnumColumnAdapter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import com.github.woodsmarshes.chat.core.common.di.PlatformContext
import com.github.woodsmarshes.chat.core.database.session.DatabaseSessionGate
import com.github.woodsmarshes.chat.core.database.session.ManagedDatabaseSessionGate
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
import io.github.woodsmarshes.chat.db.ContactRequestEntity
import io.github.woodsmarshes.chat.db.ConversationEntity
import io.github.woodsmarshes.chat.db.GroupJoinRequestEntity
import io.github.woodsmarshes.chat.db.GroupProfileEntity
import io.github.woodsmarshes.chat.db.MessageEntity
import io.github.woodsmarshes.chat.db.ParticipantEntity
import io.github.woodsmarshes.chat.db.UserEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import kotlin.concurrent.Volatile
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.uuid.Uuid

val databaseModule = module {
    single { DatabaseHolder(get()) }
}

/**
 * Coroutine context element that pins DAO write operations within a
 * session-bound call to the exact [ChatDatabase] instance captured when the
 * operation started, preventing dynamic process-singleton DAOs from
 * redirecting a stale operation's write into a newer session's database.
 */
class BoundDatabaseElement(
    val database: ChatDatabase,
) : AbstractCoroutineContextElement(BoundDatabaseElement) {
    companion object Key : CoroutineContext.Key<BoundDatabaseElement>
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
        ContactRequestEntityAdapter = ContactRequestEntity.Adapter(
            idAdapter = uuidAdapter,
            sender_idAdapter = uuidAdapter,
            receiver_idAdapter = uuidAdapter,
            statusAdapter = EnumColumnAdapter(),
            created_atAdapter = instantAdapter,
            updated_atAdapter = instantAdapter,
        ),
        GroupJoinRequestEntityAdapter = GroupJoinRequestEntity.Adapter(
            idAdapter = uuidAdapter,
            conversation_idAdapter = uuidAdapter,
            applicant_idAdapter = uuidAdapter,
            statusAdapter = EnumColumnAdapter(),
            handled_byAdapter = uuidAdapter,
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

/**
 * Lazily opens one SQLDelight database per logged-in user. DAOs are process
 * singletons that call [getActiveDatabase] on every use, so swapping users
 * swaps the underlying driver without rebuilding the DI graph.
 *
 * Open/close are serialized with a mutex; concurrent get-or-create and close
 * (e.g. logout racing a token refresh) can no longer close a driver that was
 * just handed out.
 */
class DatabaseHolder(
    private val platformContext: PlatformContext,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val mutex = Mutex()
    @Volatile
    private var currentDb: ChatDatabase? = null
    private var currentDriver: SqlDriver? = null
    private var currentUserId: Uuid? = null
    @Volatile
    private var currentGeneration: Long = 0L

    private val managedSessionGate = ManagedDatabaseSessionGate(
        dispatcher = dispatcher,
        activeDbProvider = { currentDb },
        activeGenerationProvider = { currentGeneration },
    )

    val sessionGate: DatabaseSessionGate
        get() = managedSessionGate

    suspend fun getOrCreateDatabase(userId: Uuid, generation: Long? = null): ChatDatabase = mutex.withLock {
        if (currentUserId == userId && currentDb != null) {
            if (generation != null) {
                if (generation != currentGeneration) managedSessionGate.stop()
                currentGeneration = generation
            }
            managedSessionGate.start()
            return@withLock currentDb!!
        }

        managedSessionGate.stop()
        closeLocked()

        val dbName = "chat_${userId}.db"
        try {
            val openedDb = createDatabase { schema ->
                provideDbDriver(schema, platformContext, dbName).also { currentDriver = it }
            }
            currentDb = openedDb
            currentUserId = userId
            currentGeneration = if (generation != null && generation > 0L) generation else 1L
            managedSessionGate.start()
            openedDb
        } catch (t: Throwable) {
            managedSessionGate.stop()
            closeLocked()
            throw t
        }
    }

    /**
     * Cancels all active database query observation Flows and PagingSource
     * loads and suspends until they have exited.
     */
    suspend fun stopDatabaseSession() {
        managedSessionGate.stop()
    }

    /**
     * Closes the active database only when it still belongs to [userId] and,
     * when [generation] is supplied, the same session generation.
     */
    suspend fun closeDatabaseIfCurrent(userId: Uuid, generation: Long? = null) = mutex.withLock {
        if (currentUserId == userId && (generation == null || currentGeneration == generation)) {
            managedSessionGate.stop()
            closeLocked()
        }
    }

    /** Must be called while holding [mutex]. */
    private fun closeLocked() {
        currentDriver?.close()
        currentDriver = null
        currentDb = null
        currentUserId = null
        currentGeneration = 0L
    }

    fun getActiveDatabase(): ChatDatabase {
        return currentDb ?: throw IllegalStateException("Database is not initialized! User is not logged in.")
    }
}
