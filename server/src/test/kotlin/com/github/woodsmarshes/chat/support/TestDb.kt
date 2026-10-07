package com.github.woodsmarshes.chat.support

import com.github.woodsmarshes.chat.core.model.ContactStatus
import com.github.woodsmarshes.chat.core.model.ConversationParticipant
import com.github.woodsmarshes.chat.core.model.ConversationRole
import com.github.woodsmarshes.chat.core.model.ConversationType
import com.github.woodsmarshes.chat.core.model.GroupMetadata
import com.github.woodsmarshes.chat.core.model.GroupSettings
import com.github.woodsmarshes.chat.core.model.ParticipantSettings
import com.github.woodsmarshes.chat.core.model.PrivateMetadata
import com.github.woodsmarshes.chat.core.model.UserRole
import com.github.woodsmarshes.chat.repository.database.schema.ALL_SCHEMA_TABLES
import com.github.woodsmarshes.chat.repository.database.schema.ConversationParticipants
import com.github.woodsmarshes.chat.repository.database.schema.Conversations
import com.github.woodsmarshes.chat.repository.database.schema.Contacts
import com.github.woodsmarshes.chat.repository.database.schema.GroupProfiles
import com.github.woodsmarshes.chat.repository.database.schema.Users
import com.github.woodsmarshes.chat.utils.clearDatabaseData
import com.github.woodsmarshes.chat.utils.connectToH2Database
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * One shared in-memory H2 instance for the whole test JVM. Database-backed
 * tests call [reset] in their @BeforeTest so every test starts from a clean
 * schema, then seed exactly the rows the scenario needs.
 */
object TestDb {
    val database: Database = connectToH2Database()

    /** Drops and recreates every table: a clean slate per test. */
    fun reset() {
        // App tests legitimately close their own pool on ApplicationStopped,
        // and Exposed's global default transaction database is whoever
        // connected last — so repository dbQuery calls would otherwise ride
        // a closed pool from a torn-down test app. Pin the default back to
        // the shared test database; every H2 pool in this JVM points at the
        // same in-memory instance, so this only fixes routing, not data.
        TransactionManager.defaultDatabase = database
        runBlocking { clearDatabaseData(database) }
        transaction(database) {
            SchemaUtils.create(*ALL_SCHEMA_TABLES.toTypedArray())
        }
    }

    suspend fun user(name: String): Uuid = transaction(database) {
        Users.insert {
            it[id] = Uuid.random()
            it[username] = name
            it[email] = "$name-${Uuid.random()}@test.local"
            it[passwordHash] = "hash"
            it[salt] = ""
            it[role] = UserRole.MEMBER
            it[createdAt] = Clock.System.now()
            it[updatedAt] = Clock.System.now()
        }[Users.id].value
    }

    suspend fun contacts(a: Uuid, b: Uuid) {
        transaction(database) {
            Contacts.insert {
                it[userId] = a
                it[contactId] = b
                it[status] = ContactStatus.FRIEND
            }
            Contacts.insert {
                it[userId] = b
                it[contactId] = a
                it[status] = ContactStatus.FRIEND
            }
        }
    }

    suspend fun privateConversation(a: Uuid, b: Uuid): Uuid = transaction(database) {
        val convId = Uuid.random()
        val memberA = a
        val memberB = b
        Conversations.insert {
            it[id] = convId
            it[type] = ConversationType.PRIVATE
            it[metadata] = PrivateMetadata()
            it[createdAt] = Clock.System.now()
            it[updatedAt] = Clock.System.now()
        }
        listOf(memberA, memberB).forEach { uid ->
            ConversationParticipants.insert {
                it[conversationId] = convId
                it[userId] = uid
                it[role] = ConversationRole.PARTICIPANT
                it[lastReadMessageId] = null
                it[joinedAt] = Clock.System.now()
                it[settings] = ParticipantSettings()
            }
        }
        convId
    }

    suspend fun groupConversation(ownerId: Uuid, members: List<Uuid>): Uuid = transaction(database) {
        val convId = Uuid.random()
        val groupOwner = ownerId
        Conversations.insert {
            it[id] = convId
            it[type] = ConversationType.GROUP
            it[metadata] = GroupMetadata()
            it[createdAt] = Clock.System.now()
            it[updatedAt] = Clock.System.now()
        }
        GroupProfiles.insert {
            it[conversationId] = convId
            it[name] = "group-$convId"
            it[this.ownerId] = groupOwner
            it[settings] = GroupSettings()
            it[createdAt] = Clock.System.now()
            it[updatedAt] = Clock.System.now()
        }
        (listOf(groupOwner to ConversationRole.OWNER) + members.map { it to ConversationRole.MEMBER }).forEach { (uid, memberRole) ->
            ConversationParticipants.insert {
                it[conversationId] = convId
                it[userId] = uid
                it[role] = memberRole
                it[lastReadMessageId] = null
                it[joinedAt] = Clock.System.now()
                it[settings] = ParticipantSettings()
            }
        }
        convId
    }
}
