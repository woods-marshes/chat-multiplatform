package com.github.woodsmarshes.chat.service

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.woodsmarshes.chat.base.ServerConfig
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.github.woodsmarshes.chat.base.hashing.HashingService
import com.github.woodsmarshes.chat.base.hashing.SaltedHash
import com.github.woodsmarshes.chat.base.jwt.TokenClaim
import com.github.woodsmarshes.chat.base.jwt.TokenService
import com.github.woodsmarshes.chat.core.model.UserRole
import com.github.woodsmarshes.chat.core.model.error.AuthError
import com.github.woodsmarshes.chat.core.network.dto.auth.AuthResponse
import com.github.woodsmarshes.chat.core.network.dto.auth.LoginRequest
import com.github.woodsmarshes.chat.core.network.dto.auth.RegisterRequest
import com.github.woodsmarshes.chat.repository.UserRepository
import com.github.woodsmarshes.chat.repository.UserSettingRepository
import com.github.woodsmarshes.chat.utils.Keys
import kotlin.uuid.Uuid

class AuthService(
    private val userRepository: UserRepository,
    private val userSettingRepository: UserSettingRepository,
    private val hashingService: HashingService,
    private val tokenService: TokenService,
    private val appConfig: ServerConfig,
) {
    private val tokenConfig get() = appConfig.tokenConfig

    suspend fun register(request: RegisterRequest): Result<AuthResponse, AuthError> = coroutineBinding {
        if (userRepository.checkExists(request.email, request.username)) {
            Err(AuthError.UserAlreadyExists).bind()
        }
        val saltedHash = hashingService.generateSaltedHash(request.password)
        val user = userRepository.insertUser(
            username = request.username,
            email = request.email,
            passwordHash = saltedHash.hash,
            salt = saltedHash.salt,
            role = UserRole.MEMBER
        ) ?: Err(AuthError.InsertionFailed).bind()
        userSettingRepository.initSettings(user.id)
            ?: Err(AuthError.InsertionFailed).bind()
        val token = tokenService.generateToken(
            config = appConfig.tokenConfig,
            TokenClaim(
                name = Keys.USER_ID,
                value = user.id.toString(),
            )
        )
        AuthResponse(user, token)
    }

    suspend fun login(request: LoginRequest): Result<AuthResponse, AuthError> = coroutineBinding {
        val authInfo = userRepository.findAuthInfoByEmail(request.email)
            ?: Err(AuthError.InvalidCredentials).bind()
        val isValidPassword = hashingService.verify(
            request.password,
            SaltedHash(
                hash = authInfo.passwordHash,
                salt = authInfo.salt,
            )
        )
        if (!isValidPassword) {
            Err(AuthError.InvalidCredentials).bind()
        }
        val token = tokenService.generateToken(
            config = appConfig.tokenConfig,
            TokenClaim(
                name = Keys.USER_ID,
                value = authInfo.userId.toString(),
            )
        )
        AuthResponse(authInfo.domainUser, token)
    }

    suspend fun refreshToken(rawToken: String): Result<AuthResponse, AuthError> = coroutineBinding {
        val jwt = try {
            JWT.require(Algorithm.HMAC256(tokenConfig.secret))
                .withAudience(tokenConfig.audience)
                .withIssuer(tokenConfig.issuer)
                .acceptExpiresAt(tokenConfig.expiresIn * 2) // Allow long-expired tokens
                .build()
                .verify(rawToken)
        } catch (e: Exception) {
            Err(AuthError.InvalidCredentials).bind()
        }
        val userId = Uuid.parseOrNull(jwt.getClaim(Keys.USER_ID).asString())
            ?: Err(AuthError.InvalidCredentials).bind()
        val user = userRepository.getUserById(userId)
            ?: Err(AuthError.InvalidCredentials).bind()
        val newToken = tokenService.generateToken(
            config = tokenConfig,
            TokenClaim(name = Keys.USER_ID, value = userId.toString()),
        )
        AuthResponse(user, newToken)
    }
}