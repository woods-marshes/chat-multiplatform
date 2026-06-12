package com.github.woodsmarshes.chat.repository.database.schema

import com.github.woodsmarshes.chat.core.model.RequestStatus
import com.github.woodsmarshes.chat.repository.database.UuidV7Table
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.time.Clock

object GroupJoinRequests : UuidV7Table("group_join_requests") {
    val conversationId = reference("conversation_id", Conversations, onDelete = ReferenceOption.CASCADE)
    val applicantId = reference("applicant_id", Users, onDelete = ReferenceOption.CASCADE)
    val handledById = reference("handled_by_id", Users, onDelete = ReferenceOption.SET_NULL).nullable() // 哪个管理员处理的
    val status = enumerationByName("status", 20, RequestStatus::class).default(RequestStatus.PENDING)
    val message = varchar("message", 100).nullable()
    val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Clock.System.now() }

    init {
        index(false, applicantId)
        index(false, applicantId, status)
        index(false, conversationId, status)
    }
}