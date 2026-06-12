package com.github.woodsmarshes.chat.repository.database.schema

import com.github.woodsmarshes.chat.core.model.ContactStatus
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.time.Clock

object Contacts : Table("contacts") {
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val contactId = reference("contact_id", Users, onDelete = ReferenceOption.CASCADE)
    val status = enumerationByName("status", 50, ContactStatus::class).default(ContactStatus.FRIEND)
    val nickname = varchar("nickname", 50).nullable()
    val alias = varchar("alias", 50).nullable()
    val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Clock.System.now() }

    override val primaryKey = PrimaryKey(userId, contactId)

    init {
        index(false, userId, status)
    }
}