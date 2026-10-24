package com.github.woodsmarshes.chat.service

import com.github.woodsmarshes.chat.core.model.Conversation
import com.github.woodsmarshes.chat.core.model.ConversationParticipant
import com.github.woodsmarshes.chat.core.model.ConversationRole
import com.github.woodsmarshes.chat.core.model.ConversationType
import com.github.woodsmarshes.chat.core.model.GroupMetadata
import com.github.woodsmarshes.chat.core.model.GroupProfile
import com.github.woodsmarshes.chat.core.model.GroupSettings
import com.github.woodsmarshes.chat.core.model.ParticipantSettings
import com.github.woodsmarshes.chat.events.ConversationEvent
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
import kotlin.test.assertEquals
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

    private fun participantOf(conversationId: Uuid, userId: Uuid, role: ConversationRole) =
        ConversationParticipant(
            conversationId = conversationId,
            userId = userId,
            role = role,
            lastReadMessageId = null,
            joinedAt = Clock.System.now(),
            settings = ParticipantSettings(),
        )

    private fun groupContext(
        conversationId: Uuid,
        inviterId: Uuid,
        inviterRole: ConversationRole,
        allowMemberInvite: Boolean,
    ): Triple<ConversationParticipant, GroupProfile, Conversation> {
        val now = Clock.System.now()
        return Triple(
            participantOf(conversationId, inviterId, inviterRole),
            GroupProfile(
                conversationId = conversationId,
                name = "group",
                handle = null,
                description = null,
                avatarUrl = null,
                ownerId = Uuid.random(),
                settings = GroupSettings(allowMemberInvite = allowMemberInvite),
                createdAt = now,
                updatedAt = now,
            ),
            Conversation(
                id = conversationId,
                type = ConversationType.GROUP,
                metadata = GroupMetadata(),
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
                lastMessageId = null,
            ),
        )
    }

    @Test
    fun inviteUsersToGroupInvitesOnlyFriendsAndPublishesOnce() = runBlocking {
        val conversationId = Uuid.random()
        val inviterId = Uuid.random()
        val friend = Uuid.random()
        val stranger = Uuid.random()
        coEvery { participantRepository.getGroupParticipantContext(inviterId, conversationId) } returns
            groupContext(conversationId, inviterId, ConversationRole.MEMBER, allowMemberInvite = true)
        coEvery { contactRepository.getFriendIds(inviterId) } returns listOf(friend)
        coEvery {
            participantRepository.inviteUsersToConversation(conversationId, inviterId, setOf(friend))
        } returns listOf(participantOf(conversationId, friend, ConversationRole.MEMBER))

        val result = service.inviteUsersToGroup(conversationId, inviterId, listOf(friend, stranger))

        assertTrue(result.isOk)
        // The stranger is filtered out before the write; only the friend row is invited.
        coVerify(exactly = 1) {
            participantRepository.inviteUsersToConversation(conversationId, inviterId, setOf(friend))
        }
        coVerify(exactly = 1) {
            eventBus.publishConversationEvent(
                match {
                    it is ConversationEvent.UserJoinedConversation &&
                        it.conversationId == conversationId &&
                        it.userId == listOf(friend) &&
                        it.inviterId == inviterId
                }
            )
        }
    }

    @Test
    fun inviteUsersToGroupFailsWhenNothingCanBeInvited() = runBlocking {
        val conversationId = Uuid.random()
        val inviterId = Uuid.random()
        val target = Uuid.random()
        coEvery { participantRepository.getGroupParticipantContext(inviterId, conversationId) } returns
            groupContext(conversationId, inviterId, ConversationRole.MEMBER, allowMemberInvite = true)
        // Current pinned semantics: no invitable target (not a friend, or
        // already a member) collapses into OperationFailed.
        coEvery { contactRepository.getFriendIds(inviterId) } returns emptyList()
        coEvery {
            participantRepository.inviteUsersToConversation(conversationId, inviterId, emptySet())
        } returns emptyList()

        val result = service.inviteUsersToGroup(conversationId, inviterId, listOf(target))

        assertTrue(result.isErr)
        coVerify(exactly = 0) { eventBus.publishConversationEvent(any()) }
    }

    @Test
    fun memberCannotInviteWhenGroupDisallowsMemberInvite() = runBlocking {
        val conversationId = Uuid.random()
        val inviterId = Uuid.random()
        coEvery { participantRepository.getGroupParticipantContext(inviterId, conversationId) } returns
            groupContext(conversationId, inviterId, ConversationRole.MEMBER, allowMemberInvite = false)

        val result = service.inviteUsersToGroup(conversationId, inviterId, listOf(Uuid.random()))

        assertTrue(result.isErr)
        coVerify(exactly = 0) { participantRepository.inviteUsersToConversation(any(), any(), any()) }
        coVerify(exactly = 0) { eventBus.publishConversationEvent(any()) }
    }

    @Test
    fun nonParticipantCannotInvite() = runBlocking {
        val conversationId = Uuid.random()
        val inviterId = Uuid.random()
        coEvery { participantRepository.getGroupParticipantContext(inviterId, conversationId) } returns null

        val result = service.inviteUsersToGroup(conversationId, inviterId, listOf(Uuid.random()))

        assertEquals(true, result.isErr)
        coVerify(exactly = 0) { participantRepository.inviteUsersToConversation(any(), any(), any()) }
    }
}
