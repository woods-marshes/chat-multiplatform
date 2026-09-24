package com.github.woodsmarshes.chat.feature.profile.ui

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.fold
import com.github.woodsmarshes.chat.core.data.repository.ConversationRepository
import com.github.woodsmarshes.chat.core.data.repository.UserRepository
import com.github.woodsmarshes.chat.core.model.Conversation
import com.github.woodsmarshes.chat.core.model.ConversationParticipant
import com.github.woodsmarshes.chat.core.model.ConversationType
import com.github.woodsmarshes.chat.core.model.GroupProfile
import com.github.woodsmarshes.chat.core.model.GroupSettings
import com.github.woodsmarshes.chat.core.model.ParticipantSettings
import com.github.woodsmarshes.chat.core.model.PrivacySetting
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.UserPreference
import com.github.woodsmarshes.chat.core.model.UserSetting
import com.github.woodsmarshes.chat.core.model.error.ConversationError
import com.github.woodsmarshes.chat.core.model.error.UserError
import com.github.woodsmarshes.chat.core.model.ui.ConversationHeader
import com.github.woodsmarshes.chat.core.model.ui.SenderUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Offline-first + typed-error handling tests for [ProfileViewModel].
 *
 * Same runBlocking/UnconfinedTestDispatcher rationale as ChatViewModelTest:
 * the VM collects infinite Room flows, which runTest would wait on forever.
 */
class ProfileViewModelTest {

    private val userId = Uuid.parse("00000000-0000-0000-0000-000000000042")
    private val userFlow = MutableStateFlow<User?>(null)
    private var fetchResult: Result<User, UserError>? = null
    private val directChatCreated = MutableStateFlow<Conversation?>(null)

    private fun buildViewModel(argUserId: String = userId.toString()): ProfileViewModel {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        return ProfileViewModel(
            userId = argUserId,
            userRepository = FakeUserRepository(userFlow) { fetchResult },
            conversationRepository = FakeConversationRepository(directChatCreated),
        )
    }

    @Test
    fun cachedUserShowsImmediatelyWhileRefreshFails() {
        try {
            userFlow.value = testUser()
            fetchResult = Err(UserError.Unknown("network down"))
            val vm = buildViewModel()
            assertEquals("alice", vm.uiState.value.username)
            assertFalse(vm.uiState.value.notFound)
            assertFalse(vm.uiState.value.error != null)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun typedNotFoundMarksUserAsMissing() {
        try {
            fetchResult = Err(UserError.NotFound)
            val vm = buildViewModel()
            assertTrue(vm.uiState.value.notFound)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun genericErrorWithoutCacheSurfacesError() {
        try {
            fetchResult = Err(UserError.Unknown("boom"))
            val vm = buildViewModel()
            assertTrue(vm.uiState.value.error != null)
            assertFalse(vm.uiState.value.notFound)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun malformedUserIdShowsNotFoundWithoutNetworkCall() {
        try {
            fetchResult = Err(UserError.Unknown("should not be reached"))
            val vm = buildViewModel(argUserId = "garbage")
            assertTrue(vm.uiState.value.notFound)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun startChatNavigatesWithCreatedConversationId() {
        try {
            userFlow.value = testUser()
            val conversation = testConversation()
            directChatCreated.value = conversation
            val vm = buildViewModel()
            var navigatedTo: String? = null
            vm.startChat { navigatedTo = it }
            assertEquals(conversation.id.toString(), navigatedTo)
            assertFalse(vm.uiState.value.isStartingChat)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun testUser() = User(
        id = userId,
        username = "alice",
        email = null,
        displayName = "Alice",
        avatarUrl = null,
        bio = null,
        createdAt = Instant.fromEpochMilliseconds(0),
        updatedAt = Instant.fromEpochMilliseconds(0),
        deletedAt = null,
    )

    private fun testConversation() = Conversation(
        id = Uuid.parse("00000000-0000-0000-0000-0000000000cc"),
        type = ConversationType.PRIVATE,
        metadata = null,
        createdAt = Instant.fromEpochMilliseconds(0),
        updatedAt = Instant.fromEpochMilliseconds(0),
        deletedAt = null,
        lastMessageId = null,
    )
}

private class FakeUserRepository(
    private val user: MutableStateFlow<User?>,
    private val fetch: () -> Result<User, UserError>?,
) : UserRepository {
    override fun getMeFlow(): Flow<User?> = user
    override suspend fun syncMe(): Result<User, UserError> {
        val current = user.value ?: error("no user")
        return Ok(current)
    }
    override suspend fun updateMyProfile(displayName: String?, avatarUrl: String?, bio: String?) = syncMe()
    override fun getGlobalSettingsFlow(): Flow<UserSetting?> = emptyFlow()
    override suspend fun syncGlobalSettings() = error("not used")
    override suspend fun updateGlobalSettings(
        privacy: PrivacySetting?,
        preferences: UserPreference?,
    ) = error("not used")
    override suspend fun fetchUserDetail(userId: Uuid): Result<User, UserError> {
        val result = fetch() ?: return Err(UserError.Unknown("no stub"))
        result.fold(
            success = { user.value = it },
            failure = {},
        )
        return result
    }
    override fun getUserFlow(userId: Uuid) = user
    override suspend fun searchUsers(keyword: String) = error("not used")
}

private class FakeConversationRepository(
    private val created: Flow<Conversation?>,
) : ConversationRepository {
    override suspend fun getConversationListFlow() = emptyFlow<List<com.github.woodsmarshes.chat.core.model.ui.ConversationUiModel>>()
    override suspend fun syncConversations() = Ok(Unit)
    override suspend fun createDirectChat(targetUserId: Uuid): Result<Conversation, ConversationError> {
        return created.firstOrNull()?.let { Ok(it) } ?: Err(ConversationError.OperationFailed)
    }
    override suspend fun createGroup(name: String, handle: String?, description: String?, avatar: String?, memberIds: List<Uuid>) = error("not used")
    override suspend fun joinGroup(id: Uuid, message: String?) = Ok(Unit)
    override suspend fun updateGroupProfile(
        conversationId: Uuid,
        name: String?,
        description: String?,
        avatarUrl: String?,
        handle: String?,
        ownerId: Uuid?,
        settings: GroupSettings?,
    ) = error("not used")
    override suspend fun getParticipants(id: Uuid): Flow<List<Pair<ConversationParticipant, User>>> = emptyFlow()
    override suspend fun updateMyParticipantSettings(conversationId: Uuid, settings: ParticipantSettings) = Ok(Unit)
    override suspend fun searchGroups(keyword: String) = error("not used")
    override fun getGroupProfileFlow(conversationId: Uuid) = emptyFlow<GroupProfile?>()
    override fun getGroupMembersFlow(conversationId: Uuid) = emptyFlow<List<SenderUser>>()
    override suspend fun refreshGroupDetail(conversationId: Uuid) = Ok(Unit)
    override fun getConversationHeaderFlow(conversationId: Uuid): Flow<ConversationHeader?> = emptyFlow()
}
