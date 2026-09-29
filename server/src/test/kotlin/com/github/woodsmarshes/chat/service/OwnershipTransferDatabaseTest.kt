package com.github.woodsmarshes.chat.service

import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import com.github.woodsmarshes.chat.core.model.ConversationRole
import com.github.woodsmarshes.chat.core.model.GroupSettings
import com.github.woodsmarshes.chat.core.model.ParticipantSettings
import com.github.woodsmarshes.chat.core.model.error.ConversationError
import com.github.woodsmarshes.chat.core.network.dto.conversation.UpdateConversationSettingsRequest
import com.github.woodsmarshes.chat.events.EventBus
import com.github.woodsmarshes.chat.exceptions.AppException
import com.github.woodsmarshes.chat.repository.ConversationParticipantDataSourceImpl
import com.github.woodsmarshes.chat.repository.GroupProfileDataSourceImpl
import com.github.woodsmarshes.chat.support.TestDb
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid

/**
 * Ownership transfer against a real database, including the rollback proof:
 * transferring to a non-member must leave roles and profile untouched.
 */
class OwnershipTransferDatabaseTest {

    private val eventBus = mockk<EventBus>(relaxUnitFun = true)

    private val participantRepository = ConversationParticipantDataSourceImpl()
    private val groupProfileRepository = GroupProfileDataSourceImpl()

    private val service = ConversationSettingsService(
        conversationParticipantRepository = participantRepository,
        groupProfileRepository = groupProfileRepository,
        eventBus = eventBus,
    )

    @BeforeTest
    fun freshDatabase() {
        TestDb.reset()
    }

    private suspend fun seed(): Triple<Uuid, Uuid, Uuid> {
        val alice = TestDb.user("alice")
        val bob = TestDb.user("bob")
        val carol = TestDb.user("carol")
        TestDb.groupConversation(ownerId = alice, members = listOf(bob, carol))
        return Triple(alice, bob, carol)
    }

    @Test
    fun transferSwapsRolesAndRepointsTheProfileRow(): Unit = runBlocking {
        val (alice, bob, _) = seed()
        val conversationId = participantRepository.getUserConversationParticipants(alice).single().conversationId

        val result = service.updateGroupSettings(
            conversationId, alice, UpdateConversationSettingsRequest(ownerId = bob),
        )

        assertEquals(Unit, result.get())
        assertEquals(bob, groupProfileRepository.getGroupProfile(conversationId)!!.ownerId)
        assertEquals(ConversationRole.ADMIN, participantRepository.getConversationParticipant(alice, conversationId)!!.role)
        assertEquals(ConversationRole.OWNER, participantRepository.getConversationParticipant(bob, conversationId)!!.role)
        coVerify {
            eventBus.publishConversationEvent(match { it is com.github.woodsmarshes.chat.events.ConversationEvent.GroupProfileUpdated })
        }
    }

    @Test
    fun transferToNonMemberFailsAndRollsEverythingBack(): Unit = runBlocking {
        val (alice, bob, carol) = seed()
        val dave = TestDb.user("dave")
        val conversationId = participantRepository.getUserConversationParticipants(alice).single().conversationId

        val exception = assertFailsWith<AppException> {
            service.updateGroupSettings(
                conversationId, alice, UpdateConversationSettingsRequest(ownerId = dave),
            )
        }

        assertEquals(ConversationError.NotParticipant, exception.error)
        // Real rollback: nothing moved.
        assertEquals(alice, groupProfileRepository.getGroupProfile(conversationId)!!.ownerId)
        assertEquals(ConversationRole.OWNER, participantRepository.getConversationParticipant(alice, conversationId)!!.role)
        assertEquals(ConversationRole.MEMBER, participantRepository.getConversationParticipant(bob, conversationId)!!.role)
        assertEquals(ConversationRole.MEMBER, participantRepository.getConversationParticipant(carol, conversationId)!!.role)
        assertNotNull(participantRepository.getConversationParticipant(dave, conversationId) == null)
    }

    @Test
    fun promotedOwnerCanTransferBack(): Unit = runBlocking {
        val (alice, bob, _) = seed()
        val conversationId = participantRepository.getUserConversationParticipants(alice).single().conversationId

        assertEquals(
            Unit,
            service.updateGroupSettings(conversationId, alice, UpdateConversationSettingsRequest(ownerId = bob)).get(),
        )
        assertEquals(
            Unit,
            service.updateGroupSettings(conversationId, bob, UpdateConversationSettingsRequest(ownerId = alice)).get(),
        )

        assertEquals(alice, groupProfileRepository.getGroupProfile(conversationId)!!.ownerId)
        assertEquals(ConversationRole.OWNER, participantRepository.getConversationParticipant(alice, conversationId)!!.role)
        assertEquals(ConversationRole.ADMIN, participantRepository.getConversationParticipant(bob, conversationId)!!.role)
    }

    @Test
    fun memberCannotTriggerATransfer(): Unit = runBlocking {
        val (alice, bob, _) = seed()
        val conversationId = participantRepository.getUserConversationParticipants(alice).single().conversationId

        val result = service.updateGroupSettings(
            conversationId, bob, UpdateConversationSettingsRequest(ownerId = bob),
        )

        assertEquals(ConversationError.PermissionDenied, result.getError())
    }

    @Test
    fun personalSettingsOfANonParticipantMapToNotParticipant(): Unit = runBlocking {
        val (alice, _, _) = seed()
        val dave = TestDb.user("dave")

        val result = service.updatePersonalSettings(
            conversationId = participantRepository.getUserConversationParticipants(alice).single().conversationId,
            userId = dave,
            req = ParticipantSettings(),
        )

        assertEquals(ConversationError.NotParticipant, result.getError())
        assertNotNull(groupProfileRepository.getGroupProfile(participantRepository.getUserConversationParticipants(alice).single().conversationId))
    }

    @Test
    fun unknownSettingsRequestMapsToNotParticipant(): Unit = runBlocking {
        val (alice, _, _) = seed()
        val ghostConversation = Uuid.random()

        val result = service.updateGroupSettings(
            ghostConversation, alice, UpdateConversationSettingsRequest(name = "ghost group"),
        )

        assertEquals(ConversationError.NotParticipant, result.getError())
    }
}
