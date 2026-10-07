package com.github.woodsmarshes.chat.core.data.repository

import com.github.michaelbull.result.Ok
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
import io.github.woodsmarshes.chat.db.ChatDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.coroutines.ContinuationInterceptor
import kotlin.uuid.Uuid

class ContactRepositoryImpl(
    private val contactApi: ContactApi,
    private val contactDao: ContactDao,
    private val userDao: UserDao,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : ContactRepository {

    private data class BoundContactResources(
        val database: ChatDatabase,
        val contactDao: ContactDao,
        val userDao: UserDao,
    )

    private val sessionRunner = RepositorySessionRunner(
        dispatcher = scope.coroutineContext[ContinuationInterceptor] as? CoroutineDispatcher ?: Dispatchers.Default,
        ownerName = "ContactRepository",
        captureState = {
            val pinnedContactDao = contactDao.bindToCurrentDatabase()
            val pinnedUserDao = userDao.bindToCurrentDatabase()
            val contactDb = checkNotNull(pinnedContactDao.boundDatabase) {
                "ContactDao.bindToCurrentDatabase() must return a DAO bound to a non-null ChatDatabase instance"
            }
            val userDb = checkNotNull(pinnedUserDao.boundDatabase) {
                "UserDao.bindToCurrentDatabase() must return a DAO bound to a non-null ChatDatabase instance"
            }
            check(contactDb === userDb) {
                "ContactDao and UserDao must resolve to the same session database instance"
            }
            BoundContactResources(
                database = contactDb,
                contactDao = pinnedContactDao,
                userDao = pinnedUserDao,
            )
        },
        isSameState = { existing, candidate ->
            existing.database === candidate.database &&
                existing.contactDao.boundDatabase === candidate.contactDao.boundDatabase &&
                existing.userDao.boundDatabase === candidate.userDao.boundDatabase
        },
        databaseOf = { it.database },
    )

    override suspend fun startSession() {
        sessionRunner.startSession()
    }

    override suspend fun stopSession() {
        sessionRunner.stopSession()
    }

    private suspend fun <T> executeSessionOperation(
        block: suspend (
            session: RepositorySessionRunner<BoundContactResources>.ActiveSession,
            resources: BoundContactResources,
        ) -> Result<T, ContactError>,
    ): Result<T, ContactError> =
        sessionRunner.execute(
            onStoppedError = { ContactError.PermissionDenied },
            onUnexpectedError = { ContactError.Unknown(it.message) },
        ) { session ->
            block(session, session.state)
        }

    override fun getFriendsFlow(): Flow<List<Pair<Contact, User>>> =
        contactDao.getAllContactsWithUserInfo().map { rows ->
            rows.map { it.toFriend() }
        }

    override suspend fun syncFriends(): Result<Unit, ContactError> =
        executeSessionOperation { session, resources ->
            coroutineBinding {
                val contacts = bindApi(ContactError::Unknown) {
                    contactApi.getContacts()
                }
                session.ensureCurrent()
                resources.contactDao.transaction {
                    contacts.forEach { (contact, friend) ->
                        // The friends list query joins UserEntity, and ContactEntity
                        // declares FOREIGN KEY (contact_id) REFERENCES UserEntity(id):
                        // insert UserEntity before ContactEntity within the same
                        // fixed-database transaction.
                        resources.userDao.insertUser(friend.toUserEntity())
                        resources.contactDao.insertContact(contact.toEntity())
                    }
                }
            }
        }

    override suspend fun searchContacts(query: String): Result<List<Pair<Contact, User>>, ContactError> =
        executeSessionOperation { session, resources ->
            val trimmed = query.trim()
            if (trimmed.isEmpty()) {
                return@executeSessionOperation Ok(emptyList())
            }
            session.ensureCurrent()
            val rows = resources.contactDao.searchContacts(trimmed).first()
            session.ensureCurrent()
            Ok(rows.map { it.toFriend() })
        }

    override suspend fun sendFriendRequest(targetId: Uuid, message: String?): Result<Boolean, ContactError> =
        executeSessionOperation { session, _ ->
            coroutineBinding {
                val result = bindApi(ContactError::Unknown) {
                    contactApi.addContact(targetId, message)
                }
                session.ensureCurrent()
                result
            }
        }

    override fun observeIncomingRequests(): Flow<List<ContactRequest>> = emptyFlow()

    override suspend fun syncContactRequests(): Result<List<ContactRequest>, ContactError> =
        executeSessionOperation { session, _ ->
            coroutineBinding {
                val requests = bindApi(ContactError::Unknown) {
                    contactApi.getContactRequests()
                }
                session.ensureCurrent()
                requests
            }
        }

    override suspend fun handleFriendRequest(
        requestId: Uuid,
        accept: Boolean,
        remark: String?
    ): Result<Boolean, ContactError> =
        executeSessionOperation { session, _ ->
            coroutineBinding {
                val action = if (accept) ContactRequestAction.APPROVE else ContactRequestAction.REJECT
                val result = bindApi(ContactError::Unknown) {
                    contactApi.handleRequest(requestId, action, remark)
                }
                session.ensureCurrent()
                result
            }
        }

    override suspend fun blockUser(userId: Uuid): Result<Boolean, ContactError> =
        executeSessionOperation { session, _ ->
            coroutineBinding {
                val result = bindApi(ContactError::Unknown) {
                    contactApi.blockUser(userId)
                }
                session.ensureCurrent()
                result
            }
        }

    override suspend fun unblockUser(userId: Uuid): Result<Boolean, ContactError> =
        executeSessionOperation { session, _ ->
            coroutineBinding {
                val result = bindApi(ContactError::Unknown) {
                    contactApi.unblockUser(userId)
                }
                session.ensureCurrent()
                result
            }
        }

    override suspend fun removeFriend(userId: Uuid): Result<Boolean, ContactError> =
        executeSessionOperation { session, _ ->
            coroutineBinding {
                val result = bindApi(ContactError::Unknown) {
                    contactApi.deleteContact(userId)
                }
                session.ensureCurrent()
                result
            }
        }
}
