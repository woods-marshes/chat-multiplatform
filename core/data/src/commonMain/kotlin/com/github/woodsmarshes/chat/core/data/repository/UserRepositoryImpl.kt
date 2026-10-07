package com.github.woodsmarshes.chat.core.data.repository

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.woodsmarshes.chat.core.common.session.SessionStoppedException
import com.github.woodsmarshes.chat.core.data.model.toUser
import com.github.woodsmarshes.chat.core.data.model.toUserEntity
import com.github.woodsmarshes.chat.core.database.dao.UserDao
import com.github.woodsmarshes.chat.core.datastore.BoundCredentialIdentity
import com.github.woodsmarshes.chat.core.datastore.UserSettingDataSource
import com.github.woodsmarshes.chat.core.model.PrivacySetting
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.UserPreference
import com.github.woodsmarshes.chat.core.model.UserSetting
import com.github.woodsmarshes.chat.core.model.error.UserError
import com.github.woodsmarshes.chat.core.network.api.rest.FileApi
import com.github.woodsmarshes.chat.core.network.api.rest.UserApi
import com.github.woodsmarshes.chat.core.network.dto.user.UpdateProfileRequest
import com.github.woodsmarshes.chat.core.network.dto.user.UpdateUserSettingsRequest
import com.github.woodsmarshes.chat.core.network.ktor.bindApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.io.Buffer
import kotlin.coroutines.ContinuationInterceptor
import kotlin.time.Clock
import kotlin.uuid.Uuid

