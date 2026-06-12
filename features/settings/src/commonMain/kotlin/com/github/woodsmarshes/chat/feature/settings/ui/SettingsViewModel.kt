package com.github.woodsmarshes.chat.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.woodsmarshes.chat.core.datastore.UserSettingDataSource
import com.github.woodsmarshes.chat.core.data.repository.AuthRepository
import com.github.woodsmarshes.chat.core.model.DarkThemeConfig
import com.github.woodsmarshes.chat.core.model.ThemeBrand
import com.github.woodsmarshes.chat.core.model.UserPreference
import com.github.woodsmarshes.chat.feature.settings.model.SettingsUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val userSettingDataSource: UserSettingDataSource,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

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
                    showOnlineStatus = true,
                    allowSearch = true,
                )
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
        _uiState.value = _uiState.value.copy(showOnlineStatus = show)
    }

    fun setAllowSearch(allow: Boolean) {
        _uiState.value = _uiState.value.copy(allowSearch = allow)
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
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
