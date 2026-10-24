package com.github.woodsmarshes.chat.repository

import com.github.woodsmarshes.chat.core.model.Message
import com.github.woodsmarshes.chat.repository.database.schema.ConversationParticipants
import com.github.woodsmarshes.chat.repository.database.schema.Messages
import com.github.woodsmarshes.chat.repository.database.schema.Users
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Join
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.leftJoin
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.uuid.Uuid

/**
 * The canonical "message with sender context" join: every message joined to
 * its sender, plus (left) the conversation-scoped participant row used for
 * recipient filtering. All message reads go through one of these overloads
 * so sender hydration cannot fork between call sites — a fork that once
 * produced replies whose `sender` was silently null.
 */

private fun senderContextJoin(
    conversationConstraint: () -> Op<Boolean>,
): Join =
    Messages
        .innerJoin(Users)
        .leftJoin(
            otherTable = ConversationParticipants,
            onColumn = { Users.id },
            otherColumn = { ConversationParticipants.userId },
            additionalConstraint = conversationConstraint,
        )

/** Pins the participant row of every joined message to [conversationId]. */
fun messagesWithSenderContext(conversationId: Uuid): Join =
    senderContextJoin { ConversationParticipants.conversationId eq conversationId }

/** Joins the participant row of each message's own conversation. */
fun messagesWithSenderContext(conversationId: Column<EntityID<Uuid>>): Join =
    senderContextJoin { ConversationParticipants.conversationId eq conversationId }

/** Loads one reply target with sender context, pinned to [conversationId]. */
fun loadReplyMessage(replyId: Uuid, conversationId: Uuid): Message? =
    messagesWithSenderContext(conversationId)
        .selectAll()
        .where { (Messages.id eq replyId) and (Messages.conversationId eq conversationId) }
        .singleOrNull()
        ?.toFilteredUser()

/** Loads one reply target with its own conversation's participant row. */
fun loadReplyMessage(replyId: Uuid, conversationId: Column<EntityID<Uuid>>): Message? =
    messagesWithSenderContext(conversationId)
        .selectAll()
        .where { Messages.id eq replyId }
        .singleOrNull()
        ?.toFilteredUser()

/**
 * Batch reply hydration pinned to one conversation: loads the reply targets
 * of [rows] and maps each row to its [Message] with the hydrated reply.
 */
fun hydrateReplies(rows: List<ResultRow>, conversationId: Uuid): List<Message> {
    val replyIds = rows.mapNotNull { it[Messages.replyToMessageId] }.distinct()
    val replyMap = if (replyIds.isNotEmpty()) {
        messagesWithSenderContext(conversationId)
            .selectAll()
            .where { (Messages.id inList replyIds) and (Messages.conversationId eq conversationId) }
            .associateBy { it[Messages.id].value }
    } else {
        emptyMap()
    }
    return rows.map { row ->
        val replyTo = row[Messages.replyToMessageId]?.let { replyMap[it.value]?.toFilteredUser() }
        row.toFilteredUser(replyTo)
    }
}

/** Batch reply hydration across conversations (each row's own participant context). */
fun hydrateReplies(rows: List<ResultRow>, conversationId: Column<EntityID<Uuid>>): Map<Uuid, Message> {
    val replyIds = rows.mapNotNull { it[Messages.replyToMessageId]?.value }.distinct()
    val replyMap = if (replyIds.isNotEmpty()) {
        messagesWithSenderContext(conversationId)
            .selectAll()
            .where { Messages.id inList replyIds }
            .associate { it[Messages.id].value to it.toFilteredUser() }
    } else {
        emptyMap()
    }
    return rows.associate { row ->
        val msg = row.toFilteredUser(row[Messages.replyToMessageId]?.let { replyMap[it.value] })
        msg.id to msg
    }
}
