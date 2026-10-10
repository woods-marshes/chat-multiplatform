package com.github.woodsmarshes.chat.core.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.woodsmarshes.chat.core.database.dao.ContactDaoImpl
import com.github.woodsmarshes.chat.core.database.dao.ContactRequestDaoImpl
import com.github.woodsmarshes.chat.core.database.dao.UserDaoImpl
import com.github.woodsmarshes.chat.core.database.di.createDatabase
import com.github.woodsmarshes.chat.core.model.Contact
import com.github.woodsmarshes.chat.core.model.ContactStatus
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.error.ContactError
import com.github.woodsmarshes.chat.core.network.api.rest.ContactApi
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
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
 * Verifies the session and fixed-database resource binding on
 * [ContactRepositoryImpl] across two real SQLite databases with foreign keys
 * enabled (`PRAGMA foreign_keys = ON`):
 *  - `startSession()` pins both `ContactDao` and `UserDao` to the exact same
 *    [ChatDatabase] instance and rejects mismatched database instances.
 *  - `syncFriends()` writes `UserEntity` before `ContactEntity` inside a
 *    single transaction on the session's fixed [ChatDatabase] instance,
 *    satisfying `FOREIGN KEY (contact_id) REFERENCES UserEntity(id)`.
 *  - `stopSession()` cancels in-flight operations and waits for their
 *    `finally` cleanup blocks to finish before returning, preventing premature
 *    database close.
 *  - Stale `syncFriends()` responses arriving during or after an account switch
 *    never cross-write `UserEntity` or `ContactEntity` into the new account's
 *    database via the dynamic DAOs, and subsequent session restart on the new
 *    database works cleanly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContactRepositorySessionBindingTest {

    private val ownerAId = Uuid.parse("00000000-0000-0000-0000-000000000101")
    private val ownerBId = Uuid.parse("00000000-0000-0000-0000-000000000202")
    private val friendAId = Uuid.parse("00000000-0000-0000-0000-000000000301")
    private val friendBId = Uuid.parse("00000000-0000-0000-0000-000000000302")
    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)

    private val friendUserA = User(
        id = friendAId,
        username = "friend_of_alice",
        email = "friend_a@example.com",
        displayName = "Alice Friend",
        avatarUrl = null,
        bio = null,
        createdAt = now,
        updatedAt = now,
        deletedAt = null,
    )

    private val contactA = Contact(
        userId = ownerAId,
        contactId = friendAId,
        status = ContactStatus.FRIEND,
        nickname = "AliceFriendNick",
        alias = "AliceBestie",
        createdAt = now,
        updatedAt = now,
    )

    private val friendUserB = User(
        id = friendBId,
        username = "friend_of_bob",
        email = "friend_b@example.com",
        displayName = "Bob Friend",
        avatarUrl = null,
        bio = null,
        createdAt = now,
        updatedAt = now,
        deletedAt = null,
    )

    private val contactB = Contact(
        userId = ownerBId,
        contactId = friendBId,
        status = ContactStatus.FRIEND,
        nickname = "BobFriendNick",
        alias = "BobBuddy",
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun stopSessionWaitsForInFlightSyncFriendsCleanupBeforeAllowingDatabaseClose() = runTest {
        val driverA = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
        val driverB = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
        val dbA = createDatabase { schema -> schema.create(driverA).await(); driverA }
        val dbB = createDatabase { schema -> schema.create(driverB).await(); driverB }

        val dispatcher = StandardTestDispatcher(testScheduler)
        val repoScope = CoroutineScope(SupervisorJob() + dispatcher)

        var activeDb: ChatDatabase? = dbA
        var isDbAClosed = false
        val dbProvider: () -> ChatDatabase = {
            check(!isDbAClosed || activeDb !== dbA) { "dbA was accessed after being closed" }
            activeDb ?: error("Database is not initialized! User is not logged in.")
        }

        val dynamicContactDao = ContactDaoImpl(dbProvider = dbProvider, ioContext = dispatcher)
        val dynamicUserDao = UserDaoImpl(dbProvider = dbProvider, ioContext = dispatcher)

        val syncEntered = CompletableDeferred<Unit>()
        val allowCleanupFinish = CompletableDeferred<Unit>()
        var cleanupCompleted = false

        val contactApi = mockk<ContactApi>()
        coEvery { contactApi.getContacts() } coAnswers {
            syncEntered.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) {
                    allowCleanupFinish.await()
                    cleanupCompleted = true
                }
            }
        }

        val repo = ContactRepositoryImpl(
            contactApi = contactApi,
            contactDao = dynamicContactDao,
            contactRequestDao = ContactRequestDaoImpl(dbProvider = dbProvider, ioContext = dispatcher),
            userDao = dynamicUserDao,
            scope = repoScope,
        )

        try {
            // Refused before session starts.
            assertEquals(Err(ContactError.PermissionDenied), repo.syncFriends())
            assertEquals(Err(ContactError.PermissionDenied), repo.searchContacts("friend"))
            assertEquals(Err(ContactError.PermissionDenied), repo.sendFriendRequest(friendAId, "hi"))

            // 1. Start Session A on dbA and launch syncFriends().
            repo.startSession()
            runCurrent()

            var syncCancelled: CancellationException? = null
            val syncJob = launch(dispatcher) {
                try {
                    repo.syncFriends()
                } catch (e: CancellationException) {
                    syncCancelled = e
                }
            }
            runCurrent()
            syncEntered.await()

            // 2. Initiate stopSession() + database close and switch to dbB.
            val stopAndSwitchJob = launch(dispatcher) {
                repo.stopSession()
                isDbAClosed = true
                driverA.close()
                activeDb = dbB
                repo.startSession()
            }
            runCurrent()

            // While syncFriends cleanup is still suspended: stopSession() has not returned and dbA is still open.
            assertFalse(cleanupCompleted)
            assertFalse(isDbAClosed, "dbA must not be closed before in-flight ContactRepository operation exits")
            assertEquals(dbA, activeDb)
            assertEquals(Err(ContactError.PermissionDenied), repo.syncFriends())

            // 3. Release cleanup gate: syncFriends exits, dbA closes, and Session B starts on dbB.
            allowCleanupFinish.complete(Unit)
            runCurrent()
            syncJob.join()
            stopAndSwitchJob.join()

            assertTrue(cleanupCompleted)
            assertNotNull(syncCancelled, "in-flight syncFriends must propagate CancellationException")
            assertTrue(isDbAClosed)
            assertEquals(dbB, activeDb)
            assertNull(dbB.contactsQueries.getContactById(friendAId).executeAsOneOrNull())
        } finally {
            repo.stopSession()
            repoScope.cancel()
            runCatching { driverA.close() }
            runCatching { driverB.close() }
        }
    }

    @Test
    fun lateSyncFriendsResponseCannotCrossWriteIntoNewDatabaseAndRestartCommitsAtomicallyWithForeignKeys() = runTest {
        val driverA = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
        val driverB = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
        val dbA = createDatabase { schema -> schema.create(driverA).await(); driverA }
        val dbB = createDatabase { schema -> schema.create(driverB).await(); driverB }

        val dispatcher = StandardTestDispatcher(testScheduler)
        val repoScope = CoroutineScope(SupervisorJob() + dispatcher)

        var activeDb: ChatDatabase = dbA
        val dynamicContactDao = ContactDaoImpl(dbProvider = { activeDb }, ioContext = dispatcher)
        val dynamicUserDao = UserDaoImpl(dbProvider = { activeDb }, ioContext = dispatcher)

        val sessionASyncEntered = CompletableDeferred<Unit>()
        val releaseSessionAResponse = CompletableDeferred<List<Pair<Contact, User>>>()
        var callCount = 0

        val contactApi = mockk<ContactApi>()
        coEvery { contactApi.getContacts() } coAnswers {
            val call = ++callCount
            if (call == 1) {
                sessionASyncEntered.complete(Unit)
                withContext(NonCancellable) {
                    releaseSessionAResponse.await()
                }
            } else {
                listOf(contactB to friendUserB)
            }
        }

        val repo = ContactRepositoryImpl(
            contactApi = contactApi,
            contactDao = dynamicContactDao,
            contactRequestDao = ContactRequestDaoImpl(dbProvider = { activeDb }, ioContext = dispatcher),
            userDao = dynamicUserDao,
            scope = repoScope,
        )

        try {
            // 1. Start Session A on dbA and launch syncFriends().
            repo.startSession()
            runCurrent()

            val staleSyncDeferred = async(dispatcher) {
                runCatching { repo.syncFriends() }
            }
            runCurrent()
            sessionASyncEntered.await()

            // 2. Begin stopping Session A and switch the dynamic provider to dbB
            //    while Session A's network call is about to return its late response.
            val stopJob = launch(dispatcher) {
                repo.stopSession()
            }
            runCurrent()
            activeDb = dbB

            // 3. Session A's network response arrives late with (contactA, friendUserA).
            releaseSessionAResponse.complete(listOf(contactA to friendUserA))
            runCurrent()
            stopJob.join()

            val staleOutcome = staleSyncDeferred.await()
            assertTrue(
                staleOutcome.exceptionOrNull() is CancellationException ||
                    staleOutcome.getOrNull() == Err(ContactError.PermissionDenied),
                "stale syncFriends from Session A must not succeed: $staleOutcome",
            )
            // Neither UserEntity nor ContactEntity may be written into dbA or dbB.
            assertNull(dbA.usersQueries.getUserById(friendAId).executeAsOneOrNull())
            assertNull(dbA.contactsQueries.getContactById(friendAId).executeAsOneOrNull())
            assertNull(
                dbB.usersQueries.getUserById(friendAId).executeAsOneOrNull(),
                "stale syncFriends must not cross-write UserEntity into dbB",
            )
            assertNull(
                dbB.contactsQueries.getContactById(friendAId).executeAsOneOrNull(),
                "stale syncFriends must not cross-write ContactEntity into dbB",
            )

            // 4. Restart session on dbB: syncFriends() inserts friendUserB and contactB
            //    in one transaction on dbB (with foreign_keys = ON and no pre-seeded user row).
            repo.startSession()
            runCurrent()

            assertEquals(Ok(Unit), repo.syncFriends())
            assertNotNull(dbB.usersQueries.getUserById(friendBId).executeAsOneOrNull())
            assertNotNull(dbB.contactsQueries.getContactById(friendBId).executeAsOneOrNull())
            assertNull(dbA.usersQueries.getUserById(friendBId).executeAsOneOrNull())
            assertNull(dbA.contactsQueries.getContactById(friendBId).executeAsOneOrNull())

            val searchResult = repo.searchContacts("BobBuddy")
            assertEquals(1, searchResult.component1()?.size)
            assertEquals(friendBId, searchResult.component1()?.single()?.first?.contactId)
        } finally {
            repo.stopSession()
            repoScope.cancel()
            driverA.close()
            driverB.close()
        }
    }

    @Test
    fun repeatedStartOnSameDatabaseIsIdempotentWhileDifferentOrMismatchedDatabasesAreRejected() = runTest {
        val driverA = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
        val driverB = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
        val dbA = createDatabase { schema -> schema.create(driverA).await(); driverA }
        val dbB = createDatabase { schema -> schema.create(driverB).await(); driverB }

        val dispatcher = StandardTestDispatcher(testScheduler)
        val repoScope = CoroutineScope(SupervisorJob() + dispatcher)

        var contactActiveDb: ChatDatabase = dbA
        var userActiveDb: ChatDatabase = dbA
        val dynamicContactDao = ContactDaoImpl(dbProvider = { contactActiveDb }, ioContext = dispatcher)
        val dynamicUserDao = UserDaoImpl(dbProvider = { userActiveDb }, ioContext = dispatcher)

        val syncEntered = CompletableDeferred<Unit>()
        val releaseSync = CompletableDeferred<List<Pair<Contact, User>>>()

        val contactApi = mockk<ContactApi>()
        coEvery { contactApi.getContacts() } coAnswers {
            syncEntered.complete(Unit)
            releaseSync.await()
        }

        val repo = ContactRepositoryImpl(
            contactApi = contactApi,
            contactDao = dynamicContactDao,
            contactRequestDao = ContactRequestDaoImpl(dbProvider = { contactActiveDb }, ioContext = dispatcher),
            userDao = dynamicUserDao,
            scope = repoScope,
        )

        try {
            // 1. If ContactDao and UserDao point to different ChatDatabase instances, startSession() is rejected.
            userActiveDb = dbB
            assertFailsWith<IllegalStateException>(
                message = "startSession() must reject when ContactDao and UserDao do not resolve to the same database instance",
            ) {
                repo.startSession()
            }

            // 2. Align both DAOs on dbA and start session.
            userActiveDb = dbA
            repo.startSession()
            runCurrent()

            val inFlightSync = async(dispatcher) {
                repo.syncFriends()
            }
            runCurrent()
            syncEntered.await()

            // 3. Repeated startSession() on the same dbA while syncFriends() is suspended
            //    keeps the same ActiveSession and allows syncFriends() to succeed.
            repo.startSession()
            repo.startSession()
            runCurrent()

            // 4. Attempting to startSession() on dbB without calling stopSession() first is rejected.
            contactActiveDb = dbB
            userActiveDb = dbB
            assertFailsWith<IllegalStateException>(
                message = "startSession() on dbB without stopSession() must be rejected",
            ) {
                repo.startSession()
            }

            // 5. Complete the in-flight syncFriends(): it remains pinned to dbA and writes into dbA, not dbB.
            releaseSync.complete(listOf(contactA to friendUserA))
            runCurrent()

            assertEquals(Ok(Unit), inFlightSync.await())
            assertNotNull(dbA.contactsQueries.getContactById(friendAId).executeAsOneOrNull())
            assertNull(dbB.contactsQueries.getContactById(friendAId).executeAsOneOrNull())
        } finally {
            repo.stopSession()
            repoScope.cancel()
            driverA.close()
            driverB.close()
        }
    }
}
