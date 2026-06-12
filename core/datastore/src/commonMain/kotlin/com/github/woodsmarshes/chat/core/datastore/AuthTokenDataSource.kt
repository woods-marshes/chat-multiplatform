package com.github.woodsmarshes.chat.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.github.woodsmarshes.chat.core.model.AuthToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AuthTokenDataSource(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val JWT_TOKEN = stringPreferencesKey("authToken_jwtToken")
        val REFRESH_TOKEN = stringPreferencesKey("authToken_refreshToken")
        val EXPIRY_TIMESTAMP = longPreferencesKey("authToken_expiryTimestamp")
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

    suspend fun setJwtToken(jwtToken: String) {
        dataStore.edit {
            it[Keys.JWT_TOKEN] = jwtToken
        }
    }

    suspend fun setRefreshToken(refreshToken: String) {
        dataStore.edit {
            it[Keys.REFRESH_TOKEN] = refreshToken
        }
    }

    suspend fun setExpiryTimestamp(expiryTimestamp: Long) {
        dataStore.edit {
            it[Keys.EXPIRY_TIMESTAMP] = expiryTimestamp
        }
    }

    suspend fun clearAuthToken() {
        dataStore.edit {
            it.remove(Keys.JWT_TOKEN)
            it.remove(Keys.REFRESH_TOKEN)
            it.remove(Keys.EXPIRY_TIMESTAMP)
        }
    }

}