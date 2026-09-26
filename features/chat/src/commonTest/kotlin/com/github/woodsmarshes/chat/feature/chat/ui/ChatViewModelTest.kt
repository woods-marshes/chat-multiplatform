package com.github.woodsmarshes.chat.feature.chat.ui

import androidx.compose.ui.text.input.TextFieldValue
import androidx.paging.PagingData
import com.github.michaelbull.result.Ok
import com.github.woodsmarshes.chat.core.data.repository.ConversationRepository
import com.github.woodsmarshes.chat.core.data.repository.MessageRepository
import com.github.woodsmarshes.chat.core.data.repository.UserRepository
import com.github.woodsmarshes.chat.core.model.MessageCategory
import com.github.woodsmarshes.chat.core.model.MessageContent
import com.github.woodsmarshes.chat.core.model.MessageRenderType
import com.github.woodsmarshes.chat.core.model.ParticipantSettings
import com.github.woodsmarshes.chat.core.model.PrivacySetting
import com.github.woodsmarshes.chat.core.model.TextContent
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.UserPreference
import com.github.woodsmarshes.chat.core.model.UserSetting
import com.github.woodsmarshes.chat.core.model.error.MessageError
import com.github.woodsmarshes.chat.core.model.ui.ConversationHeader
import com.github.woodsmarshes.chat.core.model.ui.ConversationUiModel
import com.github.woodsmarshes.chat.core.model.ui.MessageState
import com.github.woodsmarshes.chat.core.model.ui.MessageUiModel
import com.github.woodsmarshes.chat.core.model.ui.SenderUser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * State-logic tests for [ChatViewModel].
 *
 * ChatViewModel launches infinite collectors (typing ticker et al.) in its
 * init block. kotlinx.coroutines.test.runTest would hang waiting for the
 * virtual-time scheduler to go idle (the ticker reschedules forever), so the
 * tests use runBlocking with Main routed to an UnconfinedTestDispatcher:
 * viewModelScope coroutines run eagerly on the unconfined dispatcher, the
 * ticker's delays simply never fire, and the JVM exits without waiting.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val conversationId = Uuid.parse("00000000-0000-0000-0000-000000000001")

    private val sentMessages = mutableListOf<Pair<Uuid, MessageContent>>()
    private val sentReplies = mutableListOf<Pair<Uuid, Uuid?>>()
    private val typingSignals = mutableListOf<Pair<Uuid, Boolean>>()
    private var sendInvocations = 0
    private var sendGate: (suspend () -> Unit)? = null

    private fun withViewModel(
        block: (ChatViewModel) -> Unit,
    ) {
        val messageRepository = FakeMessageRepository(
            onSend = { id, content, replyTo ->
                sentMessages += id to content
                sentReplies += id to replyTo
            },
            onSendStarted = { sendInvocations++ },
            onSendGate = { sendGate?.invoke() },
            onTyping = { id, isTyping -> typingSignals += id to isTyping },
        )
        val userRepository = FakeUserRepository(MutableStateFlow(null))
        val conversationRepository = FakeConversationRepository()
        val vm = ChatViewModel(
            conversationId = conversationId.toString(),
            isGroup = true,
            messageRepository = messageRepository,
            userRepository = userRepository,
            conversationRepository = conversationRepository,
        )
        block(vm)
    }

    @Test
    fun invalidConversationIdDoesNotCrash() {
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher())
            try {
                val vm = ChatViewModel(
                    conversationId = "not-a-uuid",
                    isGroup = false,
                    messageRepository = FakeMessageRepository(),
                    userRepository = FakeUserRepository(MutableStateFlow(null)),
                    conversationRepository = FakeConversationRepository(),
                )
                assertNull(vm.uiState.value.header)
            } finally {
                Dispatchers.resetMain()
            }
        }
    }

    @Test
    fun longPressEntersSelectionAndTogglesOff() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            withViewModel { vm ->
                val message = testMessage("m1")

                vm.enterSelection(message)
                assertTrue(vm.uiState.value.selectionMode)
                assertEquals(1, vm.uiState.value.selectedMessages.size)

                vm.toggleSelection(message)
                assertFalse(vm.uiState.value.selectionMode)
                assertEquals(0, vm.uiState.value.selectedMessages.size)
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun selectionSupportsMultipleMessages() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            withViewModel { vm ->
                val m1 = testMessage("m1")
                val m2 = testMessage("m2")

                vm.enterSelection(m1)
                vm.toggleSelection(m2)
                assertEquals(2, vm.uiState.value.selectedMessages.size)

                vm.toggleSelection(m1)
                assertEquals(listOf(m2.id), vm.uiState.value.selectedMessages.map { it.id })
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun replyToggleSetsThenClearsSameMessage() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            withViewModel { vm ->
                val message = testMessage("reply-target")

                vm.toggleReplyTo(message)
                assertEquals(message.id, vm.uiState.value.replyToMessage?.id)

                vm.toggleReplyTo(message)
                assertNull(vm.uiState.value.replyToMessage)
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun replySwitchesToDifferentMessage() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            withViewModel { vm ->
                val m1 = testMessage("r1")
                val m2 = testMessage("r2")

                vm.toggleReplyTo(m1)
                vm.toggleReplyTo(m2)
                assertEquals(m2.id, vm.uiState.value.replyToMessage?.id)
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun forwardStagesMessagesAndSendsToTarget() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            withViewModel { vm ->
                val target = Uuid.parse("00000000-0000-0000-0000-000000000099")
                val m1 = testMessage("f1")
                val m2 = testMessage("f2")

                vm.startForward(listOf(m1, m2))
                assertEquals(2, vm.uiState.value.forwardingMessages.size)

                vm.forwardMessages(target)
                // Unconfined dispatcher: the forward coroutine completes
                // synchronously against the fakes.
                assertEquals(
                    listOf(target to m1.content, target to m2.content),
                    sentMessages,
                )
                assertTrue(vm.uiState.value.forwardingMessages.isEmpty())
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun clearSelectionExitsSelectionMode() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            withViewModel { vm ->
                vm.enterSelection(testMessage("x"))
                assertTrue(vm.uiState.value.selectionMode)

                vm.clearSelection()
                assertFalse(vm.uiState.value.selectionMode)
                assertTrue(vm.uiState.value.selectedMessages.isEmpty())
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * A failed bubble restored from the database has no draft behind it, so the retry has to
     * resend the message itself. Reusing the input box made the tap a silent no-op there and
     * sent unrelated text once the user had typed again.
     */
    @Test
    fun retrySendsTheFailedMessageItself() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            withViewModel { vm ->
                val target = testMessage("target")
                val failed = testMessage("failed").copy(
                    content = TextContent("original text"),
                    replyTo = target,
                    sendStatus = MessageState.SendFailed("offline"),
                )
                vm.onInputChanged(TextFieldValue("unrelated draft"))

                vm.retryMessage(failed)

                assertEquals(1, sentMessages.size)
                assertEquals(TextContent("original text"), sentMessages.single().second)
                assertEquals(listOf<Uuid?>(target.id), sentReplies.map { it.second })
                assertEquals("unrelated draft", vm.input.value.text, "a retry must not consume the draft")
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun doubleTapSendsTheMessageOnlyOnce() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val gate = CompletableDeferred<Unit>()
            sendGate = { gate.await() }
            withViewModel { vm ->
                vm.onInputChanged(TextFieldValue("only once"))

                vm.sendMessage()
                vm.sendMessage()

                assertEquals(1, sendInvocations, "the second tap must be rejected while a send is in flight")

                gate.complete(Unit)
                assertEquals(1, sentMessages.size)
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun testMessage(seed: String): MessageUiModel {
        val hex = seed.hashCode().toUInt().toString(16).padStart(12, '0').take(12)
        val id = Uuid.parse("00000000-0000-0000-0000-$hex")
        return MessageUiModel(
            id = id,
            conversationId = conversationId,
            sender = null,
            category = MessageCategory.NORMAL,
            renderType = MessageRenderType.TEXT,
            createdAt = Instant.fromEpochMilliseconds(0),
            content = TextContent("hello $seed"),
            sendStatus = MessageState.Completed,
        )
    }

    @AfterTest
    fun tearDown() {
        sentMessages.clear()
        sentReplies.clear()
        typingSignals.clear()
        sendInvocations = 0
        sendGate = null
    }
}

private class FakeUserRepository(
    private val me: Flow<User?>,
) : UserRepository {
    override fun getMeFlow(): Flow<User?> = me
    override suspend fun syncMe() = Ok(
        User(
            id = Uuid.NIL,
            username = "",
            email = null,
            displayName = null,
            avatarUrl = null,
            bio = null,
            createdAt = Instant.fromEpochMilliseconds(0),
            updatedAt = Instant.fromEpochMilliseconds(0),
            deletedAt = null,
        )
    )
    override suspend fun updateMyProfile(displayName: String?, avatarUrl: String?, bio: String?) = syncMe()
    override fun getGlobalSettingsFlow(): Flow<UserSetting?> = emptyFlow()
    override suspend fun syncGlobalSettings() = error("not used")
    override suspend fun updateGlobalSettings(
        privacy: PrivacySetting?,
        preferences: UserPreference?,
    ) = error("not used")
    override suspend fun fetchUserDetail(userId: Uuid) = error("not used")
    override fun getUserFlow(userId: Uuid) = emptyFlow<User?>()
    override suspend fun searchUsers(keyword: String) = error("not used")
}

private class FakeConversationRepository(
    private val header: Flow<ConversationHeader?> = MutableStateFlow(null),
    private val members: Flow<List<SenderUser>> = MutableStateFlow(emptyList()),
) : ConversationRepository {
    override suspend fun getConversationListFlow() = emptyFlow<List<ConversationUiModel>>()
    override suspend fun syncConversations() = Ok(Unit)
    override suspend fun createDirectChat(targetUserId: Uuid) = error("not used")
    override suspend fun createGroup(name: String, handle: String?, description: String?, avatar: String?, memberIds: List<Uuid>) = error("not used")
    override suspend fun joinGroup(id: Uuid, message: String?) = Ok(Unit)
    override suspend fun updateGroupProfile(
        conversationId: Uuid,
        name: String?,
        description: String?,
        avatarUrl: String?,
        handle: String?,
        ownerId: Uuid?,
        settings: com.github.woodsmarshes.chat.core.model.GroupSettings?,
    ) = error("not used")
    override suspend fun getParticipants(id: Uuid): Flow<List<Pair<com.github.woodsmarshes.chat.core.model.ConversationParticipant, User>>> = emptyFlow()
    override suspend fun updateMyParticipantSettings(conversationId: Uuid, settings: ParticipantSettings) = Ok(Unit)
    override suspend fun searchGroups(keyword: String) = error("not used")
    override fun getGroupProfileFlow(conversationId: Uuid) = emptyFlow<com.github.woodsmarshes.chat.core.model.GroupProfile?>()
    override fun getGroupMembersFlow(conversationId: Uuid) = members
    override suspend fun refreshGroupDetail(conversationId: Uuid) = Ok(Unit)
    override fun getConversationHeaderFlow(conversationId: Uuid) = header
}

private class FakeMessageRepository(
    private val onSend: (Uuid, MessageContent, Uuid?) -> Unit = { _, _, _ -> },
    private val onSendStarted: () -> Unit = {},
    private val onSendGate: suspend () -> Unit = {},
    private val onTyping: (Uuid, Boolean) -> Unit = { _, _ -> },
) : MessageRepository {
    override suspend fun retryPendingMessages() = Unit
    override fun getMessages(
        ownUserId: Uuid,
        conversationId: Uuid,
        isGroup: Boolean,
        limit: Int,
    ): Flow<PagingData<MessageUiModel>> = emptyFlow()
    override suspend fun sendMessage(
        conversationId: Uuid,
        content: MessageContent,
        replyToMessageId: Uuid?,
    ): com.github.michaelbull.result.Result<Unit, MessageError> {
        onSendStarted()
        onSendGate()
        onSend(conversationId, content, replyToMessageId)
        return Ok(Unit)
    }
    override suspend fun revokeMessage(messageId: Uuid) = Unit
    override suspend fun markAsRead(conversationId: Uuid, messageId: Uuid) = Unit
    override fun getTypingUsersFlow(conversationId: Uuid) = MutableStateFlow(emptyMap<Uuid, Long>())
    override suspend fun sendTyping(conversationId: Uuid, isTyping: Boolean) {
        onTyping(conversationId, isTyping)
    }
}
