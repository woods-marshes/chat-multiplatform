package com.github.woodsmarshes.chat.core.network.api.rest

import com.github.woodsmarshes.chat.core.network.api.V1
import com.github.woodsmarshes.chat.core.network.dto.auth.AuthResponse
import com.github.woodsmarshes.chat.core.network.dto.auth.LoginRequest
import com.github.woodsmarshes.chat.core.network.dto.auth.RegisterRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody

class AuthApi (
    private val client: HttpClient,
) {
    suspend fun register(
        username: String,
        email: String,
        password: String,
    ): AuthResponse {
        return client.post(V1.Auth.Register()) {
            setBody(
                RegisterRequest(
                    username = username,
                    email = email,
                    password = password,
                )
            )
        }.body()
    }

    suspend fun login(
        email: String,
        password: String,
    ): AuthResponse {
        return client.post(V1.Auth.Login()) {
            setBody(
                LoginRequest(
                    email = email,
                    password = password
                )
            )
        }.body()
    }

    suspend fun refreshToken() {
        client.post(V1.Auth.Refresh()) {}
    }
}