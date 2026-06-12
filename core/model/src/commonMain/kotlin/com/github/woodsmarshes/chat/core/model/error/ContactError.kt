package com.github.woodsmarshes.chat.core.model.error

import kotlinx.serialization.Serializable
@Serializable
sealed interface ContactError : DomainError {
    // 业务逻辑错误
    @Serializable data object UserBlocked : ContactError          // 你屏蔽了对方
    @Serializable data object BlockedByTarget : ContactError      // 对方屏蔽了你
    @Serializable data object AlreadyFriends : ContactError       // 已经是好友
    @Serializable data object RequestAlreadySent : ContactError   // 请求已发送
    @Serializable data object RequestNotFound : ContactError      // 请求不存在
    @Serializable data object PermissionDenied : ContactError     // 权限不足（操作了不属于自己的请求）

    // 操作失败（数据库更新失败等）
    @Serializable data object OperationFailed : ContactError

    // 未知错误
    @Serializable data class Unknown(override val message: String? = null) : ContactError
}