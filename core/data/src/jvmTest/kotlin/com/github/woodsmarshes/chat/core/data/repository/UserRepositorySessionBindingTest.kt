package com.github.woodsmarshes.chat.core.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.woodsmarshes.chat.core.database.dao.UserDaoImpl
import com.github.woodsmarshes.chat.core.database.di.createDatabase
import com.github.woodsmarshes.chat.core.datastore.AuthTokenDataSource
import com.github.woodsmarshes.chat.core.datastore.UserSettingDataSource
import com.github.woodsmarshes.chat.core.datastore.di.createPreferenceDataSources
import com.github.woodsmarshes.chat.core.model.AuthToken
import com.github.woodsmarshes.chat.core.model.DarkThemeConfig
import com.github.woodsmarshes.chat.core.model.FriendRequestPolicy
import com.github.woodsmarshes.chat.core.model.PrivacySetting
import com.github.woodsmarshes.chat.core.model.ProfileVisibility
import com.github.woodsmarshes.chat.core.model.ThemeBrand
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.UserPreference
import com.github.woodsmarshes.chat.core.model.UserSetting
import com.github.woodsmarshes.chat.core.model.error.UserError
import com.github.woodsmarshes.chat.core.network.api.rest.FileApi
import com.github.woodsmarshes.chat.core.network.api.rest.UserApi
import com.github.woodsmarshes.chat.core.network.serialization.ProjectJson
import io.github.woodsmarshes.chat.db.ChatDatabase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Verifies the session, database, and DataStore resource-binding mechanism on
 * [UserRepositoryImpl]:
 *  - Fixed [ChatDatabase] binding (`PinnedUserDao`) prevents an old session's
 *    operation from redirecting its write into a newer session's database.
 *  - `SessionExecutor` + `stopSession()` waits for in-flight operations and
 *    their `finally` cleanup blocks to finish before the caller closes the
 *    old database.
 *  - Repeated `startSession()` while already running on the same database and
 *    credential identity is idempotent and preserves the running session
 *    instance so in-flight requests still complete normally.
 *  - Calling `startSession()` while running against a different database
 *    without calling `stopSession()` first is rejected instead of silently
 *    rebinding.
 *  - Write operations that also update [UserSettingDataSource] (`syncMe`,
 *    `updateMyProfile`, `uploadMyAvatar`, `syncGlobalSettings`,
 *    `updateGlobalSettings`) conditionally verify the bound credential
 *    identity (`generation`, `userId`) inside DataStore so a stale response
 *    arriving in the window after User B's credentials are committed (before
 *    cancellation is delivered) cannot overwrite User B's cached user or
 *    settings.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UserRepositorySessionBindingTest {

    private val userAId = Uuid.parse("00000000-0000-0000-0000-0000000000a1")
    private val userBId = Uuid.parse("00000000-0000-0000-0000-0000000000b2")
    private val targetUserId = Uuid.parse("00000000-0000-0000-0000-0000000000c3")
    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)

    private val userA = User(
        id = userAId,
        username = "alice",
        email = "alice@example.com",
        displayName = "Alice",
        avatarUrl = null,
        bio = "Alice bio",
        createdAt = now,
        updatedAt = now,
        deletedAt = null,
    )

    private val userB = User(
        id = userBId,
        username = "bob",
        email = "bob@example.com",
        displayName = "Bob",
        avatarUrl = null,
        bio = "Bob bio",
        createdAt = now,
        updatedAt = now,
        deletedAt = null,
    )

    private val targetUserFromSessionA = User(
        id = targetUserId,
        username = "contact_seen_by_a",
        email = "contact_a@example.com",
        displayName = "Contact From A",
        avatarUrl = null,
        bio = "fetched in session A",
        createdAt = now,
        updatedAt = now,
        deletedAt = null,
    )

    private val targetUserFromSessionB = User(
        id = targetUserId,
        username = "contact_seen_by_b",
        email = "contact_b@example.com",
        displayName = "Contact From B",
        avatarUrl = null,
        bio = "fetched in session B",
        createdAt = now,
        updatedAt = now,
        deletedAt = null,
    )

    private suspend fun commitCredentials(
        authTokenDataSource: AuthTokenDataSource,
        user: User,
        jwt: String,
        refresh: String,
    ) {
        val epoch = authTokenDataSource.beginAuthRequest()
        authTokenDataSource.withCredentialLock {
            authTokenDataSource.commitSessionLocked(
                requestEpoch = epoch,
                user = user,
                token = AuthToken(jwtToken = jwt, refreshToken = refresh, expiryTimestamp = null),
            )
        }
    }

    @Test
    fun stopSessionWaitsForInFlightFetchUserDetailCleanupBeforeAllowingDatabaseClose() = runTest {
        val driverA = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
        val driverB = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
        val dbA = createDatabase { schema -> schema.create(driverA).await(); driverA }
        val dbB = createDatabase { schema -> schema.create(driverB).await(); driverB }

        val dispatcher = StandardTestDispatcher(testScheduler)
        val repoScope = CoroutineScope(SupervisorJob() + dispatcher)

        var activeDb: ChatDatabase? = dbA
        var isDbAClosed = false
        val dynamicUserDao = UserDaoImpl(
            dbProvider = {
                check(!isDbAClosed || activeDb !== dbA) { "dbA was accessed after being closed" }
                activeDb ?: error("Database is not initialized! User is not logged in.")
            },
            ioContext = dispatcher,
        )

        val fetchEntered = CompletableDeferred<Unit>()
        val allowCleanupFinish = CompletableDeferred<Unit>()
        var cleanupCompleted = false

        val userApi = mockk<UserApi>()
        coEvery { userApi.getUserById(targetUserId) } coAnswers {
            fetchEntered.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) {
                    allowCleanupFinish.await()
                    cleanupCompleted = true
                }
            }
        }

        val repo = UserRepositoryImpl(
            userSettingDataSource = mockk<UserSettingDataSource>(relaxed = true),
            userDao = dynamicUserDao,
            userApi = userApi,
            fileApi = mockk<FileApi>(relaxed = true),
            scope = repoScope,
        )

        try {
            // Refused before session starts.
            assertEquals(Err(UserError.PermissionDenied), repo.fetchUserDetail(targetUserId))

            // 1. Start Session A bound to dbA.
            repo.startSession()
            runCurrent()

            var fetchCancelled: CancellationException? = null
            val fetchJob = launch(dispatcher) {
                try {
                    repo.fetchUserDetail(targetUserId)
                } catch (e: CancellationException) {
                    fetchCancelled = e
                }
            }
            runCurrent()
            fetchEntered.await()

            // 2. Initiate stopSession() + database close sequence (mirroring SessionManager teardown).
            val stopAndSwitchJob = launch(dispatcher) {
                repo.stopSession()
                isDbAClosed = true
                driverA.close()
                activeDb = dbB
                repo.startSession()
            }
            runCurrent()

            // While fetchUserDetail's cleanup is still suspended at allowCleanupFinish:
            // stopSession() must NOT have returned, and dbA must NOT be closed yet.
            assertFalse(cleanupCompleted)
            assertFalse(isDbAClosed, "database A must not be closed before in-flight operation cleanup finishes")
            assertEquals(dbA, activeDb, "must not switch active database to dbB while old operation is still stopping")
            assertEquals(
                Err(UserError.PermissionDenied),
                repo.fetchUserDetail(userAId),
                "new operations must be refused while session is stopping",
            )

            // 3. Release the cleanup gate: stopSession() finishes, dbA closes, and Session B starts on dbB.
            allowCleanupFinish.complete(Unit)
            runCurrent()
            fetchJob.join()
            stopAndSwitchJob.join()

            assertTrue(cleanupCompleted)
            assertNotNull(fetchCancelled, "cancelled in-flight fetchUserDetail must propagate CancellationException")
            assertTrue(isDbAClosed, "database A is closed only after in-flight operation exited")
            assertEquals(dbB, activeDb)
            assertNull(dbB.usersQueries.getUserById(targetUserId).executeAsOneOrNull())
        } finally {
            repo.stopSession()
            repoScope.cancel()
            runCatching { driverA.close() }
            runCatching { driverB.close() }
        }
    }

    @Test
    fun lateFetchUserDetailResponseCannotCrossWriteIntoNewDatabaseViaDynamicDao() = runTest {
        val driverA = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
        val driverB = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
        val dbA = createDatabase { schema -> schema.create(driverA).await(); driverA }
        val dbB = createDatabase { schema -> schema.create(driverB).await(); driverB }

        val dispatcher = StandardTestDispatcher(testScheduler)
        val repoScope = CoroutineScope(SupervisorJob() + dispatcher)

        var activeDb: ChatDatabase = dbA
        val dynamicUserDao = UserDaoImpl(
            dbProvider = { activeDb },
            ioContext = dispatcher,
        )

        val sessionAFetchEntered = CompletableDeferred<Unit>()
        val releaseSessionAResponse = CompletableDeferred<User>()
        var apiCallCount = 0

        val userApi = mockk<UserApi>()
        coEvery { userApi.getUserById(targetUserId) } coAnswers {
            val call = ++apiCallCount
            if (call == 1) {
                sessionAFetchEntered.complete(Unit)
                // Simulate a non-cancellable / late-completing transport call that
                // returns a response instead of throwing CancellationException even
                // after the active database has switched to dbB.
                withContext(NonCancellable) {
                    releaseSessionAResponse.await()
                }
            } else {
                targetUserFromSessionB
            }
        }

        val repo = UserRepositoryImpl(
            userSettingDataSource = mockk<UserSettingDataSource>(relaxed = true),
            userDao = dynamicUserDao,
            userApi = userApi,
            fileApi = mockk<FileApi>(relaxed = true),
            scope = repoScope,
        )

        try {
            // 1. Start Session A on dbA and launch fetchUserDetail(targetUserId).
            repo.startSession()
            runCurrent()

            val staleFetchDeferred = async(dispatcher) {
                runCatching { repo.fetchUserDetail(targetUserId) }
            }
            runCurrent()
            sessionAFetchEntered.await()

            // 2. Begin stopping Session A and switch the underlying dynamic dbProvider to dbB
            //    while Session A's network call is about to return its late response.
            val stopJob = launch(dispatcher) {
                repo.stopSession()
            }
            runCurrent()
            activeDb = dbB

            // 3. Session A's network call now returns targetUserFromSessionA.
            //    Because the operation pinned dbA and Session A at operation start,
            //    it must NOT redirect its write to dbB via dynamicUserDao.
            releaseSessionAResponse.complete(targetUserFromSessionA)
            runCurrent()
            stopJob.join()

            val staleOutcome = staleFetchDeferred.await()
            assertTrue(
                staleOutcome.exceptionOrNull() is CancellationException ||
                    staleOutcome.getOrNull() == Err(UserError.PermissionDenied),
                "stale operation from Session A must not succeed: $staleOutcome",
            )
            assertNull(
                dbA.usersQueries.getUserById(targetUserId).executeAsOneOrNull(),
                "stale response must not be written into dbA after teardown began",
            )
            assertNull(
                dbB.usersQueries.getUserById(targetUserId).executeAsOneOrNull(),
                "stale response from Session A must NEVER cross-write into User B's dbB via dynamic UserDao",
            )

            // 4. Start Session B on dbB: a fresh fetchUserDetail binds to dbB and persists targetUserFromSessionB.
            repo.startSession()
            runCurrent()

            val sessionBResult = repo.fetchUserDetail(targetUserId)
            assertEquals(Ok(targetUserFromSessionB), sessionBResult)
            assertEquals(
                "contact_seen_by_b",
                dbB.usersQueries.getUserById(targetUserId).executeAsOneOrNull()?.username,
                "fresh operation in Session B must write into dbB",
            )
            assertNull(
                dbA.usersQueries.getUserById(targetUserId).executeAsOneOrNull(),
                "Session B operation must not write into dbA",
            )
        } finally {
            repo.stopSession()
            repoScope.cancel()
            driverA.close()
            driverB.close()
        }
    }

    @Test
    fun repeatedStartSessionOnSameDatabaseWhileRequestInFlightKeepsRunningInstanceAndRequestSucceeds() = runTest {
        val driverA = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
        val dbA = createDatabase { schema -> schema.create(driverA).await(); driverA }

        val dispatcher = StandardTestDispatcher(testScheduler)
        val repoScope = CoroutineScope(SupervisorJob() + dispatcher)
        val storeScope = CoroutineScope(SupervisorJob() + dispatcher)
        val tempDir = Files.createTempDirectory("user-repo-idempotent-start-")
        val (authTokenDataSource, userSettingDataSource) = createPreferenceDataSources(
            filePath = tempDir.resolve("test.preferences_pb").toFile().absolutePath,
            scope = storeScope,
            json = ProjectJson,
        )

        val dynamicUserDao = UserDaoImpl(
            dbProvider = { dbA },
            ioContext = dispatcher,
        )

        val fetchEntered = CompletableDeferred<Unit>()
        val releaseFetch = CompletableDeferred<User>()

        val userApi = mockk<UserApi>()
        coEvery { userApi.getUserById(targetUserId) } coAnswers {
            fetchEntered.complete(Unit)
            releaseFetch.await()
        }

        val repo = UserRepositoryImpl(
            userSettingDataSource = userSettingDataSource,
            userDao = dynamicUserDao,
            userApi = userApi,
            fileApi = mockk<FileApi>(relaxed = true),
            scope = repoScope,
        )

        try {
            commitCredentials(authTokenDataSource, userA, "jwt-alice", "refresh-alice")
            repo.startSession()
            runCurrent()

            val inFlightFetch = async(dispatcher) {
                repo.fetchUserDetail(targetUserId)
            }
            runCurrent()
            fetchEntered.await()

            // Repeated startSession() on the same database and same credential identity
            // while fetchUserDetail is suspended in flight must NOT replace the Running
            // session instance or invalidate the in-flight operation.
            repo.startSession()
            repo.startSession()
            runCurrent()

            releaseFetch.complete(targetUserFromSessionA)
            runCurrent()

            assertEquals(Ok(targetUserFromSessionA), inFlightFetch.await())
            assertEquals(
                "contact_seen_by_a",
                dbA.usersQueries.getUserById(targetUserId).executeAsOneOrNull()?.username,
            )
        } finally {
            repo.stopSession()
            repoScope.cancel()
            storeScope.cancel()
            driverA.close()
            runCatching { tempDir.toFile().deleteRecursively() }
        }
    }

    @Test
    fun startSessionForDifferentDatabaseWithoutStopIsRejectedAndDoesNotSilentlyRebind() = runTest {
        val driverA = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
        val driverB = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
        val dbA = createDatabase { schema -> schema.create(driverA).await(); driverA }
        val dbB = createDatabase { schema -> schema.create(driverB).await(); driverB }

        val dispatcher = StandardTestDispatcher(testScheduler)
        val repoScope = CoroutineScope(SupervisorJob() + dispatcher)

        var activeDb: ChatDatabase = dbA
        val dynamicUserDao = UserDaoImpl(
            dbProvider = { activeDb },
            ioContext = dispatcher,
        )

        val userApi = mockk<UserApi>()
        coEvery { userApi.getUserById(targetUserId) } returns targetUserFromSessionA

        val repo = UserRepositoryImpl(
            userSettingDataSource = mockk<UserSettingDataSource>(relaxed = true),
            userDao = dynamicUserDao,
            userApi = userApi,
            fileApi = mockk<FileApi>(relaxed = true),
            scope = repoScope,
        )

        try {
            repo.startSession()
            runCurrent()

            // Switch dynamic provider to dbB WITHOUT calling repo.stopSession() first.
            activeDb = dbB
            assertFailsWith<IllegalStateException>(
                message = "startSession() on a different database without stopSession() must be rejected",
            ) {
                repo.startSession()
            }

            // The running session remains pinned to dbA (not silently rebound to dbB).
            assertEquals(Ok(targetUserFromSessionA), repo.fetchUserDetail(targetUserId))
            assertEquals(
                "contact_seen_by_a",
                dbA.usersQueries.getUserById(targetUserId).executeAsOneOrNull()?.username,
                "existing session must remain pinned to dbA",
            )
            assertNull(
                dbB.usersQueries.getUserById(targetUserId).executeAsOneOrNull(),
                "must not have silently rebound to dbB",
            )
        } finally {
            repo.stopSession()
            repoScope.cancel()
            driverA.close()
            driverB.close()
        }
    }

    @Test
    fun inFlightProfileSettingsAndAvatarWritesCannotOverwriteNewCredentialsInDataStoreBeforeCancelArrives() = runTest {
        val driverA = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
        val driverB = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
        val dbA = createDatabase { schema -> schema.create(driverA).await(); driverA }
        val dbB = createDatabase { schema -> schema.create(driverB).await(); driverB }

        val dispatcher = StandardTestDispatcher(testScheduler)
        val repoScope = CoroutineScope(SupervisorJob() + dispatcher)
        val storeScope = CoroutineScope(SupervisorJob() + dispatcher)
        val tempDir = Files.createTempDirectory("user-repo-datastore-guard-")
        val (authTokenDataSource, userSettingDataSource) = createPreferenceDataSources(
            filePath = tempDir.resolve("test.preferences_pb").toFile().absolutePath,
            scope = storeScope,
            json = ProjectJson,
        )

        var activeDb: ChatDatabase = dbA
        val dynamicUserDao = UserDaoImpl(
            dbProvider = { activeDb },
            ioContext = dispatcher,
        )

        val updateProfileEntered = CompletableDeferred<Unit>()
        val releaseUpdateProfile = CompletableDeferred<User>()
        val syncSettingsEntered = CompletableDeferred<Unit>()
        val releaseSyncSettings = CompletableDeferred<UserSetting>()

        val aliceUpdatedUser = userA.copy(displayName = "Alice Updated Late", bio = "Stale Alice Bio")
        val aliceSettings = UserSetting(
            userId = userAId,
            privacy = PrivacySetting(
                allowSearch = false,
                friendRequestPolicy = FriendRequestPolicy.DENY_ANY,
                showOnlineStatus = false,
                profileVisibility = ProfileVisibility.PRIVATE,
                allowStrangerChat = false,
            ),
            preferences = UserPreference(themeBrand = ThemeBrand.DEFAULT, darkThemeConfig = DarkThemeConfig.DARK),
            updatedAt = Instant.fromEpochSeconds(1_700_000_100),
        )
        val bobPreference = UserPreference(themeBrand = ThemeBrand.MIUIX, darkThemeConfig = DarkThemeConfig.LIGHT)
        val bobPrivacy = PrivacySetting(
            allowSearch = true,
            friendRequestPolicy = FriendRequestPolicy.AUTO_ACCEPT,
            showOnlineStatus = true,
            profileVisibility = ProfileVisibility.PUBLIC,
            allowStrangerChat = true,
        )
        val bobUpdatedAt = Instant.fromEpochSeconds(1_700_000_200)

        val userApi = mockk<UserApi>()
        coEvery { userApi.updateProfile(any()) } coAnswers {
            updateProfileEntered.complete(Unit)
            releaseUpdateProfile.await()
        }
        coEvery { userApi.getSettings() } coAnswers {
            syncSettingsEntered.complete(Unit)
            releaseSyncSettings.await()
        }
        coEvery { userApi.getMe() } returns userB.copy(avatarUrl = "https://example.com/bob-avatar.png")
        coEvery { userApi.searchUsers("bob") } returns listOf(userB)

        val fileApi = mockk<FileApi>()
        coEvery { fileApi.uploadAvatar(any(), any(), any(), any()) } returns "https://example.com/bob-avatar.png"

        val repo = UserRepositoryImpl(
            userSettingDataSource = userSettingDataSource,
            userDao = dynamicUserDao,
            userApi = userApi,
            fileApi = fileApi,
            scope = repoScope,
        )

        try {
            // 1. User A logs in (credential generation 1) and starts UserRepository session on dbA.
            commitCredentials(authTokenDataSource, userA, "jwt-alice", "refresh-alice")
            repo.startSession()
            runCurrent()

            // 2. User A starts updateMyProfile and syncGlobalSettings; both suspend in flight.
            val staleProfileDeferred = async(dispatcher) {
                repo.updateMyProfile(displayName = "Alice Updated Late", avatarUrl = null, bio = "Stale Alice Bio")
            }
            val staleSettingsDeferred = async(dispatcher) {
                repo.syncGlobalSettings()
            }
            runCurrent()
            updateProfileEntered.await()
            syncSettingsEntered.await()

            // 3. Simulate the exact race window where User B's login commits credentials (generation 2)
            //    and settings into DataStore BEFORE SessionManager has delivered stopSession() to UserRepository!
            commitCredentials(authTokenDataSource, userB, "jwt-bob", "refresh-bob")
            userSettingDataSource.setPreference(bobPreference)
            userSettingDataSource.setPrivacySetting(bobPrivacy)
            userSettingDataSource.setUpdatedAt(bobUpdatedAt)
            runCurrent()

            // 4. User A's in-flight responses arrive BEFORE stopSession() is called.
            releaseUpdateProfile.complete(aliceUpdatedUser)
            releaseSyncSettings.complete(aliceSettings)
            runCurrent()

            // Both must be rejected by UserSettingDataSource's conditional credential identity check!
            assertEquals(Err(UserError.PermissionDenied), staleProfileDeferred.await())
            assertEquals(Err(UserError.PermissionDenied), staleSettingsDeferred.await())

            // User B's user cache and settings in DataStore must remain intact, and neither DB was written.
            assertEquals(userB, userSettingDataSource.user.first(), "User A's late profile response must not overwrite User B in DataStore")
            assertEquals(bobPreference, userSettingDataSource.preference.first(), "User A's late settings response must not overwrite User B's preference")
            assertEquals(bobPrivacy, userSettingDataSource.privacySetting.first(), "User A's late settings response must not overwrite User B's privacy")
            assertEquals(bobUpdatedAt, userSettingDataSource.updatedAt.first(), "User A's late settings response must not overwrite User B's updatedAt")
            assertNull(dbA.usersQueries.getUserById(userAId).executeAsOneOrNull())
            assertNull(dbB.usersQueries.getUserById(userAId).executeAsOneOrNull())

            // 5. Now stop Session A, switch activeDb to dbB, and start Session B for User B.
            repo.stopSession()
            activeDb = dbB
            repo.startSession()
            runCurrent()

            // Verify uploadMyAvatar (and nested syncMe) + searchUsers succeed in Session B and write to dbB + DataStore.
            val avatarResult = repo.uploadMyAvatar(byteArrayOf(1, 2, 3))
            assertEquals(Ok("https://example.com/bob-avatar.png"), avatarResult)
            assertEquals("https://example.com/bob-avatar.png", userSettingDataSource.user.first()?.avatarUrl)
            assertEquals("https://example.com/bob-avatar.png", dbB.usersQueries.getUserById(userBId).executeAsOneOrNull()?.avatar)

            val searchResult = repo.searchUsers(" bob ")
            assertEquals(Ok(listOf(userB)), searchResult)
            assertNotNull(dbB.usersQueries.getUserById(userBId).executeAsOneOrNull())
            assertNull(dbA.usersQueries.getUserById(userBId).executeAsOneOrNull())
        } finally {
            repo.stopSession()
            repoScope.cancel()
            storeScope.cancel()
            driverA.close()
            driverB.close()
            runCatching { tempDir.toFile().deleteRecursively() }
        }
    }
}
