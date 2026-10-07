package com.github.woodsmarshes.chat.core.data.repository

import com.github.michaelbull.result.Result
import com.github.woodsmarshes.chat.core.model.AuthSessionSnapshot
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.error.AuthError
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val jwtToken: Flow<String?>

    fun observeIsLoggedIn(): Flow<Boolean>

    /**
     * Unified snapshot of persisted credential generation, user, and token
     * state mapped from a single Preferences emission when backed by
     * [AuthRepositoryImpl].
     */
    fun observeAuthSession(): Flow<AuthSessionSnapshot>? = null

    suspend fun login(email: String, password: String): Result<User, AuthError>

    suspend fun register(username: String, email: String, password: String): Result<User, AuthError>

    suspend fun logout()

    suspend fun tryAutoLogin(): Result<User, AuthError>
}