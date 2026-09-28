package com.github.woodsmarshes.chat.repository

import com.github.woodsmarshes.chat.core.model.ContactStatus
import com.github.woodsmarshes.chat.core.model.UserRole
import com.github.woodsmarshes.chat.repository.database.schema.Contacts
import com.github.woodsmarshes.chat.repository.database.schema.Users
import com.github.woodsmarshes.chat.utils.connectToH2Database
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ContactRepositoryTest {

    private val database = connectToH2Database()
    private val repository = ContactSourceImpl()
    private val userA = Uuid.random()
    private val userB = Uuid.random()

    private val fixedCreatedAt = Instant.fromEpochMilliseconds(1_577_836_800_000) // 2020-01-01

    @AfterTest
    fun tearDown() {
        runBlocking {
            transaction(database) {
                Contacts.deleteWhere { Contacts.userId eq userA }
                Contacts.deleteWhere { Contacts.userId eq userB }
                Users.deleteWhere { Users.id inList listOf(userA, userB) }
            }
        }
    }

    private fun seed() = runBlocking {
        val now = Clock.System.now()
        transaction(database) {
            SchemaUtils.create(Users, Contacts)
            listOf(userA, userB).forEach { id ->
                Users.insert {
                    it[this.id] = id
                    it[username] = "user-$id"
                    it[email] = "user-$id@test.local"
                    it[passwordHash] = "hash"
                    it[salt] = ""
                    it[role] = UserRole.MEMBER
                    it[createdAt] = now
                    it[updatedAt] = now
                }
            }
        }
    }

    @Test
    fun upsertContactDoesNotResetCreatedAtOnUpdate() = runBlocking {
        seed()

        assertTrue(repository.upsertContact(userA, userB, status = ContactStatus.FRIEND))

        // Pin created_at to a fixed past value so a reset cannot pass by
        // coincidence of two timestamps landing in the same instant.
        transaction(database) {
            Contacts.update({ (Contacts.userId eq userA) and (Contacts.contactId eq userB) }) {
                it[createdAt] = fixedCreatedAt
            }
        }

        assertTrue(repository.upsertContact(userA, userB, nickname = "updated", status = ContactStatus.FRIEND))

        transaction(database) {
            val row = Contacts.selectAll()
                .where { (Contacts.userId eq userA) and (Contacts.contactId eq userB) }
                .single()
            assertEquals(fixedCreatedAt, row[Contacts.createdAt])
            assertEquals("updated", row[Contacts.nickname])
            assertEquals(ContactStatus.FRIEND, row[Contacts.status])
        }
    }
}
