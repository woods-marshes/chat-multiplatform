package com.github.woodsmarshes.chat.service

import com.github.woodsmarshes.chat.core.model.Conversation
import com.github.woodsmarshes.chat.core.model.ConversationParticipant
import com.github.woodsmarshes.chat.core.model.ConversationRole
import com.github.woodsmarshes.chat.core.model.ConversationType
import com.github.woodsmarshes.chat.core.model.ParticipantSettings
import com.github.woodsmarshes.chat.core.model.PrivateMetadata
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.network.dto.conversation.UserInfo
import com.github.woodsmarshes.chat.events.EventBus
import com.github.woodsmarshes.chat.exceptions.getOrThrow
import com.github.woodsmarshes.chat.repository.ContactRepository
import com.github.woodsmarshes.chat.repository.ConversationParticipantRepository
import com.github.woodsmarshes.chat.repository.ConversationRepository
import com.github.woodsmarshes.chat.repository.GroupProfileRepository
import com.github.woodsmarshes.chat.repository.UserRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

class ConversationLifecycleServiceTest {

    private val conversationRepository = mockk<ConversationRepository>()
    private val participantRepository = mockk<ConversationParticipantRepository>()
    private val groupProfileRepository = mockk<GroupProfileRepository>()
    private val userRepository = mockk<UserRepository>()
    private val contactRepository = mockk<ContactRepository>()
    private val eventBus = mockk<EventBus>(relaxUnitFun = true)

    private val service = ConversationLifecycleService(
        conversationRepository = conversationRepository,
        conversationParticipantRepository = participantRepository,
        groupProfileRepository = groupProfileRepository,
        userRepository = userRepository,
        contactRepository = contactRepository,
        eventBus = eventBus,
    )

    @Test
    fun getUserConversationsIncludesPrivateChats() = runBlocking {
        val userId = Uuid.random()
        val otherUserId = Uuid.random()
        val privateConversationId = Uuid.random()
        val now = Clock.System.now()
        coEvery {
            participantRepository.getConversationParticipantByUserId(userId, emptyList())
        } returns listOf(
            ConversationParticipant(
                conversationId = privateConversationId,
                userId = userId,
                role = ConversationRole.PARTICIPANT,
                lastReadMessageId = null,
                joinedAt = now,
                settings = ParticipantSettings(),
            )
        )
        coEvery { conversationRepository.getConversations(listOf(privateConversationId)) } returns listOf(
            Conversation(
                id = privateConversationId,
                type = ConversationType.PRIVATE,
                metadata = PrivateMetadata(),
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
                lastMessageId = null,
            ) to null
        )
        coEvery {
            userRepository.getPrivateConversationOtherUser(userId, listOf(privateConversationId))
        } returns listOf(
            privateConversationId to User(
                id = otherUserId,
                username = "other",
                email = null,
                displayName = null,
                avatarUrl = null,
                bio = null,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
            )
        )
        coEvery { groupProfileRepository.getGroupProfilesWithUsers(emptyList()) } returns emptyList()

        val result = service.getUserConversations(userId)
        assertTrue(result.isOk)

        val conversations = result.getOrThrow()

        assertEquals(1, conversations.size)
        assertEquals(otherUserId, (conversations.first().conversationInfo as UserInfo).id)
    }
}
