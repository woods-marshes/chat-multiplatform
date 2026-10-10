package com.github.woodsmarshes.chat.core.database.dao

import app.cash.sqldelight.SuspendingTransactionWithoutReturn
import com.github.woodsmarshes.chat.core.model.ContactStatus
import io.github.woodsmarshes.chat.db.ChatDatabase
import io.github.woodsmarshes.chat.db.ContactEntity
import io.github.woodsmarshes.chat.db.GetAllContactsWithUserInfo
import io.github.woodsmarshes.chat.db.GetBlockedContactsWithUserInfo
import io.github.woodsmarshes.chat.db.SearchContacts
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface ContactDao {
    val boundDatabase: ChatDatabase?

    /**
     * Captures the [ChatDatabase] active at the moment this method is called
     * and returns a [ContactDao] handle pinned to that exact database instance,
     * so subsequent DAO calls and transactions on the returned handle never
     * dynamically resolve a different session's database after a suspension
     * point.
     */
    fun bindToCurrentDatabase(): ContactDao

    suspend fun transaction(body: suspend SuspendingTransactionWithoutReturn.() -> Unit)

    // 写入与同步
    suspend fun insertContact(contact: ContactEntity)
    suspend fun insertContacts(contacts: List<ContactEntity>)

    // 查询 - 好友列表（带用户信息）
    fun getAllContactsWithUserInfo(): Flow<List<GetAllContactsWithUserInfo>>
    fun getBlockedContactsWithUserInfo(): Flow<List<GetBlockedContactsWithUserInfo>>

    // 查询 - 基础
    fun getContactById(contactId: Uuid): Flow<ContactEntity?>
    fun getBlockedContacts(): Flow<List<ContactEntity>>

    // 搜索
    fun searchContacts(query: String): Flow<List<SearchContacts>>

    // 更新
    suspend fun updateAlias(contactId: Uuid, alias: String?, updatedAt: Instant)
    suspend fun updateStatus(contactId: Uuid, status: ContactStatus, updatedAt: Instant)

    // 删除
    suspend fun deleteContact(contactId: Uuid)

    // 统计
    fun countFriends(): Flow<Long>
}
