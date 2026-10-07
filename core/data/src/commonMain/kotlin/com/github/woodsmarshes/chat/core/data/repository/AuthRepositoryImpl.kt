package com.github.woodsmarshes.chat.core.data.repository

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.woodsmarshes.chat.core.datastore.AuthTokenDataSource
import com.github.woodsmarshes.chat.core.datastore.UserSettingDataSource
import com.github.woodsmarshes.chat.core.model.AuthSessionSnapshot
import com.github.woodsmarshes.chat.core.model.AuthToken
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.error.AuthError
import com.github.woodsmarshes.chat.core.network.api.rest.AuthApi
import com.github.woodsmarshes.chat.core.network.dto.auth.AuthResponse
import com.github.woodsmarshes.chat.core.network.ktor.bindApi
import com.github.woodsmarshes.chat.core.network.ktor.jwtExpiryEpochMs
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AuthRepositoryImpl(
    private val authTokenDataSource: AuthTokenDataSource,
    private val userSettingDataSource: UserSettingDataSource,
    private val authApi: AuthApi,
) : AuthRepository {

    private val log = KotlinLogging.logger {}

    override val jwtToken: Flow<String?> = authTokenDataSource.jwtToken

    override fun observeAuthSession(): Flow<AuthSessionSnapshot> =
        authTokenDataSource.sessionSnapshot

    /**
     * Pure mapping of the unified persisted [AuthSessionSnapshot]: logged in
     * means both a token and a usable cached user exist in the same snapshot.
     * Database opening and per-session user initialization are owned by
     * `SessionManager`, keeping authentication and resource lifecycle separate.
     */
    override fun observeIsLoggedIn(): Flow<Boolean> =
        authTokenDataSource.sessionSnapshot
            .map { it.isLoggedIn }
            .distinctUntilChanged()

    override suspend fun login(
        email: String,
        password: String
    ): Result<User, AuthError> = coroutineBinding {
        val requestEpoch = authTokenDataSource.beginAuthRequest()
        val resp = bindApi(AuthError::Unknown) {
            authApi.login(email, password)
        }
        commitAuthResponse(requestEpoch, resp).bind()
    }

    override suspend fun register(
        username: String,
        email: String,
        password: String
    ): Result<User, AuthError> = coroutineBinding {
        val requestEpoch = authTokenDataSource.beginAuthRequest()
        val resp = bindApi(AuthError::Unknown) {
            authApi.register(username, email, password)
        }
        commitAuthResponse(requestEpoch, resp).bind()
    }

    /**
     * Commits [resp] to persisted credential and user settings only if
     * [requestEpoch] has not been invalidated by an intervening [logout] or
     * newer login/register request while the network call was in flight.
     * Does NOT open or write the per-user SQLite database here: `SessionManager`
     * opens the database and seeds the authenticated user only after any
     * previous session's workers have stopped.
     */
    private suspend fun commitAuthResponse(
        requestEpoch: Long,
        resp: AuthResponse,
    ): Result<User, AuthError> {
        val committed = authTokenDataSource.withCredentialLock {
            if (!authTokenDataSource.isAuthRequestValidLocked(requestEpoch)) {
                return@withCredentialLock false
            }
            withContext(NonCancellable) {
                val committedGen = authTokenDataSource.commitSessionLocked(
                    requestEpoch = requestEpoch,
                    user = resp.user,
                    token = AuthToken(
                        jwtToken = resp.accessToken,
                        // The rotating refresh token; drives /v1/auth/refresh and the
                        // server-side logout revocation.
                        refreshToken = resp.refreshToken,
                        // Decoded from the JWT payload; drives the client's
                        // proactive refresh margin.
                        expiryTimestamp = jwtExpiryEpochMs(resp.accessToken),
                    ),
                )
                committedGen != null
            }
        }
        if (!committed) {
            val staleRefresh = resp.refreshToken
            if (!staleRefresh.isNullOrEmpty()) {
                runCatching { authApi.logout(staleRefresh) }
                    .onFailure { log.warn(it) { "[Auth] failed to revoke superseded auth session" } }
            }
            return Err(AuthError.Unknown("Authentication request was superseded or cancelled by logout"))
        }
        return Ok(resp.user)
    }

    /**
     * Captures the current refresh token, invalidates any in-flight login or
     * refresh requests, and clears local credentials atomically before
     * performing best-effort server-side revocation of the captured refresh
     * token. Because local state is cleared and generation is advanced before
     * the network call, a new login completing while server revocation is in
     * flight is never overwritten or cleared.
     */
    override suspend fun logout() {
        val capturedRefresh = withContext(NonCancellable) {
            authTokenDataSource.withCredentialLock {
                authTokenDataSource.invalidateAndClearLocked()
            }
        }
        if (!capturedRefresh.isNullOrEmpty()) {
            runCatching { authApi.logout(capturedRefresh) }
                .onFailure { log.warn(it) { "[Auth] server-side session revocation failed" } }
        }
    }

    override suspend fun tryAutoLogin(): Result<User, AuthError> {
        val snapshot = authTokenDataSource.currentSnapshot()
        if (snapshot.jwtToken.isNullOrEmpty()) {
            return Err(AuthError.InvalidCredentials)
        }
        val cachedUser = userSettingDataSource.user.first()
        if (cachedUser != null) {
            return Ok(cachedUser)
        }
        // No cached user: refresh the token and persist the response only if
        // the captured credential generation is still current. Database
        // opening and user initialization are handled by SessionManager.
        return try {
            val resp = authApi.refreshToken()
            val updated = authTokenDataSource.commitRefreshedSessionIfCurrent(
                expectedGeneration = snapshot.generation,
                expectedRefreshToken = snapshot.refreshToken,
                user = resp.user,
                token = AuthToken(
                    jwtToken = resp.accessToken,
                    refreshToken = resp.refreshToken ?: snapshot.refreshToken,
                    expiryTimestamp = jwtExpiryEpochMs(resp.accessToken),
                ),
            )
            if (!updated) {
                return Err(AuthError.InvalidCredentials)
            }
            Ok(resp.user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Err(AuthError.Unknown(e.message))
        }
    }
}
