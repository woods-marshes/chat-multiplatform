package com.github.woodsmarshes.chat.app.session

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.woodsmarshes.chat.core.data.repository.ContactRepository
import com.github.woodsmarshes.chat.core.data.repository.UserRepository
import com.github.woodsmarshes.chat.core.model.Contact
import com.github.woodsmarshes.chat.core.model.ContactRequest
import com.github.woodsmarshes.chat.core.model.PrivacySetting
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.UserPreference
import com.github.woodsmarshes.chat.core.model.UserSetting
import com.github.woodsmarshes.chat.core.model.error.ContactError
import com.github.woodsmarshes.chat.core.model.error.UserError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.uuid.Uuid

/**
 * Null-object repositories standing in while no session is active: flows
 * stay empty and every call is refused, so pre-login screens observe no data
 * and cannot accidentally reach a database that is not open yet.
 */

internal object InertUserRepository : UserRepository {
    override fun getMeFlow(): Flow<User?> = emptyFlow()
    override suspend fun syncMe(): Result<User, UserError> = Err(UserError.PermissionDenied)
    override suspend fun updateMyProfile(
        displayName: String?,
        avatarUrl: String?,
        bio: String?,
    ): Result<User, UserError> = Err(UserError.PermissionDenied)
    override fun getGlobalSettingsFlow(): Flow<UserSetting?> = emptyFlow()
    override suspend fun syncGlobalSettings(): Result<UserSetting, UserError> = Err(UserError.PermissionDenied)
    override suspend fun updateGlobalSettings(
        privacy: PrivacySetting?,
        preferences: UserPreference?,
    ): Result<Boolean, UserError> = Err(UserError.PermissionDenied)
    override suspend fun fetchUserDetail(userId: Uuid): Result<User, UserError> = Err(UserError.PermissionDenied)
    override fun getUserFlow(userId: Uuid): Flow<User?> = emptyFlow()
    override suspend fun searchUsers(keyword: String): Result<List<User>, UserError> = Err(UserError.PermissionDenied)
}

internal object InertContactRepository : ContactRepository {
    override fun getFriendsFlow(): Flow<List<Pair<Contact, User>>> = emptyFlow()
    override suspend fun syncFriends(): Result<Unit, ContactError> = Err(ContactError.PermissionDenied)
    override suspend fun searchContacts(query: String): Result<List<Pair<Contact, User>>, ContactError> =
        Err(ContactError.PermissionDenied)
    override suspend fun sendFriendRequest(targetId: Uuid, message: String?): Result<Boolean, ContactError> =
        Err(ContactError.PermissionDenied)
    override fun observeIncomingRequests(): Flow<List<ContactRequest>> = emptyFlow()
    override suspend fun syncContactRequests(): Result<List<ContactRequest>, ContactError> =
        Err(ContactError.PermissionDenied)
    override suspend fun handleFriendRequest(
        requestId: Uuid,
        accept: Boolean,
        remark: String?,
    ): Result<Boolean, ContactError> = Err(ContactError.PermissionDenied)
    override suspend fun blockUser(userId: Uuid): Result<Boolean, ContactError> = Err(ContactError.PermissionDenied)
    override suspend fun unblockUser(userId: Uuid): Result<Boolean, ContactError> = Err(ContactError.PermissionDenied)
    override suspend fun removeFriend(userId: Uuid): Result<Boolean, ContactError> = Err(ContactError.PermissionDenied)
}
