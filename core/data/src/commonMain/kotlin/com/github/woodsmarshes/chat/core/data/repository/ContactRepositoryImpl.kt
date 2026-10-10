package com.github.woodsmarshes.chat.core.data.repository

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.woodsmarshes.chat.core.data.model.toContact
import com.github.woodsmarshes.chat.core.data.model.toContactRequest
import com.github.woodsmarshes.chat.core.data.model.toEntity
import com.github.woodsmarshes.chat.core.data.model.toFriend
import com.github.woodsmarshes.chat.core.data.model.toUserEntity
import com.github.woodsmarshes.chat.core.database.dao.ContactDao
import com.github.woodsmarshes.chat.core.database.dao.ContactRequestDao
import com.github.woodsmarshes.chat.core.database.dao.UserDao
import com.github.woodsmarshes.chat.core.datastore.UserSettingDataSource
import com.github.woodsmarshes.chat.core.model.Contact
import com.github.woodsmarshes.chat.core.model.ContactRequest
import com.github.woodsmarshes.chat.core.model.ContactStatus
import com.github.woodsmarshes.chat.core.model.RequestStatus
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.error.ContactError
import com.github.woodsmarshes.chat.core.network.api.rest.ContactApi
import com.github.woodsmarshes.chat.core.network.api.websocket.RealtimeApi
import com.github.woodsmarshes.chat.core.network.dto.contact.ContactRequestAction
import com.github.woodsmarshes.chat.core.network.dto.events.ContactEventResponse
import com.github.woodsmarshes.chat.core.network.ktor.bindApi
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.woodsmarshes.chat.db.ChatDatabase
import io.github.woodsmarshes.chat.db.ContactRequestEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.coroutines.ContinuationInterceptor
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.uuid.Uuid

