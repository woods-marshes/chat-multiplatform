package com.github.woodsmarshes.chat.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.michaelbull.result.onErr
import com.github.woodsmarshes.chat.core.data.repository.UserRepository
import com.github.woodsmarshes.chat.core.datastore.UserSettingDataSource
import com.github.woodsmarshes.chat.core.model.DarkThemeConfig
import com.github.woodsmarshes.chat.core.model.PrivacySetting
import com.github.woodsmarshes.chat.core.model.ThemeBrand
import com.github.woodsmarshes.chat.core.model.UserPreference
import com.github.woodsmarshes.chat.feature.settings.model.SettingsUiState
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val userRepository: UserRepository,
    private val userSettingDataSource: UserSettingDataSource,
) : ViewModel() {

    private val log = KotlinLogging.logger {}

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    // Latest privacy snapshot; the switches read-modify-write this object so
    // other fields (friend request policy etc.) are never lost on persist.
    private var privacy: PrivacySetting = PrivacySetting()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            userSettingDataSource.preference.first()?.let { pref ->
                _uiState.value = _uiState.value.copy(
                    themeBrand = pref.themeBrand,
                    darkThemeConfig = pref.darkThemeConfig,
                    notificationSound = pref.notificationSound,
                )
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
                    privacy = it.privacy
                    _uiState.value = _uiState.value.copy(
                        showOnlineStatus = it.privacy.showOnlineStatus,
                        allowSearch = it.privacy.allowSearch,
                    )
                }
            }
        }
    }

    fun setThemeBrand(themeBrand: ThemeBrand) {
        _uiState.value = _uiState.value.copy(themeBrand = themeBrand)
        saveThemePreference()
    }

    fun setDarkThemeConfig(darkThemeConfig: DarkThemeConfig) {
        _uiState.value = _uiState.value.copy(darkThemeConfig = darkThemeConfig)
        saveThemePreference()
    }

    fun setNotificationSound(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(notificationSound = enabled)
        saveThemePreference()
    }

    fun setShowOnlineStatus(show: Boolean) {
        // Only this field is rolled back on failure: reverting the whole snapshot would
        // also undo a concurrent toggle of the other switch.
        val previous = privacy.showOnlineStatus
        _uiState.update { it.copy(showOnlineStatus = show) }
        persistPrivacy(privacy.copy(showOnlineStatus = show)) {
            _uiState.update { it.copy(showOnlineStatus = previous) }
        }
    }

    fun setAllowSearch(allow: Boolean) {
        val previous = privacy.allowSearch
        _uiState.update { it.copy(allowSearch = allow) }
        persistPrivacy(privacy.copy(allowSearch = allow)) {
            _uiState.update { it.copy(allowSearch = previous) }
        }
    }

    /**
     * Optimistically persists the new privacy snapshot to the server and the
     * local cache; on failure the caller restores the single field it owns.
     */
    private fun persistPrivacy(next: PrivacySetting, onRevert: () -> Unit) {
        privacy = next
        viewModelScope.launch {
            userRepository.updateGlobalSettings(privacy = next).onErr { err ->
                log.error { "[SettingsVM] persist privacy failed, reverting: $err" }
                onRevert()
            }
        }
    }

    private fun saveThemePreference() {
        val state = _uiState.value
        viewModelScope.launch {
            userSettingDataSource.setPreference(
                UserPreference(
                    themeBrand = state.themeBrand,
                    darkThemeConfig = state.darkThemeConfig,
                    notificationSound = state.notificationSound,
                )
            )
        }
    }
}
