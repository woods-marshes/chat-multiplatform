package com.github.woodsmarshes.chat.core.data.repository

import com.github.michaelbull.result.Result
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.error.AuthError
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    fun observeIsLoggedIn(): Flow<Boolean>

    suspend fun login(email: String, password: String): Result<User, AuthError>

    suspend fun register(username: String, email: String, password: String): Result<User, AuthError>

    suspend fun logout()

    suspend fun tryAutoLogin(): Result<User, AuthError>
}