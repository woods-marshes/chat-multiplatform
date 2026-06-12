package com.github.woodsmarshes.chat.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.github.woodsmarshes.chat.core.model.PrivacySetting
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.UserPreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlin.let
import kotlin.time.Instant
import kotlin.uuid.Uuid

class UserSettingDataSource(
    private val dataStore: DataStore<Preferences>,
    private val json: Json
) {
    private object Keys {
        val USER = stringPreferencesKey("userJson")

        val PREFERENCE = stringPreferencesKey("preferenceJson")

        val PRIVACY_SETTING = stringPreferencesKey("privacySettingJson")

        val UPDATED_AT = longPreferencesKey("updatedAt")
    }

    val userId: Flow<Uuid?> = dataStore.data.map { preferences ->
        preferences[Keys.USER]?.let { json.decodeFromString<User>(it).id }
    }

    val user: Flow<User?> = dataStore.data.map { preferences ->
        preferences[Keys.USER]?.let { json.decodeFromString<User>(it) }
    }

    val preference: Flow<UserPreference?> = dataStore.data.map { preferences ->
        preferences[Keys.PREFERENCE]?.let { json.decodeFromString<UserPreference>(it) }
    }

    val privacySetting: Flow<PrivacySetting?> = dataStore.data.map { preferences ->
        preferences[Keys.PRIVACY_SETTING]?.let { json.decodeFromString<PrivacySetting>(it) }
    }

    val updatedAt: Flow<Instant?> = dataStore.data.map { preferences ->
        preferences[Keys.UPDATED_AT]?.let { Instant.fromEpochSeconds(it) }
    }

    suspend fun setUser(user: User) {
        dataStore.edit {
            it[Keys.USER] = json.encodeToString(user)
        }
    }

    suspend fun setPreference(preference: UserPreference) {
        dataStore.edit {
            it[Keys.PREFERENCE] = json.encodeToString(preference)
        }
    }

    suspend fun setPrivacySetting(privacySetting: PrivacySetting) {
        dataStore.edit {
            it[Keys.PRIVACY_SETTING] = json.encodeToString(privacySetting)
        }
    }

    suspend fun setUpdatedAt(updatedAt: Instant) {
        dataStore.edit {
            it[Keys.UPDATED_AT] = updatedAt.epochSeconds
        }
    }

    suspend fun clearUserSetting() {
        dataStore.edit { preferences ->
            preferences.remove(Keys.USER)
            preferences.remove(Keys.PRIVACY_SETTING)
            preferences.remove(Keys.PREFERENCE)
        }
    }
}