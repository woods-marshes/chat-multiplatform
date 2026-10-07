package com.github.woodsmarshes.chat.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.github.woodsmarshes.chat.core.model.AuthSessionSnapshot
import com.github.woodsmarshes.chat.core.model.AuthToken
import com.github.woodsmarshes.chat.core.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Atomic snapshot of the persisted authentication token and the credential
 * session [generation] it belongs to.
 */
data class AuthTokenSnapshot(
    val generation: Long,
    val jwtToken: String?,
    val refreshToken: String?,
    val expiryTimestamp: Long?,
)

class AuthTokenDataSource(
    private val dataStore: DataStore<Preferences>,
    private val userSettingDataSource: UserSettingDataSource,
) {
    private val credentialMutex = Mutex()

    init {
        require(userSettingDataSource.sharesDataStoreWith(dataStore)) {
            "AuthTokenDataSource and UserSettingDataSource must share the same DataStore<Preferences> instance"
        }
    }

    internal object Keys {
        val JWT_TOKEN = stringPreferencesKey("authToken_jwtToken")
        val REFRESH_TOKEN = stringPreferencesKey("authToken_refreshToken")
        val EXPIRY_TIMESTAMP = longPreferencesKey("authToken_expiryTimestamp")
        val GENERATION = longPreferencesKey("authToken_generation")
        val AUTH_REQUEST_EPOCH = longPreferencesKey("authToken_requestEpoch")
    }

    val authToken: Flow<AuthToken> = dataStore.data.map { preferences ->
        AuthToken(
            jwtToken = preferences[Keys.JWT_TOKEN],
            refreshToken = preferences[Keys.REFRESH_TOKEN],
            expiryTimestamp = preferences[Keys.EXPIRY_TIMESTAMP]
        )
    }

    val jwtToken: Flow<String?> = dataStore.data.map { preferences ->
        preferences[Keys.JWT_TOKEN]
    }

    val refreshToken: Flow<String?> = dataStore.data.map { preferences ->
        preferences[Keys.REFRESH_TOKEN]
    }

    val expiryTimestamp: Flow<Long?> = dataStore.data.map { preferences ->
        preferences[Keys.EXPIRY_TIMESTAMP]
    }

    val generation: Flow<Long> = dataStore.data.map { preferences ->
        preferences[Keys.GENERATION] ?: 0L
    }

    val snapshot: Flow<AuthTokenSnapshot> = dataStore.data.map { preferences ->
        AuthTokenSnapshot(
            generation = preferences[Keys.GENERATION] ?: 0L,
            jwtToken = preferences[Keys.JWT_TOKEN],
            refreshToken = preferences[Keys.REFRESH_TOKEN],
            expiryTimestamp = preferences[Keys.EXPIRY_TIMESTAMP],
        )
    }

    /**
     * Emits a coherent [AuthSessionSnapshot] mapped from a single
     * [Preferences] snapshot in the shared [dataStore].
     */
    val sessionSnapshot: Flow<AuthSessionSnapshot> = dataStore.data.map { preferences ->
        AuthSessionSnapshot(
            generation = preferences[Keys.GENERATION] ?: 0L,
            user = userSettingDataSource.decodeUserFromPreferences(preferences),
            jwtToken = preferences[Keys.JWT_TOKEN],
            refreshToken = preferences[Keys.REFRESH_TOKEN],
            expiryTimestamp = preferences[Keys.EXPIRY_TIMESTAMP],
        )
    }.distinctUntilChanged()

    suspend fun currentSnapshot(): AuthTokenSnapshot = snapshot.first()

    suspend fun <T> withCredentialLock(block: suspend () -> T): T =
        credentialMutex.withLock { block() }

    /**
     * Issues a monotonically increasing validity ticket for a new login or
     * registration request before its network call starts. Any subsequent
     * [beginAuthRequest] or [invalidateAndClearLocked] advances the epoch and
     * invalidates earlier in-flight requests.
     */
    suspend fun beginAuthRequest(): Long = credentialMutex.withLock {
        var issuedEpoch = 0L
        dataStore.edit { preferences ->
            val next = (preferences[Keys.AUTH_REQUEST_EPOCH] ?: 0L) + 1L
            preferences[Keys.AUTH_REQUEST_EPOCH] = next
            issuedEpoch = next
        }
        issuedEpoch
    }

    /** Must be called while holding [withCredentialLock]. */
    suspend fun isAuthRequestValidLocked(requestEpoch: Long): Boolean {
        val currentEpoch = dataStore.data.first()[Keys.AUTH_REQUEST_EPOCH] ?: 0L
        return currentEpoch == requestEpoch
    }

    /**
     * Commits [user] and [token] as a new credential session generation in a
     * single [DataStore] transaction if and only if [requestEpoch] is still
     * the active auth request epoch. Even a same-account re-login advances
     * [Keys.GENERATION], distinguishing the new session from any prior session
     * of the same user.
     *
     * Must be called while holding [withCredentialLock].
     */
    suspend fun commitSessionLocked(
        requestEpoch: Long,
        user: User,
        token: AuthToken,
    ): Long? {
        var committedGen: Long? = null
        dataStore.edit { preferences ->
            val currentEpoch = preferences[Keys.AUTH_REQUEST_EPOCH] ?: 0L
            if (currentEpoch == requestEpoch) {
                val nextGen = (preferences[Keys.GENERATION] ?: 0L) + 1L
                preferences[Keys.GENERATION] = nextGen
                userSettingDataSource.writeUserInPlace(preferences, user)
                writeTokenInPlace(preferences, token)
                committedGen = nextGen
            }
        }
        return committedGen
    }

    /**
     * Commits a refreshed [user] and [token] (used by `tryAutoLogin` when a
     * legacy token exists without a cached user) only if the stored session
     * still matches [expectedGeneration] and [expectedRefreshToken].
     */
    suspend fun commitRefreshedSessionIfCurrent(
        expectedGeneration: Long,
        expectedRefreshToken: String?,
        user: User,
        token: AuthToken,
    ): Boolean = credentialMutex.withLock {
        var updated = false
        dataStore.edit { preferences ->
            val currentGen = preferences[Keys.GENERATION] ?: 0L
            val currentRefresh = preferences[Keys.REFRESH_TOKEN]
            val currentJwt = preferences[Keys.JWT_TOKEN]
            if (currentGen == expectedGeneration &&
                !currentJwt.isNullOrEmpty() &&
                currentRefresh == expectedRefreshToken
            ) {
                userSettingDataSource.writeUserInPlace(preferences, user)
                writeTokenInPlace(preferences, token)
                updated = true
            }
        }
        updated
    }

    /**
     * Invalidates any in-flight login/register requests, advances the
     * credential session generation, clears stored token and user settings in
     * one transaction, and returns the refresh token that belonged to the
     * cleared session.
     *
     * Must be called while holding [withCredentialLock].
     */
    suspend fun invalidateAndClearLocked(): String? {
        var capturedRefresh: String? = null
        dataStore.edit { preferences ->
            capturedRefresh = preferences[Keys.REFRESH_TOKEN]
            preferences[Keys.AUTH_REQUEST_EPOCH] = (preferences[Keys.AUTH_REQUEST_EPOCH] ?: 0L) + 1L
            preferences[Keys.GENERATION] = (preferences[Keys.GENERATION] ?: 0L) + 1L
            clearTokenInPlace(preferences)
            userSettingDataSource.clearPreferencesInPlace(preferences)
        }
        return capturedRefresh
    }

    /**
     * Updates the stored token pair from a token refresh if and only if the
     * active credential session is still at [expectedGeneration] and still
     * holds [expectedRefreshToken]. Prevents an old session's late refresh
     * response from overwriting a newly logged-in session's credentials.
     */
    suspend fun updateTokenIfCurrent(
        expectedGeneration: Long,
        expectedRefreshToken: String,
        token: AuthToken,
    ): Boolean = credentialMutex.withLock {
        var updated = false
        dataStore.edit { preferences ->
            val currentGen = preferences[Keys.GENERATION] ?: 0L
            val currentRefresh = preferences[Keys.REFRESH_TOKEN]
            val currentJwt = preferences[Keys.JWT_TOKEN]
            if (currentGen == expectedGeneration &&
                !currentJwt.isNullOrEmpty() &&
                currentRefresh == expectedRefreshToken
            ) {
                writeTokenInPlace(preferences, token)
                updated = true
            }
        }
        updated
    }

    /**
     * Clears the stored session when a refresh is rejected with 401 if and
     * only if the active credential session is still at [expectedGeneration]
     * and still holds [expectedRefreshToken]. Prevents a failed refresh from
     * an old session from wiping out a newly logged-in session (including a
     * same-account re-login).
     */
    suspend fun clearIfCurrent(
        expectedGeneration: Long,
        expectedRefreshToken: String,
    ): Boolean = credentialMutex.withLock {
        var cleared = false
        dataStore.edit { preferences ->
            val currentGen = preferences[Keys.GENERATION] ?: 0L
            val currentRefresh = preferences[Keys.REFRESH_TOKEN]
            if (currentGen == expectedGeneration && currentRefresh == expectedRefreshToken) {
                preferences[Keys.GENERATION] = currentGen + 1L
                clearTokenInPlace(preferences)
                userSettingDataSource.clearPreferencesInPlace(preferences)
                cleared = true
            }
        }
        cleared
    }

    /**
     * Persists all token fields in one DataStore transaction and advances the
     * credential session generation so observers never see an inconsistent mix.
     */
    suspend fun setToken(token: AuthToken) {
        credentialMutex.withLock {
            dataStore.edit { preferences ->
                preferences[Keys.GENERATION] = (preferences[Keys.GENERATION] ?: 0L) + 1L
                writeTokenInPlace(preferences, token)
            }
        }
    }

    suspend fun setJwtToken(jwtToken: String) {
        credentialMutex.withLock {
            dataStore.edit { preferences ->
                preferences[Keys.JWT_TOKEN] = jwtToken
            }
        }
    }

    suspend fun setRefreshToken(refreshToken: String) {
        credentialMutex.withLock {
            dataStore.edit {
                it[Keys.REFRESH_TOKEN] = refreshToken
            }
        }
    }

    suspend fun setExpiryTimestamp(expiryTimestamp: Long) {
        credentialMutex.withLock {
            dataStore.edit {
                it[Keys.EXPIRY_TIMESTAMP] = expiryTimestamp
            }
        }
    }

    suspend fun clearAuthToken() {
        credentialMutex.withLock {
            dataStore.edit { preferences ->
                preferences[Keys.AUTH_REQUEST_EPOCH] = (preferences[Keys.AUTH_REQUEST_EPOCH] ?: 0L) + 1L
                preferences[Keys.GENERATION] = (preferences[Keys.GENERATION] ?: 0L) + 1L
                clearTokenInPlace(preferences)
            }
        }
    }

    private fun writeTokenInPlace(preferences: MutablePreferences, token: AuthToken) {
        token.jwtToken?.let { preferences[Keys.JWT_TOKEN] = it } ?: preferences.remove(Keys.JWT_TOKEN)
        token.refreshToken?.let { preferences[Keys.REFRESH_TOKEN] = it } ?: preferences.remove(Keys.REFRESH_TOKEN)
        token.expiryTimestamp?.let { preferences[Keys.EXPIRY_TIMESTAMP] = it }
            ?: preferences.remove(Keys.EXPIRY_TIMESTAMP)
    }

    private fun clearTokenInPlace(preferences: MutablePreferences) {
        preferences.remove(Keys.JWT_TOKEN)
        preferences.remove(Keys.REFRESH_TOKEN)
        preferences.remove(Keys.EXPIRY_TIMESTAMP)
    }
}
