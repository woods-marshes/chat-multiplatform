package com.github.woodsmarshes.chat.core.data.repository

import com.github.michaelbull.result.Result
import com.github.woodsmarshes.chat.core.model.Contact
import com.github.woodsmarshes.chat.core.model.ContactRequest
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.error.ContactError
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

interface ContactRepository {
    fun getFriendsFlow(): Flow<List<Pair<Contact, User>>>

    suspend fun syncFriends(): Result<Unit, ContactError>

    suspend fun searchContacts(query: String): Result<List<Pair<Contact, User>>, ContactError>

    suspend fun sendFriendRequest(targetId: Uuid, message: String? = null): Result<Boolean, ContactError>

    fun observeIncomingRequests(): Flow<List<ContactRequest>>

    suspend fun syncContactRequests(): Result<List<ContactRequest>, ContactError>

    suspend fun handleFriendRequest(
        requestId: Uuid,
        accept: Boolean,
        remark: String? = null
    ): Result<Boolean, ContactError>

    suspend fun blockUser(userId: Uuid): Result<Boolean, ContactError>

    suspend fun unblockUser(userId: Uuid): Result<Boolean, ContactError>

    suspend fun removeFriend(userId: Uuid): Result<Boolean, ContactError>
}
