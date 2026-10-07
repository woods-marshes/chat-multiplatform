package com.github.woodsmarshes.chat.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.woodsmarshes.chat.base.ServerConfig
import com.github.woodsmarshes.chat.base.hashing.HashingService
import com.github.woodsmarshes.chat.base.hashing.SaltedHash
import com.github.woodsmarshes.chat.base.jwt.TokenClaim
import com.github.woodsmarshes.chat.base.jwt.TokenService
import com.github.woodsmarshes.chat.core.model.UserRole
import com.github.woodsmarshes.chat.core.model.error.AuthError
import com.github.woodsmarshes.chat.core.network.dto.auth.AuthResponse
import com.github.woodsmarshes.chat.core.network.dto.auth.LoginRequest
import com.github.woodsmarshes.chat.core.network.dto.auth.RegisterRequest
import com.github.woodsmarshes.chat.exceptions.AppException
import com.github.woodsmarshes.chat.repository.AuthSessionRepository
import com.github.woodsmarshes.chat.repository.UserRepository
import com.github.woodsmarshes.chat.repository.UserSettingRepository
import com.github.woodsmarshes.chat.utils.inTransaction
import com.github.woodsmarshes.chat.utils.Keys
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.SQLException
import kotlinx.coroutines.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid

class AuthService(
    private val userRepository: UserRepository,
    private val userSettingRepository: UserSettingRepository,
    private val authSessionRepository: AuthSessionRepository,
    private val hashingService: HashingService,
    private val tokenService: TokenService,
    private val appConfig: ServerConfig,
) {
    private val tokenConfig get() = appConfig.tokenConfig
    private val secureRandom = SecureRandom()

    suspend fun register(request: RegisterRequest): Result<AuthResponse, AuthError> = coroutineBinding {
        if (request.password.length < MIN_PASSWORD_LENGTH) {
            Err(AuthError.WeakPassword).bind()
        }
        // Fast path for the friendly error; the unique constraint stays the
        // real guard against concurrent registrations (mapped below).
        if (userRepository.checkExists(request.email, request.username)) {
            Err(AuthError.UserAlreadyExists).bind()
        }
        // CPU-bound hashing stays outside the transaction so it never holds a
        // pooled connection for the duration of the KDF.
        val saltedHash = hashingService.generateSaltedHash(request.password)

        // One unit of work: user, settings row and refresh session commit
        // together or not at all. Repository dbQuery calls join this
        // transaction, and failures must THROW (bind does) so Exposed rolls
        // the partial account back instead of leaving a half-registered user.
        val registered = try {
            inTransaction {
                val user = userRepository.insertUser(
                    username = request.username,
                    email = request.email,
                    passwordHash = saltedHash.hash,
                    salt = saltedHash.salt,
                    role = UserRole.MEMBER
                ) ?: Err(AuthError.InsertionFailed).bind()
                userSettingRepository.initSettings(user.id)
                    ?: Err(AuthError.InsertionFailed).bind()
                val refreshToken = issueRefreshToken(user.id)
                    ?: Err(AuthError.InsertionFailed).bind()
                user to refreshToken
            }
        } catch (e: Exception) {
            // A concurrent registration with the same identity lost the race
            // to the unique index — a client error, not a 500. Everything
            // else keeps propagating; cancellation is never swallowed.
            if (e is CancellationException) throw e
            if (e.hasUniqueConstraintViolation()) Err(AuthError.UserAlreadyExists).bind() else throw e
        }

        AuthResponse(
            user = registered.first,
            accessToken = issueAccessToken(registered.first.id),
            refreshToken = registered.second,
        )
    }

    suspend fun login(request: LoginRequest): Result<AuthResponse, AuthError> = coroutineBinding {
        val authInfo = userRepository.findAuthInfoByEmail(request.email)
            ?: Err(AuthError.InvalidCredentials).bind()
        val isValidPassword = hashingService.verify(
            request.password,
            SaltedHash(
                hash = authInfo.passwordHash,
                salt = authInfo.salt,
            ),
        )
        if (!isValidPassword) {
            Err(AuthError.InvalidCredentials).bind()
        }
        AuthResponse(
            user = authInfo.domainUser,
            accessToken = issueAccessToken(authInfo.userId),
            refreshToken = issueRefreshToken(authInfo.userId)
                ?: Err(AuthError.InsertionFailed).bind(),
        )
    }

    /**
     * Exchanges a live refresh token for a fresh access JWT and a rotated
     * refresh token. The old session is revoked inside the same transaction,
     * so a replayed token stops working immediately.
     */
    suspend fun refreshSession(refreshToken: String): Result<AuthResponse, AuthError> = coroutineBinding {
        val tokenHash = sha256(refreshToken)
        val session = authSessionRepository.findActiveSession(tokenHash)
            ?: Err(AuthError.InvalidCredentials).bind()
        val user = userRepository.getUserById(session.userId)
            ?: Err(AuthError.InvalidCredentials).bind()

        val rotated = inTransaction {
            val revoked = authSessionRepository.revokeSession(tokenHash)
            val newRefreshToken = newOpaqueToken()
            val created = revoked && authSessionRepository.createSession(
                userId = session.userId,
                tokenHash = sha256(newRefreshToken),
                expiresAt = Clock.System.now() + REFRESH_TOKEN_TTL_DAYS.days,
            )
            if (!created) throw AppException(AuthError.InsertionFailed)
            newRefreshToken
        }

        AuthResponse(
            user = user,
            accessToken = issueAccessToken(session.userId),
            refreshToken = rotated,
        )
    }

    /** Revokes the session behind [refreshToken]; idempotent. */
    suspend fun logoutSession(refreshToken: String): Result<Unit, AuthError> = coroutineBinding {
        authSessionRepository.revokeSession(sha256(refreshToken))
    }

    /**
     * Mints an opaque refresh token and persists only its hash. Null when the
     * session row could not be stored — callers translate that into the
     * appropriate error inside their own result context.
     */
    private suspend fun issueRefreshToken(userId: Uuid): String? {
        val raw = newOpaqueToken()
        val stored = authSessionRepository.createSession(
            userId = userId,
            tokenHash = sha256(raw),
            expiresAt = Clock.System.now() + REFRESH_TOKEN_TTL_DAYS.days,
        )
        return raw.takeIf { stored }
    }

    private fun issueAccessToken(userId: Uuid): String = tokenService.generateToken(
        config = tokenConfig,
        TokenClaim(name = Keys.USER_ID, value = userId.toString()),
    )

    private fun newOpaqueToken(): String =
        ByteArray(REFRESH_TOKEN_BYTES).also { secureRandom.nextBytes(it) }
            .joinToString("") { "%02x".format(it) }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    /**
     * Walks the JDBC cause chain for the standard SQLState of a unique-index
     * violation — "23505" on both PostgreSQL and H2. The wrapping Exposed
     * exception itself carries no SQLState, so the chain must be searched.
     */
    private fun Throwable.hasUniqueConstraintViolation(): Boolean {
        var cause: Throwable? = this
        while (cause != null) {
            if (cause is SQLException && cause.sqlState == UNIQUE_VIOLATION_SQL_STATE) return true
            cause = cause.cause
        }
        return false
    }

    private companion object {
        const val MIN_PASSWORD_LENGTH = 8
        const val REFRESH_TOKEN_BYTES = 48
        const val REFRESH_TOKEN_TTL_DAYS = 30L
        const val UNIQUE_VIOLATION_SQL_STATE = "23505"
    }
}
