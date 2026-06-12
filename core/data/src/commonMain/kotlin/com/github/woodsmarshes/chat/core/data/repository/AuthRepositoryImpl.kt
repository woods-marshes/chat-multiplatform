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
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.error.AuthError
import com.github.woodsmarshes.chat.core.network.api.rest.AuthApi
import com.github.woodsmarshes.chat.core.network.ktor.bindApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class AuthRepositoryImpl(
    private val authTokenDataSource: AuthTokenDataSource,
    private val userSettingDataSource: UserSettingDataSource,
    private val userDao: UserDao,
    private val authApi: AuthApi,
    private val databaseHolder: DatabaseHolder
) : AuthRepository {
    override fun observeIsLoggedIn(): Flow<Boolean> {
        return authTokenDataSource.jwtToken.map { jwt ->
            val hasToken = !jwt.isNullOrEmpty()
            if (hasToken) {
                val cachedUser = userSettingDataSource.user.first()
                if (cachedUser != null) {
                    databaseHolder.getOrCreateDatabase(cachedUser.id)
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }
    }

    override suspend fun login(
        email: String,
        password: String
    ): Result<User, AuthError> = coroutineBinding {
        bindApi(AuthError::Unknown) {
            authApi.login(email, password)
        }
            .also { resp ->
                userSettingDataSource.setUser(resp.user)
                databaseHolder.getOrCreateDatabase(resp.user.id)
                userDao.insertUser(resp.user.toUserEntity())
                authTokenDataSource.setJwtToken(resp.accessToken)
            }
            .user
    }

    override suspend fun register(
        username: String,
        email: String,
        password: String
    ): Result<User, AuthError> = coroutineBinding {
        bindApi(AuthError::Unknown) {
            authApi.register(username, email, password)
        }
            .also { resp ->
                userSettingDataSource.setUser(resp.user)
                databaseHolder.getOrCreateDatabase(resp.user.id)
                userDao.insertUser(resp.user.toUserEntity())
                authTokenDataSource.setJwtToken(resp.accessToken)
            }
            .user
    }

    override suspend fun logout() {
        databaseHolder.closeDatabase()
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
        return try {
            authApi.refreshToken()
            val user = userSettingDataSource.user.first()
            if (user != null) {
                databaseHolder.getOrCreateDatabase(user.id)
                Ok(user)
            } else {
                Err(AuthError.InvalidCredentials)
            }
        } catch (e: Exception) {
            Err(AuthError.Unknown(e.message))
        }
    }
}
