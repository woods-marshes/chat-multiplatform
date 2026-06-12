package com.github.woodsmarshes.chat.core.data.repository

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.woodsmarshes.chat.core.database.dao.ContactDao
import com.github.woodsmarshes.chat.core.model.Contact
import com.github.woodsmarshes.chat.core.model.ContactRequest
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.error.ContactError
import com.github.woodsmarshes.chat.core.network.api.rest.ContactApi
import com.github.woodsmarshes.chat.core.network.dto.contact.ContactRequestAction
import com.github.woodsmarshes.chat.core.network.ktor.bindApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.uuid.Uuid

class ContactRepositoryImpl(
    private val contactApi: ContactApi,
    private val contactDao: ContactDao,
) : ContactRepository {

    override fun getFriendsFlow(): Flow<List<Pair<Contact, User>>> {
        contactDao.getAllContactsWithUserInfo()
        return emptyFlow()
    }

    override suspend fun syncFriends(): Result<Unit, ContactError> = coroutineBinding {
        val contacts = bindApi(ContactError::Unknown) {
            contactApi.getContacts()
        }
        contacts.forEach { (contact, _) ->
            contactDao.insertContact(
                io.github.woodsmarshes.chat.db.ContactEntity(
                    contact.contactId,
                    contact.status,
                    contact.nickname,
                    contact.alias,
                    contact.createdAt,
                    contact.updatedAt,
                )
            )
        }
    }

    override suspend fun searchContacts(query: String): Result<List<Pair<Contact, User>>, ContactError> {
        TODO("Not yet implemented")
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
