package com.github.woodsmarshes.chat.repository.database.schema

import com.github.woodsmarshes.chat.core.model.RequestStatus
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.time.Clock

object ContactRequests : UuidTable(name = "contact_requests", uuidVersion = UuidVersion.V7) {
    val senderId = reference("sender_id", Users, onDelete = ReferenceOption.CASCADE)
    val receiverId = reference("receiver_id", Users, onDelete = ReferenceOption.CASCADE)

    val message = varchar("message", 100).nullable()
    val status = enumerationByName("status", 20, RequestStatus::class).default(RequestStatus.PENDING)

    val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Clock.System.now() }

    init {
        index(false, receiverId, status)
        index(false, senderId, status)
    }
}