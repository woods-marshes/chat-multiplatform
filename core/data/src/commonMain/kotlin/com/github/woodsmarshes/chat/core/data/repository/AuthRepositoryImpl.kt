package com.github.woodsmarshes.chat.core.data.repository

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.woodsmarshes.chat.core.data.model.toUserEntity
import com.github.woodsmarshes.chat.core.database.dao.UserDao
import com.github.woodsmarshes.chat.core.database.di.DatabaseHolder
import com.github.woodsmarshes.chat.core.datastore.AuthTokenDataSource
import com.github.woodsmarshes.chat.core.datastore.UserSettingDataSource
import com.github.woodsmarshes.chat.core.model.AuthToken
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.error.AuthError
import com.github.woodsmarshes.chat.core.network.api.rest.AuthApi
import com.github.woodsmarshes.chat.core.network.dto.auth.AuthResponse
import com.github.woodsmarshes.chat.core.network.ktor.bindApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

class AuthRepositoryImpl(
    private val authTokenDataSource: AuthTokenDataSource,
    private val userSettingDataSource: UserSettingDataSource,
    private val userDao: UserDao,
    private val authApi: AuthApi,
    private val databaseHolder: DatabaseHolder,
) : AuthRepository {

    override val jwtToken: Flow<String?> = authTokenDataSource.jwtToken

    /**
     * Pure mapping of persisted auth state: logged in means both a token and
     * a usable cached user exist. Database opening is deliberately NOT done
     * here — that is the SessionManager's job, driven by an eagerly collected
     * session flow rather than whatever happens to be subscribed.
     */
    override fun observeIsLoggedIn(): Flow<Boolean> {
        return combine(
            authTokenDataSource.jwtToken,
            userSettingDataSource.user,
        ) { jwt, user -> !jwt.isNullOrEmpty() && user != null }
    }

    override suspend fun login(
        email: String,
        password: String
    ): Result<User, AuthError> = coroutineBinding {
        val resp = bindApi(AuthError::Unknown) {
            authApi.login(email, password)
        }
        persistAuthResponse(resp)
        resp.user
    }

    override suspend fun register(
        username: String,
        email: String,
        password: String
    ): Result<User, AuthError> = coroutineBinding {
        val resp = bindApi(AuthError::Unknown) {
            authApi.register(username, email, password)
        }
        persistAuthResponse(resp)
        resp.user
    }

    /**
     * Persists user + token atomically: the token write is a single DataStore
     * transaction and the user cache is written before the token, so a crash
     * mid-way can only leave "user without token" (safe, treated as logged
     * out) rather than "token without usable user".
     */
    private suspend fun persistAuthResponse(resp: AuthResponse) {
        userSettingDataSource.setUser(resp.user)
        databaseHolder.getOrCreateDatabase(resp.user.id)
        userDao.insertUser(resp.user.toUserEntity())
        authTokenDataSource.setToken(
            AuthToken(jwtToken = resp.accessToken, refreshToken = null, expiryTimestamp = null)
        )
    }

    /**
     * Clears the persisted session only. Closing the per-user database is not
     * done here: it belongs to the session owner (SessionManager), which can
     * wait for the authenticated UI and its in-flight queries to tear down
     * before the driver goes away.
     */
    override suspend fun logout() {
        userSettingDataSource.clearUserSetting()
        authTokenDataSource.clearAuthToken()
    }

    override suspend fun tryAutoLogin(): Result<User, AuthError> {
        val token = authTokenDataSource.jwtToken.first()
        if (token.isNullOrEmpty()) {
            return Err(AuthError.InvalidCredentials)
        }
        val cachedUser = userSettingDataSource.user.first()
        if (cachedUser != null) {
            databaseHolder.getOrCreateDatabase(cachedUser.id)
            return Ok(cachedUser)
        }
        // No cached user: refresh the token and persist the response so the
        // next launch has a usable session instead of relying on a cache
        // that never existed.
        return try {
            val resp = authApi.refreshToken()
            persistAuthResponse(resp)
            Ok(resp.user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Err(AuthError.Unknown(e.message))
        }
    }
}