class ContactRepositoryImpl(
    private val contactApi: ContactApi,
    private val contactDao: ContactDao,
    private val contactRequestDao: ContactRequestDao,
    private val userDao: UserDao,
    private val userSettingDataSource: UserSettingDataSource? = null,
    private val realtimeApi: RealtimeApi? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : ContactRepository {

    private val log = KotlinLogging.logger {}

    /**
     * Conflated signals decoupling realtime events from network syncs: a burst
     * of N events collapses into one sync, and the websocket read loop is never
     * backpressured by a slow refresh (the shared event flow uses suspending
     * emit with a bounded buffer that both collectors share).
     */
    private val requestSyncSignal = MutableSharedFlow<Unit>(
        replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val friendsSyncSignal = MutableSharedFlow<Unit>(
        replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private var realtimeJob: Job? = null

    @Volatile
    private var pinnedRequestDao: ContactRequestDao? = null

    private data class BoundContactResources(
        val database: ChatDatabase,
        val contactDao: ContactDao,
        val userDao: UserDao,
        val contactRequestDao: ContactRequestDao,
    )

    private val sessionRunner = RepositorySessionRunner(
        dispatcher = scope.coroutineContext[ContinuationInterceptor] as? CoroutineDispatcher ?: Dispatchers.Default,
        ownerName = "ContactRepository",
        captureState = {
            val pinnedContactDao = contactDao.bindToCurrentDatabase()
            val pinnedUserDao = userDao.bindToCurrentDatabase()
            val pinnedContactRequestDao = contactRequestDao.bindToCurrentDatabase()
            val contactDb = checkNotNull(pinnedContactDao.boundDatabase) {
                "ContactDao.bindToCurrentDatabase() must return a DAO bound to a non-null ChatDatabase instance"
            }
            val userDb = checkNotNull(pinnedUserDao.boundDatabase) {
                "UserDao.bindToCurrentDatabase() must return a DAO bound to a non-null ChatDatabase instance"
            }
            val requestDb = checkNotNull(pinnedContactRequestDao.boundDatabase) {
                "ContactRequestDao.bindToCurrentDatabase() must return a DAO bound to a non-null ChatDatabase instance"
            }
            check(contactDb === userDb && contactDb === requestDb) {
                "ContactDao, UserDao and ContactRequestDao must resolve to the same session database instance"
            }
            BoundContactResources(
                database = contactDb,
                contactDao = pinnedContactDao,
                userDao = pinnedUserDao,
                contactRequestDao = pinnedContactRequestDao,
            )
        },
        isSameState = { existing, candidate ->
            existing.database === candidate.database &&
                existing.contactDao.boundDatabase === candidate.contactDao.boundDatabase &&
                existing.userDao.boundDatabase === candidate.userDao.boundDatabase &&
                existing.contactRequestDao.boundDatabase === candidate.contactRequestDao.boundDatabase
        },
        databaseOf = { it.database },
    )

    override suspend fun startSession() {
        sessionRunner.startSession()
        val rt = realtimeApi
        if (rt != null && realtimeJob?.isActive != true) {
            // Pin the ledger DAO to this session's database: the realtime
            // collector runs on the repository scope, outside any session-bound
            // context, so unqualified writes could trail an account switch into
            // the next user's database.
            pinnedRequestDao = contactRequestDao.bindToCurrentDatabase()
            // Refresh the request ledger on every session start so badges are
            // current even when no realtime event fires.
            requestSyncSignal.tryEmit(Unit)
            realtimeJob = scope.launch {
                launch {
                    rt.events.collect { event ->
                        val requestDao = pinnedRequestDao
                        when (event) {
                            is ContactEventResponse.FriendRequestSent -> {
                                // Seed the ledger immediately; the verification
                                // message (not in the event payload) arrives with
                                // the follow-up sync. Seed semantics keep any
                                // already-known message and never downgrade a
                                // handled row on replay.
                                runCatching {
                                    requestDao?.seedContactRequest(
                                        ContactRequestEntity(
                                            id = event.requestId,
                                            sender_id = event.senderId,
                                            receiver_id = event.receiverId,
                                            message = null,
                                            status = RequestStatus.PENDING,
                                            created_at = event.timestamp,
                                            updated_at = event.timestamp,
                                        )
                                    )
                                }.onFailure { log.warn(it) { "Failed to record FriendRequestSent ${event.requestId}" } }
                                requestSyncSignal.tryEmit(Unit)
                            }
                            is ContactEventResponse.FriendRequestAccepted -> {
                                runCatching {
                                    requestDao?.updateContactRequestStatus(
                                        event.requestId, RequestStatus.ACCEPTED, event.timestamp,
                                    )
                                }
                                friendsSyncSignal.tryEmit(Unit)
                                requestSyncSignal.tryEmit(Unit)
                            }
                            is ContactEventResponse.FriendRequestRejected -> {
                                runCatching {
                                    requestDao?.updateContactRequestStatus(
                                        event.requestId, RequestStatus.REJECTED, event.timestamp,
                                    )
                                }
                                requestSyncSignal.tryEmit(Unit)
                            }
                            is ContactEventResponse.ContactAdded,
                            is ContactEventResponse.ContactDeleted,
                            is ContactEventResponse.UserBlocked,
                            is ContactEventResponse.UserUnblocked,
                            is ContactEventResponse.ContactUpdated -> friendsSyncSignal.tryEmit(Unit)
                            else -> Unit
                        }
                    }
                }
                launch {
                    requestSyncSignal.collect { syncContactRequests() }
                }
                launch {
                    friendsSyncSignal.collect { syncFriends() }
                }
            }
        }
    }

    override suspend fun stopSession() {
        realtimeJob?.cancelAndJoin()
        realtimeJob = null
        pinnedRequestDao = null
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

    override fun getBlockedContactsFlow(): Flow<List<Pair<Contact, User>>> =
        contactDao.getBlockedContactsWithUserInfo().map { rows ->
            rows.map { it.toFriend() }
        }

    override fun getContactFlow(userId: Uuid): Flow<Contact?> =
        contactDao.getContactById(userId).map { entity ->
            entity?.toContact(Uuid.NIL)
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
                // The response carries no request id; the ledger refreshes via
                // the conflated sync and the FriendRequestSent event.
                if (result) {
                    requestSyncSignal.tryEmit(Unit)
                }
                result
            }
        }

    override fun observeIncomingRequests(): Flow<List<ContactRequest>> {
        val ownFlow = userSettingDataSource?.user
        val all = contactRequestDao.selectAllContactRequests().map { rows ->
            rows.map { it.toContactRequest() }
        }
        return if (ownFlow != null) {
            combine(all, ownFlow) { requests, me ->
                if (me == null) {
                    requests.filter { it.status == RequestStatus.PENDING }
                } else {
                    requests.filter { it.receiverId == me.id && it.status == RequestStatus.PENDING }
                }
            }
        } else {
            all.map { list -> list.filter { it.status == RequestStatus.PENDING } }
        }
    }

    override fun observeAllRequests(): Flow<List<ContactRequest>> =
        contactRequestDao.selectAllContactRequests().map { rows ->
            rows.map { it.toContactRequest() }
        }

    override suspend fun syncContactRequests(): Result<List<ContactRequest>, ContactError> =
        executeSessionOperation { session, resources ->
            coroutineBinding {
                val requests = bindApi(ContactError::Unknown) {
                    contactApi.getContactRequests()
                }
                session.ensureCurrent()
                persistContactRequests(resources.contactRequestDao, requests)
                requests
            }
        }

    private suspend fun persistContactRequests(
        contactRequestDao: ContactRequestDao,
        requests: List<ContactRequest>,
    ) {
        if (requests.isEmpty()) return
        contactRequestDao.transaction {
            requests.forEach { request ->
                // Row-isolated: one malformed row must not abort the batch sync.
                runCatching { contactRequestDao.upsertContactRequest(request.toEntity()) }
                    .onFailure { log.warn(it) { "Skipping unpersistable contact request ${request.id}" } }
            }
        }
    }

    override suspend fun handleFriendRequest(
        requestId: Uuid,
        accept: Boolean,
        remark: String?
    ): Result<Boolean, ContactError> =
        executeSessionOperation { session, resources ->
            coroutineBinding {
                val action = if (accept) ContactRequestAction.APPROVE else ContactRequestAction.REJECT
                val result = bindApi(ContactError::Unknown) {
                    contactApi.handleRequest(requestId, action, remark)
                }
                session.ensureCurrent()
                if (result) {
                    resources.contactRequestDao.updateContactRequestStatus(
                        requestId,
                        if (accept) RequestStatus.ACCEPTED else RequestStatus.REJECTED,
                        Clock.System.now(),
                    )
                    if (accept) {
                        friendsSyncSignal.tryEmit(Unit)
                    }
                    requestSyncSignal.tryEmit(Unit)
                }
                result
            }
        }

    override suspend fun cancelFriendRequest(requestId: Uuid): Result<Boolean, ContactError> =
        executeSessionOperation { session, resources ->
            coroutineBinding {
                val result = bindApi(ContactError::Unknown) {
                    contactApi.handleRequest(requestId, ContactRequestAction.CANCEL, null)
                }
                session.ensureCurrent()
                if (result) {
                    resources.contactRequestDao.updateContactRequestStatus(
                        requestId,
                        RequestStatus.CANCELED,
                        Clock.System.now(),
                    )
                    requestSyncSignal.tryEmit(Unit)
                }
                result
            }
        }

    override suspend fun updateContactInfo(
        userId: Uuid,
        nickname: String?,
        alias: String?,
    ): Result<Boolean, ContactError> =
        executeSessionOperation { session, resources ->
            coroutineBinding {
                val result = bindApi(ContactError::Unknown) {
                    contactApi.updateContactInfo(userId, nickname, alias)
                }
                session.ensureCurrent()
                if (result) {
                    resources.contactDao.updateAlias(userId, alias, Clock.System.now())
                }
                result
            }
        }

    override suspend fun blockUser(userId: Uuid): Result<Boolean, ContactError> =
        executeSessionOperation { session, resources ->
            coroutineBinding {
                val result = bindApi(ContactError::Unknown) {
                    contactApi.blockUser(userId)
                }
                session.ensureCurrent()
                if (result) {
                    val now = Clock.System.now()
                    val existing = resources.contactDao.getContactById(userId).first()
                    when {
                        existing != null ->
                            resources.contactDao.updateStatus(userId, ContactStatus.BLOCKED, now)
                        // ProfileScreen always runs fetchUserDetail first, so the
                        // UserEntity parent row is usually present and the mirror
                        // insert is foreign-key safe.
                        resources.userDao.getUserById(userId).first() != null ->
                            resources.contactDao.insertContact(
                                io.github.woodsmarshes.chat.db.ContactEntity(
                                    contact_id = userId,
                                    status = ContactStatus.BLOCKED,
                                    nickname = null,
                                    alias = null,
                                    created_at = now,
                                    updated_at = now,
                                )
                            )
                        // Truly unknown user: skip the mirror rather than violate
                        // the foreign key and mask the successful server-side
                        // block; the friends sync repairs the cache.
                        else -> log.warn { "[contacts] blocked user $userId has no local row; cache repairs via sync" }
                    }
                    // Authoritative repair regardless of which mirror path ran.
                    friendsSyncSignal.tryEmit(Unit)
                }
                result
            }
        }

    override suspend fun unblockUser(userId: Uuid): Result<Boolean, ContactError> =
        executeSessionOperation { session, resources ->
            coroutineBinding {
                val result = bindApi(ContactError::Unknown) {
                    contactApi.unblockUser(userId)
                }
                session.ensureCurrent()
                if (result) {
                    // Leave the blocked list immediately; the authoritative friend
                    // state (conditionally restored to FRIEND or DELETED server-side)
                    // arrives with the conflated friends sync.
                    resources.contactDao.updateStatus(userId, ContactStatus.DELETED, Clock.System.now())
                    friendsSyncSignal.tryEmit(Unit)
                }
                result
            }
        }

    override suspend fun removeFriend(userId: Uuid): Result<Boolean, ContactError> =
        executeSessionOperation { session, resources ->
            coroutineBinding {
                val result = bindApi(ContactError::Unknown) {
                    contactApi.deleteContact(userId)
                }
                session.ensureCurrent()
                if (result) {
                    resources.contactDao.updateStatus(userId, ContactStatus.DELETED, Clock.System.now())
                }
                result
            }
        }
}

