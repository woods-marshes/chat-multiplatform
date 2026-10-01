package com.github.woodsmarshes.chat.repository.database.schema

import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.Uuid

/**
 * Every table the server owns, in creation order (children may reference
 * parents). configureSchema creates from this list and clearDatabaseData
 * drops it in reverse — adding a table here is what wires both, so the two
 * lists can never drift apart again.
 */
val ALL_SCHEMA_TABLES: List<Table> = listOf(
    Users,
    AuthSessions,
    Conversations,
    UserSettings,
    GroupProfiles,
    Messages,
    GroupJoinRequests,
    ConversationParticipants,
    Contacts,
    ContactRequests,
    PrivateFiles,
    Articles,
    YjsDocuments,
)

/**
 * One-time backfill for messages stored before the seq column existed: fills
 * messages.seq in id order (UUIDv7 matches creation order) per conversation
 * and leaves conversations.next_seq at the high-water mark, so allocation
 * continues from there. Runs inside one transaction and is a no-op once
 * every message carries a seq.
 */
fun backfillMessageSeq(database: Database) {
    transaction(database) {
        val hasUnsequenced = Messages
            .selectAll()
            .where { Messages.seq.isNull() }
            .limit(1)
            .count() > 0
        if (!hasUnsequenced) return@transaction

        var current: Uuid? = null
        var seq = 0L
        Messages
            .selectAll()
            .orderBy(Messages.conversationId to SortOrder.ASC, Messages.id to SortOrder.ASC)
            .forEach { row ->
                val conversationId = row[Messages.conversationId].value
                if (conversationId != current) {
                    current = conversationId
                    seq = 0L
                }
                seq += 1
                Messages.update({ Messages.id eq row[Messages.id] }) {
                    it[Messages.seq] = seq
                }
                Conversations.update({ Conversations.id eq conversationId }) {
                    it[Conversations.nextSeq] = seq
                }
            }
    }
}
