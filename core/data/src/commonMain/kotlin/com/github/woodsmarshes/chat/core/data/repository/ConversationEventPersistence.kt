package com.github.woodsmarshes.chat.core.data.repository

import com.github.woodsmarshes.chat.core.database.dao.ConversationDao
import com.github.woodsmarshes.chat.core.database.dao.GroupJoinRequestDao
import com.github.woodsmarshes.chat.core.database.dao.ParticipantDao
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Local teardown a ConversationDeleted event implies: drop every participant
 * row, soft-delete the conversation, and clear its join requests.
 */
internal suspend fun applyConversationDeleted(
    conversationId: Uuid,
    timestamp: Instant,
    conversationDao: ConversationDao,
    participantDao: ParticipantDao,
    groupJoinRequestDao: GroupJoinRequestDao,
) {
    participantDao.removeAllParticipantsFromConversation(conversationId)
    conversationDao.softDeleteConversation(conversationId, timestamp)
    groupJoinRequestDao.deleteGroupJoinRequestsForConversation(conversationId)
}

/**
 * Local teardown a UserLeftConversation event implies: drop the leaver's
 * participant row; when the leaver is the local user, the conversation is
 * gone for them, so soft-delete it and clear its join requests.
 */
internal suspend fun applyUserLeftConversation(
    conversationId: Uuid,
    userId: Uuid,
    meId: Uuid?,
    timestamp: Instant,
    conversationDao: ConversationDao,
    participantDao: ParticipantDao,
    groupJoinRequestDao: GroupJoinRequestDao,
) {
    participantDao.removeParticipant(conversationId, userId)
    if (userId == meId) {
        conversationDao.softDeleteConversation(conversationId, timestamp)
        groupJoinRequestDao.deleteGroupJoinRequestsForConversation(conversationId)
    }
}
