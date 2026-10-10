package com.github.woodsmarshes.chat.core.data.repository

import com.github.michaelbull.result.Result
import com.github.woodsmarshes.chat.core.model.Contact
import com.github.woodsmarshes.chat.core.model.ContactRequest
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.error.ContactError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.uuid.Uuid

interface ContactRepository {
    /**
     * Binds the current session's database for both `ContactDao` and `UserDao`
     * and starts accepting session-bound contact operations. Idempotent while
     * the current session is already running on the same database; waits if a
     * previous session is still stopping.
     */
    suspend fun startSession() = Unit

    /**
     * Refuses new session-bound contact operations, cancels all in-flight ones,
     * and suspends until they (including their `finally` blocks and active
     * database transactions) have exited. Idempotent.
     */
    suspend fun stopSession() = Unit

    fun getFriendsFlow(): Flow<List<Pair<Contact, User>>>

    fun getBlockedContactsFlow(): Flow<List<Pair<Contact, User>>> = emptyFlow()

    fun getContactFlow(userId: Uuid): Flow<Contact?> = emptyFlow()

    suspend fun syncFriends(): Result<Unit, ContactError>

    suspend fun searchContacts(query: String): Result<List<Pair<Contact, User>>, ContactError>

    suspend fun sendFriendRequest(targetId: Uuid, message: String? = null): Result<Boolean, ContactError>

    fun observeIncomingRequests(): Flow<List<ContactRequest>>

    fun observeAllRequests(): Flow<List<ContactRequest>> = observeIncomingRequests()

    suspend fun syncContactRequests(): Result<List<ContactRequest>, ContactError>

    suspend fun handleFriendRequest(
        requestId: Uuid,
        accept: Boolean,
        remark: String? = null
    ): Result<Boolean, ContactError>

    suspend fun cancelFriendRequest(requestId: Uuid): Result<Boolean, ContactError> =
        handleFriendRequest(requestId = requestId, accept = false)

    suspend fun updateContactInfo(
        userId: Uuid,
        nickname: String? = null,
        alias: String? = null,
    ): Result<Boolean, ContactError> = com.github.michaelbull.result.Err(ContactError.PermissionDenied)

    suspend fun blockUser(userId: Uuid): Result<Boolean, ContactError>

    suspend fun unblockUser(userId: Uuid): Result<Boolean, ContactError>

    suspend fun removeFriend(userId: Uuid): Result<Boolean, ContactError>
}
