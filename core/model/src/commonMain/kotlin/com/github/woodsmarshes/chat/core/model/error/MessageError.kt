package com.github.woodsmarshes.chat.core.model.error

import kotlinx.serialization.Serializable

@Serializable
sealed interface MessageError : DomainError {
    // 权限与访问
    @Serializable data object PermissionDenied : MessageError    // 权限不足（如非管理员撤回他人消息）
    @Serializable data object NotParticipant : MessageError    // 用户不在会话中
    @Serializable data object UserBlocked : MessageError       // 被对方屏蔽
    @Serializable data object ConversationDeleted : MessageError // 会话已被删除
    @Serializable data object ConversationMuted : MessageError   // 全员禁言或被禁言
    @Serializable data object StrangerChatDenied : MessageError  // 对方拒绝陌生人消息

    // 资源状态
    @Serializable data object MessageNotFound : MessageError   // 消息未找到
    @Serializable data object ConversationNotFound : MessageError // 会话未找到

    // 内容校验
    @Serializable data object MediaExpired : MessageError      // 多媒体文件已过期或未找到
    @Serializable data object InvalidContent : MessageError    // 内容格式错误

    // 操作结果
    @Serializable data object OperationFailed : MessageError   // 数据库插入/更新失败
    @Serializable data object RevokeFailed : MessageError      // 撤回失败（可能超过时间限制）

    @Serializable data class Unknown(override val message: String? = null) : MessageError
}