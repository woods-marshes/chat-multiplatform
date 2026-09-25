package com.github.woodsmarshes.chat.service

import com.github.woodsmarshes.chat.core.model.Conversation
import com.github.woodsmarshes.chat.core.model.ConversationParticipant
import com.github.woodsmarshes.chat.core.model.ConversationRole
import com.github.woodsmarshes.chat.core.model.ConversationType
import com.github.woodsmarshes.chat.core.model.GroupMetadata
import com.github.woodsmarshes.chat.core.model.GroupProfile
import com.github.woodsmarshes.chat.core.model.GroupSettings
import com.github.woodsmarshes.chat.core.model.ParticipantSettings
import com.github.woodsmarshes.chat.events.EventBus
import com.github.woodsmarshes.chat.repository.ContactRepository
import com.github.woodsmarshes.chat.repository.ConversationParticipantRepository
import com.github.woodsmarshes.chat.repository.ConversationRepository
import com.github.woodsmarshes.chat.repository.GroupJoinRequestRepository
import com.github.woodsmarshes.chat.repository.GroupProfileRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

class GroupMembershipServiceTest {

    private val conversationRepository = mockk<ConversationRepository>()
    private val participantRepository = mockk<ConversationParticipantRepository>()
    private val groupJoinRequestRepository = mockk<GroupJoinRequestRepository>()
    private val groupProfileRepository = mockk<GroupProfileRepository>()
    private val contactRepository = mockk<ContactRepository>()
    private val eventBus = mockk<EventBus>(relaxUnitFun = true)

    private val service = GroupMembershipService(
        conversationRepository = conversationRepository,
        conversationParticipantRepository = participantRepository,
        groupJoinRequestRepository = groupJoinRequestRepository,
        groupProfileRepository = groupProfileRepository,
        contactRepository = contactRepository,
        eventBus = eventBus,
    )

    @Test
    fun leaveGroupDeletesParticipantRowOfTheRequestingUser() = runBlocking {
        val conversationId = Uuid.random()
        val userId = Uuid.random()
        val now = Clock.System.now()
        val conversation = Conversation(
            id = conversationId,
            type = ConversationType.GROUP,
            metadata = GroupMetadata(),
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
            lastMessageId = null,
        )
        val groupProfile = GroupProfile(
            conversationId = conversationId,
            name = "group",
            handle = null,
            description = null,
            avatarUrl = null,
            ownerId = userId,
            settings = GroupSettings(),
            createdAt = now,
            updatedAt = now,
        )
        coEvery { conversationRepository.getConversationWithGroupProfile(conversationId) } returns
            (conversation to groupProfile)
        coEvery { participantRepository.getConversationParticipant(userId, conversationId) } returns
            ConversationParticipant(
                conversationId = conversationId,
                userId = userId,
                role = ConversationRole.MEMBER,
                lastReadMessageId = null,
                joinedAt = now,
                settings = ParticipantSettings(),
            )
        coEvery { participantRepository.deleteConversationParticipant(any(), any()) } returns true

        val result = service.leaveGroup(conversationId, userId)

        assertTrue(result.isOk)
        coVerify(exactly = 1) {
            participantRepository.deleteConversationParticipant(userId, conversationId)
        }
    }

    @Test
    fun ownerCannotLeaveGroup() = runBlocking {
        val conversationId = Uuid.random()
        val ownerId = Uuid.random()
        val now = Clock.System.now()
        val conversation = Conversation(
            id = conversationId,
            type = ConversationType.GROUP,
            metadata = GroupMetadata(),
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
            lastMessageId = null,
        )
        val groupProfile = GroupProfile(
            conversationId = conversationId,
            name = "group",
            handle = null,
            description = null,
            avatarUrl = null,
            ownerId = ownerId,
            settings = GroupSettings(),
            createdAt = now,
            updatedAt = now,
        )
        coEvery { conversationRepository.getConversationWithGroupProfile(conversationId) } returns
            (conversation to groupProfile)
        coEvery { participantRepository.getConversationParticipant(ownerId, conversationId) } returns
            ConversationParticipant(
                conversationId = conversationId,
                userId = ownerId,
                role = ConversationRole.OWNER,
                lastReadMessageId = null,
                joinedAt = now,
                settings = ParticipantSettings(),
            )

        val result = service.leaveGroup(conversationId, ownerId)

        assertTrue(result.isErr)
        coVerify(exactly = 0) { participantRepository.deleteConversationParticipant(any(), any()) }
    }
}
