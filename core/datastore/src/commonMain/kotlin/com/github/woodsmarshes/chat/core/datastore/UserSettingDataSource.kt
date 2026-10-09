package com.github.woodsmarshes.chat.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.github.woodsmarshes.chat.core.model.PrivacySetting
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.UserPreference
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Identifies the persisted credential session captured when a session-bound
 * repository operation begins, so a late response from an older session cannot
 * overwrite a newer session's user cache or global settings in [DataStore].
 */
data class BoundCredentialIdentity(
    val generation: Long = 0L,
    val userId: Uuid? = null,
)

class UserSettingDataSource(
    private val dataStore: DataStore<Preferences>,
    private val json: Json
) {
    private val log = KotlinLogging.logger {}

    private object Keys {
        val USER = stringPreferencesKey("userJson")

        val PREFERENCE = stringPreferencesKey("preferenceJson")

        val PRIVACY_SETTING = stringPreferencesKey("privacySettingJson")

        val UPDATED_AT = longPreferencesKey("updatedAt")
    }

    // A corrupt or schema-drifted payload must degrade to null instead of
    // killing every collector of the DataStore flow.
    private inline fun <reified T> decodeOrNull(raw: String?): T? {
        if (raw == null) return null
        return runCatching { json.decodeFromString<T>(raw) }
            .onFailure { log.error(it) { "Failed to decode persisted setting" } }
            .getOrNull()
    }

    val userId: Flow<Uuid?> = dataStore.data.map { preferences ->
        decodeOrNull<User>(preferences[Keys.USER])?.id
    }

    val user: Flow<User?> = dataStore.data.map { preferences ->
        decodeOrNull<User>(preferences[Keys.USER])
    }

    val preference: Flow<UserPreference?> = dataStore.data.map { preferences ->
        decodeOrNull<UserPreference>(preferences[Keys.PREFERENCE])
    }

    val privacySetting: Flow<PrivacySetting?> = dataStore.data.map { preferences ->
        decodeOrNull<PrivacySetting>(preferences[Keys.PRIVACY_SETTING])
    }

    val updatedAt: Flow<Instant?> = dataStore.data.map { preferences ->
        preferences[Keys.UPDATED_AT]?.let { Instant.fromEpochSeconds(it) }
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

    /**
     * Captures the active credential generation and user id from a single
     * [Preferences] snapshot.
     */
    suspend fun captureCredentialIdentity(): BoundCredentialIdentity {
        val preferences = dataStore.data.first()
        val generation = preferences[AuthTokenDataSource.Keys.GENERATION] ?: 0L
        val jwt = preferences[AuthTokenDataSource.Keys.JWT_TOKEN]
        val user = decodeUserFromPreferences(preferences)
        if (generation > 0L && jwt.isNullOrEmpty()) {
            return BoundCredentialIdentity(generation = generation, userId = null)
        }
        return BoundCredentialIdentity(
            generation = generation,
            userId = user?.id,
        )
    }

    /**
     * Writes [user] if and only if the persisted credential session still
     * matches [expected] (and [user]'s id matches [expected]'s userId when
     * present). Prevents a stale `syncMe`, `updateMyProfile`, or
     * `uploadMyAvatar` response from overwriting a newly committed login
     * session before cancellation is delivered.
     */
    suspend fun setUserIfCurrent(
        expected: BoundCredentialIdentity,
        user: User,
    ): Boolean {
        if (expected.userId != null && user.id != expected.userId) {
            return false
        }
        var updated = false
        dataStore.edit { preferences ->
            if (isIdentityCurrent(preferences, expected)) {
                writeUserInPlace(preferences, user)
                updated = true
            }
        }
        return updated
    }

    /**
     * Writes [preference], [privacy], and [updatedAt] in one [DataStore]
     * transaction if and only if the persisted credential session still
     * matches [expected]. Prevents a stale `syncGlobalSettings` or
     * `updateGlobalSettings` response from overwriting a newer session's
     * settings.
     */
    suspend fun updateGlobalSettingsIfCurrent(
        expected: BoundCredentialIdentity,
        preference: UserPreference?,
        privacy: PrivacySetting?,
        updatedAt: Instant,
    ): Boolean {
        var updated = false
        dataStore.edit { preferences ->
            if (isIdentityCurrent(preferences, expected)) {
                preference?.let { preferences[Keys.PREFERENCE] = json.encodeToString(it) }
                privacy?.let { preferences[Keys.PRIVACY_SETTING] = json.encodeToString(it) }
                preferences[Keys.UPDATED_AT] = updatedAt.epochSeconds
                updated = true
            }
        }
        return updated
    }

    private fun isIdentityCurrent(
        preferences: Preferences,
        expected: BoundCredentialIdentity,
    ): Boolean {
        val currentGen = preferences[AuthTokenDataSource.Keys.GENERATION] ?: 0L
        if (currentGen != expected.generation) return false
        if (expected.generation > 0L && preferences[AuthTokenDataSource.Keys.JWT_TOKEN].isNullOrEmpty()) {
            return false
        }
        val currentUserId = decodeUserFromPreferences(preferences)?.id
        if (expected.userId != null && currentUserId != expected.userId) {
            return false
        }
        return true
    }

    internal fun sharesDataStoreWith(other: DataStore<Preferences>): Boolean =
        dataStore === other

    internal fun decodeUserFromPreferences(preferences: Preferences): User? =
        decodeOrNull<User>(preferences[Keys.USER])

    internal fun writeUserInPlace(preferences: MutablePreferences, user: User) {
        preferences[Keys.USER] = json.encodeToString(user)
    }

    internal fun clearPreferencesInPlace(preferences: MutablePreferences) {
        preferences.remove(Keys.USER)
        preferences.remove(Keys.PRIVACY_SETTING)
        preferences.remove(Keys.PREFERENCE)
        // Without this the stale timestamp survives logout.
        preferences.remove(Keys.UPDATED_AT)
    }
}
