package com.github.woodsmarshes.chat.core.data.repository

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.woodsmarshes.chat.core.data.model.toEntity
import com.github.woodsmarshes.chat.core.data.model.toFriend
import com.github.woodsmarshes.chat.core.data.model.toUserEntity
import com.github.woodsmarshes.chat.core.database.dao.ContactDao
import com.github.woodsmarshes.chat.core.database.dao.UserDao
import com.github.woodsmarshes.chat.core.model.Contact
import com.github.woodsmarshes.chat.core.model.ContactRequest
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.error.ContactError
import com.github.woodsmarshes.chat.core.network.api.rest.ContactApi
import com.github.woodsmarshes.chat.core.network.dto.contact.ContactRequestAction
import com.github.woodsmarshes.chat.core.network.ktor.bindApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.uuid.Uuid

class ContactRepositoryImpl(
    private val contactApi: ContactApi,
    private val contactDao: ContactDao,
    private val userDao: UserDao,
) : ContactRepository {

    override fun getFriendsFlow(): Flow<List<Pair<Contact, User>>> =
        contactDao.getAllContactsWithUserInfo().map { rows ->
            rows.map { it.toFriend() }
        }

    override suspend fun syncFriends(): Result<Unit, ContactError> = coroutineBinding {
        val contacts = bindApi(ContactError::Unknown) {
            contactApi.getContacts()
        }
        contacts.forEach { (contact, friend) ->
            contactDao.insertContact(contact.toEntity())
            // The friends list query joins UserEntity; without caching the
            // friend's user row the join silently drops the contact.
            friend.toUserEntity()?.let { userDao.insertUser(it) }
        }
    }

    override suspend fun searchContacts(query: String): Result<List<Pair<Contact, User>>, ContactError> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return com.github.michaelbull.result.Ok(emptyList())
        }
        val rows = contactDao.searchContacts(trimmed).first()
        return com.github.michaelbull.result.Ok(rows.map { it.toFriend() })
    }

    override suspend fun sendFriendRequest(targetId: Uuid, message: String?): Result<Boolean, ContactError> =
        coroutineBinding {
            bindApi(ContactError::Unknown) {
                contactApi.addContact(targetId, message)
            }
        }

    override fun observeIncomingRequests(): Flow<List<ContactRequest>> = emptyFlow()

    override suspend fun syncContactRequests(): Result<List<ContactRequest>, ContactError> = coroutineBinding {
        bindApi(ContactError::Unknown) {
            contactApi.getContactRequests()
        }
    }

    override suspend fun handleFriendRequest(
        requestId: Uuid,
        accept: Boolean,
        remark: String?
    ): Result<Boolean, ContactError> = coroutineBinding {
        val action = if (accept) ContactRequestAction.APPROVE else ContactRequestAction.REJECT
        bindApi(ContactError::Unknown) {
            contactApi.handleRequest(requestId, action, remark)
        }
    }

    override suspend fun blockUser(userId: Uuid): Result<Boolean, ContactError> = coroutineBinding {
        bindApi(ContactError::Unknown) {
            contactApi.blockUser(userId)
        }
    }

    override suspend fun unblockUser(userId: Uuid): Result<Boolean, ContactError> = coroutineBinding {
        bindApi(ContactError::Unknown) {
            contactApi.unblockUser(userId)
        }
    }

    override suspend fun removeFriend(userId: Uuid): Result<Boolean, ContactError> = coroutineBinding {
        bindApi(ContactError::Unknown) {
            contactApi.deleteContact(userId)
        }
    }
}