class UserRepositoryImpl(
    private val userSettingDataSource: UserSettingDataSource,
    private val userDao: UserDao,
    private val userApi: UserApi,
    private val fileApi: FileApi,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : UserRepository {
    private data class BoundUserResources(
        val database: io.github.woodsmarshes.chat.db.ChatDatabase,
        val boundDao: UserDao,
        val credentialIdentity: BoundCredentialIdentity,
    )

    private val sessionRunner = RepositorySessionRunner(
        dispatcher = scope.coroutineContext[ContinuationInterceptor] as? CoroutineDispatcher ?: Dispatchers.Default,
        ownerName = "UserRepository",
        captureState = {
            val pinnedUserDao = userDao.bindToCurrentDatabase()
            val userDb = checkNotNull(pinnedUserDao.boundDatabase) {
                "UserDao.bindToCurrentDatabase() must return a DAO bound to a non-null ChatDatabase instance"
            }
            BoundUserResources(
                database = userDb,
                boundDao = pinnedUserDao,
                credentialIdentity = runCatching { userSettingDataSource.captureCredentialIdentity() }
                    .getOrElse { BoundCredentialIdentity() },
            )
        },
        isSameState = { existing, candidate ->
            existing.database === candidate.database &&
                existing.boundDao.boundDatabase === candidate.boundDao.boundDatabase &&
                existing.credentialIdentity == candidate.credentialIdentity
        },
        databaseOf = { it.database },
    )

    override suspend fun startSession() {
        sessionRunner.startSession()
    }

    override suspend fun stopSession() {
        sessionRunner.stopSession()
    }

    private suspend fun <T> executeSessionDbOperation(
        block: suspend (
            session: RepositorySessionRunner<BoundUserResources>.ActiveSession,
            boundDao: UserDao,
            boundIdentity: BoundCredentialIdentity,
        ) -> Result<T, UserError>,
    ): Result<T, UserError> =
        sessionRunner.execute(
            onStoppedError = { UserError.PermissionDenied },
            onUnexpectedError = { UserError.Unknown(it.message) },
        ) { session ->
            block(session, session.state.boundDao, session.state.credentialIdentity)
        }

    override fun getMeFlow(): Flow<User?> {
        return userSettingDataSource.user
    }

    override suspend fun syncMe(): Result<User, UserError> =
        executeSessionDbOperation { session, boundDao, boundIdentity ->
            coroutineBinding {
                val user = bindApi(UserError::Unknown) {
                    userApi.getMe()
                }
                session.ensureCurrent()
                if (!userSettingDataSource.setUserIfCurrent(boundIdentity, user)) {
                    throw SessionStoppedException("Credential session changed while syncMe was in flight")
                }
                boundDao.insertUser(user.toUserEntity())
                user
            }
        }

    override suspend fun updateMyProfile(
        displayName: String?,
        avatarUrl: String?,
        bio: String?
    ): Result<User, UserError> =
        executeSessionDbOperation { session, boundDao, boundIdentity ->
            coroutineBinding {
                val updatedUser = bindApi(UserError::Unknown) {
                    userApi.updateProfile(UpdateProfileRequest(displayName, avatarUrl, bio))
                }
                session.ensureCurrent()
                if (!userSettingDataSource.setUserIfCurrent(boundIdentity, updatedUser)) {
                    throw SessionStoppedException("Credential session changed while updateMyProfile was in flight")
                }
                boundDao.insertUser(updatedUser.toUserEntity())
                updatedUser
            }
        }

    override suspend fun uploadMyAvatar(
        bytes: ByteArray,
        onProgress: suspend (bytesSent: Long, total: Long?) -> Unit,
    ): Result<String, UserError> =
        executeSessionDbOperation { session, boundDao, boundIdentity ->
            coroutineBinding {
                val url = bindApi(UserError::Unknown) {
                    val buffer = Buffer().apply { write(bytes) }
                    fileApi.uploadAvatar(
                        source = buffer,
                        isGroup = false,
                        targetId = null,
                        onProgress = onProgress,
                    )
                }
                session.ensureCurrent()
                val current = userSettingDataSource.user.first()
                if (current != null && (boundIdentity.userId == null || current.id == boundIdentity.userId)) {
                    val updated = current.copy(avatarUrl = url, updatedAt = Clock.System.now())
                    if (!userSettingDataSource.setUserIfCurrent(boundIdentity, updated)) {
                        throw SessionStoppedException("Credential session changed while uploadMyAvatar was in flight")
                    }
                    boundDao.insertUser(updated.toUserEntity())
                } else {
                    syncMe().bind()
                }
                url
            }
        }

    override fun getGlobalSettingsFlow(): Flow<UserSetting?> {
        // Combine preference and privacySetting flows to create UserSetting
        return combine(
            userSettingDataSource.user,
            userSettingDataSource.preference,
            userSettingDataSource.privacySetting,
            userSettingDataSource.updatedAt
        ) { user, preference, privacy, updateAt ->
            if (user!= null && preference != null && privacy != null && updateAt != null) {
                UserSetting(
                    userId = user.id,
                    privacy = privacy,
                    preferences = preference,
                    updatedAt = updateAt
                )
            } else null
        }
    }

    override suspend fun syncGlobalSettings(): Result<UserSetting, UserError> =
        executeSessionDbOperation { session, _, boundIdentity ->
            coroutineBinding {
                val settings = bindApi(UserError::Unknown) {
                    userApi.getSettings()
                }
                session.ensureCurrent()
                if (!userSettingDataSource.updateGlobalSettingsIfCurrent(
                        expected = boundIdentity,
                        preference = settings.preferences,
                        privacy = settings.privacy,
                        updatedAt = settings.updatedAt,
                    )
                ) {
                    throw SessionStoppedException("Credential session changed while syncGlobalSettings was in flight")
                }
                settings
            }
        }

    override suspend fun updateGlobalSettings(
        privacy: PrivacySetting?,
        preferences: UserPreference?
    ): Result<Boolean, UserError> =
        executeSessionDbOperation { session, _, boundIdentity ->
            coroutineBinding {
                val success = bindApi(UserError::Unknown) {
                    userApi.updateSettings(UpdateUserSettingsRequest(privacy, preferences))
                }
                session.ensureCurrent()
                if (success) {
                    if (!userSettingDataSource.updateGlobalSettingsIfCurrent(
                            expected = boundIdentity,
                            preference = preferences,
                            privacy = privacy,
                            updatedAt = Clock.System.now(),
                        )
                    ) {
                        throw SessionStoppedException("Credential session changed while updateGlobalSettings was in flight")
                    }
                }
                success
            }
        }

    override suspend fun fetchUserDetail(userId: Uuid): Result<User, UserError> =
        executeSessionDbOperation { session, boundDao, _ ->
            coroutineBinding {
                val user = bindApi(UserError::Unknown) {
                    userApi.getUserById(userId)
                }
                session.ensureCurrent()
                boundDao.insertUser(user.toUserEntity())
                user
            }
        }

    override fun getUserFlow(userId: Uuid): Flow<User?> =
        userDao.getUserById(userId).map { it?.toUser() }

    override suspend fun searchUsers(keyword: String): Result<List<User>, UserError> =
        executeSessionDbOperation { session, boundDao, _ ->
            coroutineBinding {
                val users = bindApi(UserError::Unknown) {
                    userApi.searchUsers(keyword.trim())
                }
                session.ensureCurrent()
                boundDao.insertUsers(users.map(User::toUserEntity))
                users
            }
        }

}