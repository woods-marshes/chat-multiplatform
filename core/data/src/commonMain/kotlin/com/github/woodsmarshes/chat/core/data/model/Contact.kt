package com.github.woodsmarshes.chat.core.data.model

import com.github.woodsmarshes.chat.core.model.Contact
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.UserRole
import io.github.woodsmarshes.chat.db.ContactEntity
import io.github.woodsmarshes.chat.db.GetAllContactsWithUserInfo
import io.github.woodsmarshes.chat.db.SearchContacts
import kotlin.time.Instant
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
/**
 * Cached friend row -> (contact, user). The contact row's owner is the local
 * user; the client schema does not track it per row, so NIL is used as a
 * placeholder (Contact.userId is not consumed by any UI layer today).
 */
fun GetAllContactsWithUserInfo.toFriend(): Pair<Contact, User> = Pair(
    Contact(
        userId = Uuid.NIL,
        contactId = this.contact_id,
        status = this.status,
        nickname = this.nickname,
        alias = this.alias,
        createdAt = this.created_at,
        updatedAt = this.updated_at,
    ),
    User(
        id = this.u_id,
        username = this.u_username,
        email = this.u_email,
        displayName = this.u_display_name,
        avatarUrl = this.u_avatar,
        bio = this.u_bio,
        createdAt = this.u_created_at,
        updatedAt = this.u_updated_at,
        deletedAt = this.u_deleted_at,
        role = this.u_role,
    ),
)

fun SearchContacts.toFriend(): Pair<Contact, User> = Pair(
    Contact(
        userId = Uuid.NIL,
        contactId = this.contact_id,
        status = this.status,
        nickname = this.nickname,
        alias = this.alias,
        createdAt = this.created_at,
        updatedAt = this.updated_at,
    ),
    User(
        id = this.u_id,
        username = this.u_username,
        email = this.u_email,
        displayName = this.u_display_name,
        avatarUrl = this.u_avatar,
        bio = this.u_bio,
        createdAt = this.u_created_at,
        updatedAt = this.u_updated_at,
        deletedAt = this.u_deleted_at,
        role = this.u_role,
    ),
)
