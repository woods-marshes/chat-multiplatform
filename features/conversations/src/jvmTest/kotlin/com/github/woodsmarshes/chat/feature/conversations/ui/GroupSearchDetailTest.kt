package com.github.woodsmarshes.chat.feature.conversations.ui

import androidx.lifecycle.ViewModelStore
import com.github.michaelbull.result.Err
import com.github.woodsmarshes.chat.core.data.repository.ContactRepository
import com.github.woodsmarshes.chat.core.data.repository.ConversationRepository
import com.github.woodsmarshes.chat.core.data.repository.UserRepository
import com.github.woodsmarshes.chat.core.model.GroupProfile
import com.github.woodsmarshes.chat.core.model.GroupSettings
import com.github.woodsmarshes.chat.core.model.error.ConversationError
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class GroupSearchDetailTest {
    @Test
    fun publicSnapshotSurvivesMemberOnlyEndpointDenialWithoutGrantingMembership() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val id = Uuid.parse("00000000-0000-0000-0000-000000000111")
            val profile = GroupProfile(id, "Public group", "dingdong", "Public description", null,
                Uuid.parse("00000000-0000-0000-0000-000000000222"),
                GroupSettings(joinApprovalRequired = true), Instant.fromEpochMilliseconds(1), Instant.fromEpochMilliseconds(1))
            val conversations = mockk<ConversationRepository>()
            every { conversations.getGroupProfileFlow(id) } returns flowOf(null)
            every { conversations.getGroupMembersFlow(id) } returns flowOf(emptyList())
            every { conversations.getMyParticipantFlow(id) } returns flowOf(null)
            every { conversations.observeGroupJoinRequests(id, any()) } returns flowOf(emptyList())
            coEvery { conversations.refreshGroupDetail(id) } returns Err(ConversationError.NotParticipant)
            val users = mockk<UserRepository>()
            every { users.getMeFlow() } returns flowOf(null)
            val contacts = mockk<ContactRepository>()
            coEvery { contacts.getFriendsFlow() } returns flowOf(emptyList())
            val vm = GroupInfoViewModel(id.toString(), conversations, users, contacts, profile)
            store.put("public", vm)
            assertEquals("Public group", vm.uiState.value.name)
            assertEquals("Public description", vm.uiState.value.description)
            assertFalse(vm.uiState.value.isLoading)
            assertFalse(vm.uiState.value.notFound)
            assertFalse(vm.uiState.value.isMember)
            assertNull(vm.uiState.value.myRole)
            vm.onJoinButtonClick()
            assertTrue(vm.uiState.value.showJoinDialog)

            val noSnapshot = GroupInfoViewModel(id.toString(), conversations, users, contacts)
            store.put("missing", noSnapshot)
            assertTrue(noSnapshot.uiState.value.notFound)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }
}
