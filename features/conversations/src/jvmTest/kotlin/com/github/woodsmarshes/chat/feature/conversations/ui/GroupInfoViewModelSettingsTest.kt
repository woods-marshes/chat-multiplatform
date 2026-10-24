package com.github.woodsmarshes.chat.feature.conversations.ui

import com.github.michaelbull.result.Err
import com.github.woodsmarshes.chat.core.data.repository.ContactRepository
import com.github.woodsmarshes.chat.core.data.repository.ConversationRepository
import com.github.woodsmarshes.chat.core.data.repository.UserRepository
import com.github.woodsmarshes.chat.core.model.error.ConversationError
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class GroupInfoViewModelSettingsTest {

    // Relaxed: the ViewModel starts several observation flows on init; the
    // behavior under test is the settings write, stubbed per test.
    private val conversationRepository = mockk<ConversationRepository>(relaxed = true)
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val contactRepository = mockk<ContactRepository>(relaxed = true)
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = GroupInfoViewModel(
        conversationId = Uuid.random().toString(),
        conversationRepository = conversationRepository,
        userRepository = userRepository,
        contactRepository = contactRepository,
    )

    @Test
    fun failedPinToggleRollsBackTheSwitch() = runTest(dispatcher) {
        coEvery { conversationRepository.updateMyParticipantSettings(any(), any()) } returns
            Err(ConversationError.Unknown("offline"))
        val vm = viewModel()

        vm.togglePin(true)

        // The optimistic flip must follow the settings value back down when
        // persistence fails, instead of staying on the requested position.
        assertEquals(false, vm.uiState.value.isPinned)
        assertNull(vm.uiState.value.myParticipantSettings.pinnedAt)
        assertNotNull(vm.uiState.value.actionError)
    }

    @Test
    fun failedMuteToggleRollsBackTheSwitch() = runTest(dispatcher) {
        coEvery { conversationRepository.updateMyParticipantSettings(any(), any()) } returns
            Err(ConversationError.Unknown("offline"))
        val vm = viewModel()

        vm.toggleMute(true)

        assertEquals(false, vm.uiState.value.isMuted)
        assertEquals(true, vm.uiState.value.myParticipantSettings.enableNotification)
        assertNotNull(vm.uiState.value.actionError)
    }

    @Test
    fun successfulPinToggleKeepsTheSwitchOn() = runTest(dispatcher) {
        coEvery { conversationRepository.updateMyParticipantSettings(any(), any()) } returns
            mockk(relaxed = true)
        val vm = viewModel()

        vm.togglePin(true)

        assertEquals(true, vm.uiState.value.isPinned)
        assertNotNull(vm.uiState.value.myParticipantSettings.pinnedAt)
    }
}
