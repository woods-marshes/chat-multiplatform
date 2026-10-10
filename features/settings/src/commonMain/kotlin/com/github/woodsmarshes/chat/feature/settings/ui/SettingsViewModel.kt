package com.github.woodsmarshes.chat.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.michaelbull.result.onErr
import com.github.michaelbull.result.onOk
import com.github.woodsmarshes.chat.core.data.repository.UserRepository
import com.github.woodsmarshes.chat.core.datastore.BoundCredentialIdentity
import com.github.woodsmarshes.chat.core.datastore.UserSettingDataSource
import com.github.woodsmarshes.chat.core.model.DarkThemeConfig
import com.github.woodsmarshes.chat.core.model.FriendRequestPolicy
import com.github.woodsmarshes.chat.core.model.PrivacySetting
import com.github.woodsmarshes.chat.core.model.ProfileVisibility
import com.github.woodsmarshes.chat.core.model.ThemeBrand
import com.github.woodsmarshes.chat.core.model.UserPreference
import com.github.woodsmarshes.chat.core.ui.resources.getLocaleStrings
import com.github.woodsmarshes.chat.feature.settings.model.SettingsUiState
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SettingsViewModel(
    private val userRepository: UserRepository,
    private val userSettingDataSource: UserSettingDataSource,
) : ViewModel() {

    private val log = KotlinLogging.logger {}
    private val strings = getLocaleStrings()

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    // Latest privacy snapshot; the switches read-modify-write this object so
    // other fields (friend request policy etc.) are never lost on persist.
    private var privacy: PrivacySetting = PrivacySetting()
    private var confirmedPrivacy: PrivacySetting = PrivacySetting()
    private var privacyRevision = 0L
    private var privacyWriter: Job? = null
    private val preferenceMutex = Mutex()
    private var hasInitializedProfileForm = false
    // Last preference snapshot actually persisted; the revert path mirrors it
    // back into the UI when a write is rejected.
    private var confirmedPreference: UserPreference? = null

    // A Deferred rather than a late-captured var: a toggle racing the VM's
    // construction used to find a null identity and be silently dropped.
    private val preferenceIdentity: Deferred<BoundCredentialIdentity> =
        viewModelScope.async { userSettingDataSource.captureCredentialIdentity() }

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            if (userRepository.getMeFlow().first() == null) {
                userRepository.syncMe().onErr { err ->
                    log.warn { "[SettingsVM] syncMe failed: $err" }
                }
            }
            userRepository.getMeFlow().collect { user ->
                if (user != null) {
                    _uiState.update { state ->
                        val name = user.displayName.orEmpty()
                        val userBio = user.bio.orEmpty()
                        val shouldSyncForm = !hasInitializedProfileForm ||
                            (state.editDisplayName == state.displayName && state.editBio == state.bio)
                        hasInitializedProfileForm = true
                        state.copy(
                            userId = user.id.toString(),
                            username = user.username,
                            email = user.email.orEmpty(),
                            displayName = name,
                            bio = userBio,
                            avatarUrl = user.avatarUrl,
                            editDisplayName = if (shouldSyncForm) name else state.editDisplayName,
                            editBio = if (shouldSyncForm) userBio else state.editBio,
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            userSettingDataSource.preference.collect { pref ->
                if (pref != null) {
                    confirmedPreference = pref
                    _uiState.update {
                        it.copy(
                            themeBrand = pref.themeBrand,
                            darkThemeConfig = pref.darkThemeConfig,
                            notificationSound = pref.notificationSound,
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            // Seed the privacy cache from the server if it was never persisted;
            // getGlobalSettingsFlow only publishes once all parts are cached.
            if (userSettingDataSource.privacySetting.first() == null) {
                userRepository.syncGlobalSettings().onErr { err ->
                    log.warn { "[SettingsVM] privacy sync failed: $err" }
                }
            }
            userRepository.getGlobalSettingsFlow().collect { settings ->
                settings?.let {
                    if (privacyWriter?.isActive == true) return@collect
                    privacy = it.privacy
                    confirmedPrivacy = it.privacy
                    renderPrivacy(it.privacy)
                }
            }
        }
    }

    fun onEditDisplayNameChange(value: String) {
        _uiState.update {
            it.copy(
                editDisplayName = value,
                profileMessage = null,
                profileError = null,
            )
        }
    }

    fun onEditBioChange(value: String) {
        _uiState.update {
            it.copy(
                editBio = value,
                profileMessage = null,
                profileError = null,
            )
        }
    }

    fun saveProfile() {
        val current = _uiState.value
        if (current.isSavingProfile) return
        val newDisplayName = current.editDisplayName.trim()
        val newBio = current.editBio.trim()

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSavingProfile = true,
                    profileMessage = null,
                    profileError = null,
                )
            }
            userRepository.updateMyProfile(
                displayName = newDisplayName,
                bio = newBio,
            ).onOk { updatedUser ->
                _uiState.update {
                    it.copy(
                        isSavingProfile = false,
                        displayName = updatedUser.displayName.orEmpty(),
                        bio = updatedUser.bio.orEmpty(),
                        editDisplayName = updatedUser.displayName.orEmpty(),
                        editBio = updatedUser.bio.orEmpty(),
                        profileMessage = strings.profileSaved,
                    )
                }
            }.onErr { err ->
                log.error { "[SettingsVM] updateMyProfile failed: $err" }
                _uiState.update {
                    it.copy(
                        isSavingProfile = false,
                        profileError = strings.profileSaveFailed,
                    )
                }
            }
        }
    }

    fun uploadAvatar(bytes: ByteArray) {
        if (_uiState.value.isUploadingAvatar) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isUploadingAvatar = true,
                    profileMessage = null,
                    profileError = null,
                )
            }
            userRepository.uploadMyAvatar(bytes).onOk { url ->
                _uiState.update {
                    it.copy(
                        isUploadingAvatar = false,
                        avatarUrl = url,
                        profileMessage = strings.avatarUploadSuccess,
                    )
                }
            }.onErr { err ->
                log.error { "[SettingsVM] uploadMyAvatar failed: $err" }
                _uiState.update {
                    it.copy(
                        isUploadingAvatar = false,
                        profileError = strings.avatarUploadFailed,
                    )
                }
            }
        }
    }

    fun clearProfileFeedback() {
        _uiState.update {
            it.copy(
                profileMessage = null,
                profileError = null,
            )
        }
    }

    fun setThemeBrand(themeBrand: ThemeBrand) {
        _uiState.update { it.copy(themeBrand = themeBrand) }
        saveThemePreference()
    }

    fun setDarkThemeConfig(darkThemeConfig: DarkThemeConfig) {
        _uiState.update { it.copy(darkThemeConfig = darkThemeConfig) }
        saveThemePreference()
    }

    fun setNotificationSound(enabled: Boolean) {
        _uiState.update { it.copy(notificationSound = enabled) }
        saveThemePreference()
    }

    fun setShowOnlineStatus(show: Boolean) = persistPrivacy(privacy.copy(showOnlineStatus = show))

    fun setAllowSearch(allow: Boolean) = persistPrivacy(privacy.copy(allowSearch = allow))

    fun setAllowStrangerChat(allow: Boolean) = persistPrivacy(privacy.copy(allowStrangerChat = allow))

    fun setFriendRequestPolicy(policy: FriendRequestPolicy) =
        persistPrivacy(privacy.copy(friendRequestPolicy = policy))

    fun setProfileVisibility(visibility: ProfileVisibility) =
        persistPrivacy(privacy.copy(profileVisibility = visibility))

    private fun renderPrivacy(value: PrivacySetting) {
        _uiState.update {
            it.copy(
                showOnlineStatus = value.showOnlineStatus,
                allowSearch = value.allowSearch,
                allowStrangerChat = value.allowStrangerChat,
                friendRequestPolicy = value.friendRequestPolicy,
                profileVisibility = value.profileVisibility,
            )
        }
    }

    /** A single writer coalesces newer intent without allowing older failures to undo it. */
    private fun persistPrivacy(next: PrivacySetting) {
        privacy = next
        privacyRevision++
        renderPrivacy(next)
        if (privacyWriter?.isActive == true) return
        privacyWriter = viewModelScope.launch {
            while (true) {
                val revision = privacyRevision
                val requested = privacy
                userRepository.updateGlobalSettings(privacy = requested)
                    .onOk { success ->
                        if (success) confirmedPrivacy = requested
                        else if (revision == privacyRevision) {
                            privacy = confirmedPrivacy
                            renderPrivacy(privacy)
                        }
                    }
                    .onErr { error ->
                        log.error { "[SettingsVM] privacy update failed: $error" }
                        if (revision == privacyRevision) {
                            privacy = confirmedPrivacy
                            renderPrivacy(privacy)
                        }
                    }
                if (revision == privacyRevision) break
            }
        }
    }

    private fun saveThemePreference() {
        val state = _uiState.value
        viewModelScope.launch {
            val success = try {
                val identity = preferenceIdentity.await()
                preferenceMutex.withLock {
                    userSettingDataSource.updateGlobalSettingsIfCurrent(
                        expected = identity,
                        privacy = null,
                        updatedAt = kotlin.time.Clock.System.now(),
                        preference = UserPreference(
                            themeBrand = state.themeBrand,
                            darkThemeConfig = state.darkThemeConfig,
                            notificationSound = state.notificationSound,
                        )
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                log.error(e) { "[SettingsVM] preference write failed" }
                false
            }
            if (!success) {
                // The write never landed: mirror the last persisted preference
                // back into the UI so a flipped toggle is not shown as saved.
                log.warn { "[SettingsVM] preference write rejected; reverting UI" }
                confirmedPreference?.let { confirmed ->
                    _uiState.update {
                        it.copy(
                            themeBrand = confirmed.themeBrand,
                            darkThemeConfig = confirmed.darkThemeConfig,
                            notificationSound = confirmed.notificationSound,
                        )
                    }
                }
            }
        }
    }
}
