package com.github.woodsmarshes.chat.core.model.error

import kotlinx.serialization.Serializable

@Serializable
sealed interface ConversationError : DomainError {
    // 权限与访问控制
    @Serializable data object PermissionDenied : ConversationError      // 权限不足（非管理员/群主）
    @Serializable data object NotParticipant : ConversationError      // 不是该会话的成员
    @Serializable data object NotFriend : ConversationError           // 不是好友（无法邀请）

    // 资源状态
    @Serializable data object NotFound : ConversationError            // 会话或资源未找到
    @Serializable data object Deleted : ConversationError             // 会话已被删除
    @Serializable data object TargetUserNotFound : ConversationError  // 目标用户未找到

    // 业务逻辑冲突
    @Serializable data object UserAlreadyMember : ConversationError   // 用户已经是成员
    @Serializable data object InviteDisabled : ConversationError      // 群组禁止邀请
    @Serializable data object RequestAlreadyPending : ConversationError // 申请已存在且待处理
    @Serializable data object RequestAlreadyProcessed : ConversationError // 申请已被处理
    @Serializable data object RequestNotFound : ConversationError     // 申请未找到
    @Serializable data object PrivateChatDeleteNotAllowed : ConversationError // 私聊无法直接删除（需解除好友）
    @Serializable data object HandleAlreadyExists : ConversationError // Handle 已存在

    // 系统/操作错误
    @Serializable data object OperationFailed : ConversationError
    @Serializable data object DataIntegrityError : ConversationError  // 数据不一致（如群无资料）
    @Serializable data object InvalidRequest : ConversationError      // 请求参数错误

    @Serializable data class Unknown(override val message: String? = null) : ConversationError
}