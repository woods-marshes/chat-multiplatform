package com.github.woodsmarshes.chat.repository

import com.github.woodsmarshes.chat.repository.database.schema.ALL_SCHEMA_TABLES
import com.github.woodsmarshes.chat.repository.database.schema.AdminAuditLogs
import com.github.woodsmarshes.chat.utils.connectToH2Database
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid

class AdminAuditLogRepositoryTest {
    private val database = connectToH2Database()
    private val repository = AdminAuditLogDataSourceImpl()
    private val adminId = Uuid.random()

    @AfterTest
    fun tearDown() {
        runBlocking {
            transaction(database) { AdminAuditLogs.deleteAll() }
        }
    }

    private fun createSchema() =
        runBlocking {
            transaction(database) { SchemaUtils.create(*ALL_SCHEMA_TABLES.toTypedArray()) }
        }

    @Test
    fun recordAndListRecentReturnsNewestFirst() =
        runBlocking {
            createSchema()
            repository.record(adminId, "ban", Uuid.random(), "banned=true")
            repository.record(adminId, "unban", Uuid.random())

            val items = repository.listRecent(10)

            assertEquals(2, items.size)
            assertEquals("unban", items[0].action)
            assertEquals("ban", items[1].action)
            assertEquals(adminId, items[0].adminId)
            assertEquals("banned=true", items[1].detail)
        }

    @Test
    fun listRecentHonoursLimit() =
        runBlocking {
            createSchema()
            repeat(5) { repository.record(adminId, "action-$it") }

            assertEquals(3, repository.listRecent(3).size)
        }

    @Test
    fun deleteOlderThanKeepsRecentRows() =
        runBlocking {
            createSchema()
            repository.record(adminId, "old")
            val oldId = repository.listRecent(10).single().id
            transaction(database) {
                AdminAuditLogs.update({ AdminAuditLogs.id eq oldId }) {
                    it[createdAt] = Clock.System.now().minus(400.days)
                }
            }
            repository.record(adminId, "new")

            val deleted = repository.deleteOlderThan(Clock.System.now().minus(365.days))

            assertEquals(1, deleted)
            val remaining = repository.listRecent(10)
            assertEquals(1, remaining.size)
            assertEquals("new", remaining.single().action)
        }
}
