package com.github.woodsmarshes.chat.feature.settings.ui

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.woodsmarshes.chat.core.data.repository.UserRepository
import com.github.woodsmarshes.chat.core.datastore.BoundCredentialIdentity
import com.github.woodsmarshes.chat.core.datastore.UserSettingDataSource
import com.github.woodsmarshes.chat.core.model.PrivacySetting
import com.github.woodsmarshes.chat.core.model.UserPreference
import com.github.woodsmarshes.chat.core.model.error.UserError
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsWriteTest {
    private fun fixture(): Triple<SettingsViewModel, UserRepository, UserSettingDataSource> {
        val repo = mockk<UserRepository>(relaxed = true)
        val store = mockk<UserSettingDataSource>(relaxed = true)
        every { repo.getMeFlow() } returns flowOf(null)
        every { repo.getGlobalSettingsFlow() } returns emptyFlow()
        every { store.preference } returns flowOf(UserPreference())
        every { store.privacySetting } returns flowOf(PrivacySetting())
        coEvery { repo.syncMe() } returns Err(UserError.PermissionDenied)
        coEvery { store.captureCredentialIdentity() } returns BoundCredentialIdentity(1, Uuid.NIL)
        return Triple(SettingsViewModel(repo, store), repo, store)
    }

    @Test
    fun rapidChangesAreSerializedAndLatestFailureRestoresConfirmedSnapshot() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val (vm, repo, _) = fixture()
            val gate = CompletableDeferred<Unit>()
            val requests = mutableListOf<PrivacySetting>()
            coEvery { repo.updateGlobalSettings(any(), any()) } coAnswers {
                requests += firstArg<PrivacySetting>()
                if (requests.size == 1) { gate.await(); Ok(true) } else Err(UserError.PermissionDenied)
            }
            runCurrent()
            vm.setShowOnlineStatus(false)
            runCurrent()
            vm.setAllowSearch(false)
            runCurrent()
            assertEquals(1, requests.size)
            gate.complete(Unit)
            runCurrent()
            assertEquals(2, requests.size)
            assertFalse(requests.last().showOnlineStatus)
            assertFalse(requests.last().allowSearch)
            assertFalse(vm.uiState.value.showOnlineStatus)
            assertTrue(vm.uiState.value.allowSearch)
            coEvery { repo.updateGlobalSettings(any(), any()) } returns Ok(true)
            vm.setShowOnlineStatus(true)
            runCurrent()
            coVerify { repo.updateGlobalSettings(PrivacySetting(), any()) }
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun oldFailureDoesNotUndoNewIntentAndThemeWriteKeepsOriginalIdentity() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val (vm, repo, store) = fixture()
            val gate = CompletableDeferred<Unit>()
            var calls = 0
            coEvery { repo.updateGlobalSettings(any(), any()) } coAnswers {
                calls++
                if (calls == 1) { gate.await(); Err(UserError.PermissionDenied) } else Ok(true)
            }
            runCurrent()
            vm.setShowOnlineStatus(false)
            runCurrent()
            vm.setAllowSearch(false)
            gate.complete(Unit)
            runCurrent()
            assertFalse(vm.uiState.value.showOnlineStatus)
            assertFalse(vm.uiState.value.allowSearch)
            coEvery { store.captureCredentialIdentity() } returns BoundCredentialIdentity(2, Uuid.random())
            coEvery { store.updateGlobalSettingsIfCurrent(any(), any(), any(), any()) } returns false
            vm.setNotificationSound(false)
            runCurrent()
            coVerify(exactly = 1) { store.updateGlobalSettingsIfCurrent(BoundCredentialIdentity(1, Uuid.NIL), any(), null, any()) }
            coVerify(exactly = 0) { store.setPreference(any()) }
        } finally { Dispatchers.resetMain() }
    }
}
