package com.github.woodsmarshes.chat.service

import com.github.michaelbull.result.getError
import com.github.woodsmarshes.chat.core.model.Conversation
import com.github.woodsmarshes.chat.core.model.ConversationParticipant
import com.github.woodsmarshes.chat.core.model.ConversationRole
import com.github.woodsmarshes.chat.core.model.ConversationType
import com.github.woodsmarshes.chat.core.model.GroupMetadata
import com.github.woodsmarshes.chat.core.model.ParticipantSettings
import com.github.woodsmarshes.chat.core.model.error.ConversationError
import com.github.woodsmarshes.chat.core.network.dto.conversation.UpdateConversationSettingsRequest
import com.github.woodsmarshes.chat.events.EventBus
import com.github.woodsmarshes.chat.exceptions.AppException
import com.github.woodsmarshes.chat.repository.ConversationParticipantRepository
import com.github.woodsmarshes.chat.repository.GroupProfileRepository
import com.github.woodsmarshes.chat.utils.connectToH2Database
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

class ConversationSettingsServiceTest {

    // Real H2 connection so the service's dbQuery blocks have a transaction
    // context; every repository call itself is mocked, so no tables are needed.
    private val database = connectToH2Database()

    private val participantRepository = mockk<ConversationParticipantRepository>()
    private val groupProfileRepository = mockk<GroupProfileRepository>()
    private val eventBus = mockk<EventBus>(relaxUnitFun = true)

    private val service = ConversationSettingsService(
        conversationParticipantRepository = participantRepository,
        groupProfileRepository = groupProfileRepository,
        eventBus = eventBus,
    )

    private val ownerId = Uuid.random()
    private val newOwnerId = Uuid.random()
    private val conversationId = Uuid.random()
    private val now = Clock.System.now()

    private fun participant(userId: Uuid, role: ConversationRole) = ConversationParticipant(
        conversationId = conversationId,
        userId = userId,
        role = role,
        lastReadMessageId = null,
        joinedAt = now,
        settings = ParticipantSettings(),
    )

    private fun stubCaller(role: ConversationRole = ConversationRole.OWNER, callerId: Uuid = ownerId) {
        coEvery {
            participantRepository.getConversationParticipantWithConversation(callerId, conversationId)
        } returns Pair(participant(callerId, role), conversation())
    }

    private fun conversation() = Conversation(
        id = conversationId,
        type = ConversationType.GROUP,
        metadata = GroupMetadata(),
        createdAt = now,
        updatedAt = now,
        deletedAt = null,
        lastMessageId = null,
    )

    @Test
    fun transferSwapsRolesAndRepointsProfile() = runBlocking {
        stubCaller()
        coEvery { participantRepository.getConversationParticipant(newOwnerId, conversationId) } returns
            participant(newOwnerId, ConversationRole.PARTICIPANT)
        coEvery { participantRepository.updateConversationParticipantRole(ownerId, conversationId, ConversationRole.ADMIN) } returns true
        coEvery { participantRepository.updateConversationParticipantRole(newOwnerId, conversationId, ConversationRole.OWNER) } returns true
        coEvery { groupProfileRepository.updateGroupProfile(conversationId = conversationId, ownerId = newOwnerId) } returns true
        coEvery {
            groupProfileRepository.updateGroupProfile(conversationId = conversationId, name = null, handle = null, ownerId = null, description = null, avatarUrl = null, settings = null)
        } returns true

        val result = service.updateGroupSettings(
            conversationId, ownerId, UpdateConversationSettingsRequest(ownerId = newOwnerId)
        )

        assertNull(result.getError())
        coVerifyOrder {
            participantRepository.updateConversationParticipantRole(ownerId, conversationId, ConversationRole.ADMIN)
            participantRepository.updateConversationParticipantRole(newOwnerId, conversationId, ConversationRole.OWNER)
            groupProfileRepository.updateGroupProfile(conversationId = conversationId, ownerId = newOwnerId)
        }
        coVerify { eventBus.publishConversationEvent(any()) }
    }

    @Test
    fun transferToNonMemberFailsWithoutTouchingRoles() = runBlocking {
        stubCaller()
        coEvery { participantRepository.getConversationParticipant(newOwnerId, conversationId) } returns null

        val exception = assertFailsWith<AppException> {
            service.updateGroupSettings(conversationId, ownerId, UpdateConversationSettingsRequest(ownerId = newOwnerId))
        }

        assertEquals(ConversationError.NotParticipant, exception.error)
        coVerify(exactly = 0) { participantRepository.updateConversationParticipantRole(any(), any(), any()) }
    }

    @Test
    fun nonOwnerCannotTransfer() = runBlocking {
        stubCaller(role = ConversationRole.MEMBER)

        val result = service.updateGroupSettings(
            conversationId, ownerId, UpdateConversationSettingsRequest(ownerId = newOwnerId)
        )

        assertEquals(ConversationError.PermissionDenied, result.getError())
    }

    @Test
    fun promotedOwnerCanManageTheGroupAfterwards() = runBlocking {
        // The fix for the original defect: after being promoted, the new owner
        // is no longer blocked by the OWNER-only guards.
        stubCaller(callerId = newOwnerId)
        coEvery { participantRepository.getConversationParticipant(ownerId, conversationId) } returns
            participant(ownerId, ConversationRole.ADMIN)
        coEvery { participantRepository.updateConversationParticipantRole(newOwnerId, conversationId, ConversationRole.ADMIN) } returns true
        coEvery { participantRepository.updateConversationParticipantRole(ownerId, conversationId, ConversationRole.OWNER) } returns true
        coEvery { groupProfileRepository.updateGroupProfile(conversationId = conversationId, ownerId = ownerId) } returns true
        coEvery {
            groupProfileRepository.updateGroupProfile(conversationId = conversationId, name = null, handle = null, ownerId = null, description = null, avatarUrl = null, settings = null)
        } returns true

        val result = service.updateGroupSettings(
            conversationId, newOwnerId, UpdateConversationSettingsRequest(ownerId = ownerId)
        )

        assertNull(result.getError())
    }
}
