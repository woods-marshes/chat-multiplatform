package com.github.woodsmarshes.chat.core.data.model

import com.github.woodsmarshes.chat.core.model.Contact
import io.github.woodsmarshes.chat.db.ContactEntity
import io.github.woodsmarshes.chat.db.GetAllContactsWithUserInfo
import kotlin.uuid.Uuid

fun Contact.toEntity() = ContactEntity(
    contact_id = this.contactId,
    status = this.status,
    nickname = this.nickname,
    alias = this.alias,
    created_at = this.createdAt,
    updated_at = this.updatedAt,
)

fun ContactEntity.toContact(userId: Uuid) = Contact(
    userId = userId,
    contactId = this.contact_id,
    status = this.status,
    nickname = this.nickname,
    alias = this.alias,
    createdAt = this.created_at,
    updatedAt = this.updated_at,
)

fun GetAllContactsWithUserInfo.toContact(userId: Uuid) = Contact(
    userId = userId,
    contactId = this.contact_id,
    status = this.status,
    nickname = this.nickname,
    alias = this.alias,
    createdAt = this.created_at,
    updatedAt = this.updated_at,
)