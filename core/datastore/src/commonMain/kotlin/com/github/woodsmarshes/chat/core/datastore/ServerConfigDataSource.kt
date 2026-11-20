package com.github.woodsmarshes.chat.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Narrow persistence seam implemented by [ServerConfigDataSource] so consumers
 * (e.g. core:network's endpoint provider) and tests can substitute fakes
 * without a real DataStore.
 */
interface ServerConfigStore {
    suspend fun currentServerBaseUrl(): String?

    suspend fun setServerBaseUrl(url: String)
}

/**
 * Persists the user-selected server base URL (normalized `scheme://host[:port]`)
 * across logins and logouts.
 *
 * This is a device-level setting, not a user setting: none of the credential
 * cleanup paths ([AuthTokenDataSource.clearLoginSession],
 * [AuthTokenDataSource.invalidateAndClearLocked],
 * [AuthTokenDataSource.clearIfCurrent] and
 * [UserSettingDataSource.clearPreferencesInPlace]) touch this key, so the
 * selected server survives logout by design — switching servers is only
 * possible from the logged-out login screen.
 */
class ServerConfigDataSource(
    private val dataStore: DataStore<Preferences>,
) : ServerConfigStore {
    internal object Keys {
        val SERVER_BASE_URL = stringPreferencesKey("serverBaseUrl")
    }

    val serverBaseUrl: Flow<String?> =
        dataStore.data.map { preferences ->
            preferences[Keys.SERVER_BASE_URL]
        }

    override suspend fun currentServerBaseUrl(): String? = serverBaseUrl.first()

    override suspend fun setServerBaseUrl(url: String) {
        dataStore.edit { preferences ->
            preferences[Keys.SERVER_BASE_URL] = url
        }
    }
}
