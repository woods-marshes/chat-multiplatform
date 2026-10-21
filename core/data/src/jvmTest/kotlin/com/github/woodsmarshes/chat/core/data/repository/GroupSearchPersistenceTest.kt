package com.github.woodsmarshes.chat.core.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.github.michaelbull.result.Ok
import com.github.woodsmarshes.chat.core.data.model.toEntity
import com.github.woodsmarshes.chat.core.database.dao.GroupProfileDaoImpl
import com.github.woodsmarshes.chat.core.database.di.DatabaseHolder
import com.github.woodsmarshes.chat.core.database.di.createDatabase
import com.github.woodsmarshes.chat.core.database.session.ManagedDatabaseSessionGate
import com.github.woodsmarshes.chat.core.datastore.UserSettingDataSource
import com.github.woodsmarshes.chat.core.model.GroupProfile
import com.github.woodsmarshes.chat.core.model.GroupSettings
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.network.api.rest.ConversationApi
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlin.uuid.Uuid

class GroupSearchPersistenceTest {
    @Test
    fun unknownGroupSearchDoesNotRequireParentRowsOrMutateMembershipCache() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
        val db = createDatabase { schema -> schema.create(driver).await(); driver }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gate = ManagedDatabaseSessionGate(dispatcher, { db }, { 1L })
        gate.start()
        try {
            val holder = mockk<DatabaseHolder>()
            every { holder.sessionGate } returns gate
            val settings = mockk<UserSettingDataSource>()
            every { settings.user } returns MutableStateFlow<User?>(null)
            val api = mockk<ConversationApi>()
            val profile = GroupProfile(
                conversationId = Uuid.parse("00000000-0000-0000-0000-000000000111"),
                name = "Public group",
                handle = "dingdong",
                description = "Not a local membership",
                avatarUrl = null,
                ownerId = Uuid.parse("00000000-0000-0000-0000-000000000222"),
                settings = GroupSettings(),
                createdAt = Instant.fromEpochMilliseconds(1),
                updatedAt = Instant.fromEpochMilliseconds(1),
            )
            val dao = GroupProfileDaoImpl({ db }, dispatcher)
            // Reproduce the old failure against real SQLite with both parents absent.
            assertFails { dao.insertGroupProfiles(listOf(profile.toEntity())) }
            coEvery { api.searchGroups("dingdong") } returns listOf(profile)
            val repository = ConversationRepositoryImpl(
                groupProfileDao = dao,
                conversationDao = mockk(),
                participantDao = mockk(),
                messageDao = mockk(),
                groupJoinRequestDao = mockk(),
                userDao = mockk(),
                conversationApi = api,
                userApi = mockk(),
                fileApi = mockk(),
                databaseHolder = holder,
                userSettingDataSource = settings,
                unreadRefreshCoordinator = mockk(),
            )
            repeat(2) {
                assertEquals(Ok(listOf(profile)), repository.searchGroups(" dingdong "))
                assertNull(db.groupProfilesQueries.getGroupProfile(profile.conversationId).executeAsOneOrNull())
            }
        } finally {
            gate.stop()
            driver.close()
        }
    }
}
