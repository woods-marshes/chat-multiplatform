package com.github.woodsmarshes.chat.utils

import com.github.woodsmarshes.chat.core.model.error.ArticleError
import com.github.woodsmarshes.chat.core.model.error.AuthError
import com.github.woodsmarshes.chat.core.model.error.ContactError
import com.github.woodsmarshes.chat.core.model.error.ConversationError
import com.github.woodsmarshes.chat.core.model.error.DomainError
import com.github.woodsmarshes.chat.core.model.error.FileError
import com.github.woodsmarshes.chat.core.model.error.MessageError
import com.github.woodsmarshes.chat.core.model.error.UserError
import io.ktor.http.HttpStatusCode

fun DomainError.toHttpStatusCode(): HttpStatusCode = when (this) {
    is AuthError -> this.mapToStatus()
    is ContactError -> this.mapToStatus()
    is FileError -> this.mapToStatus()
    is MessageError -> this.mapToStatus()
    is UserError -> this.mapToStatus()
    is ArticleError -> this.mapToStatus()
    is ConversationError -> this.mapToStatus()
    else -> HttpStatusCode.BadRequest
}

fun AuthError.mapToStatus(): HttpStatusCode = when (this) {
    AuthError.InvalidCredentials -> HttpStatusCode.Unauthorized // 401
    AuthError.UserAlreadyExists -> HttpStatusCode.Conflict      // 409
    AuthError.WeakPassword -> HttpStatusCode.BadRequest         // 400
    AuthError.InsertionFailed -> HttpStatusCode.InternalServerError // 500
    is AuthError.Unknown -> HttpStatusCode.InternalServerError
}

fun ContactError.mapToStatus(): HttpStatusCode = when (this) {
    ContactError.UserBlocked -> HttpStatusCode.Forbidden        // 403 被屏蔽无法操作
    ContactError.BlockedByTarget -> HttpStatusCode.Forbidden    // 403 对方把你屏蔽了
    ContactError.PermissionDenied -> HttpStatusCode.Forbidden   // 403 操作了不属于你的请求

    ContactError.AlreadyFriends -> HttpStatusCode.Conflict      // 409 已经是好友
    ContactError.RequestAlreadySent -> HttpStatusCode.Conflict  // 409 请求已发送

    ContactError.RequestNotFound -> HttpStatusCode.NotFound     // 404

    ContactError.OperationFailed -> HttpStatusCode.InternalServerError // 500
    is ContactError.Unknown -> HttpStatusCode.InternalServerError
}

fun FileError.mapToStatus(): HttpStatusCode = when (this) {
    FileError.FileTooLarge -> HttpStatusCode.PayloadTooLarge
    FileError.UnsupportedFormat -> HttpStatusCode.UnsupportedMediaType
    FileError.NoFileProvided -> HttpStatusCode.BadRequest
    FileError.UploadFailed -> HttpStatusCode.InternalServerError
    FileError.ProcessingFailed -> HttpStatusCode.UnprocessableEntity
    FileError.IoError -> HttpStatusCode.InternalServerError
    is FileError.Unknown -> HttpStatusCode.InternalServerError
}

fun MessageError.mapToStatus(): HttpStatusCode = when (this) {
    MessageError.PermissionDenied -> HttpStatusCode.Forbidden
    MessageError.NotParticipant -> HttpStatusCode.Forbidden
    MessageError.UserBlocked -> HttpStatusCode.Forbidden
    MessageError.StrangerChatDenied -> HttpStatusCode.Forbidden
    MessageError.ConversationMuted -> HttpStatusCode.Forbidden

    MessageError.MessageNotFound -> HttpStatusCode.NotFound
    MessageError.ConversationNotFound -> HttpStatusCode.NotFound
    MessageError.ConversationDeleted -> HttpStatusCode.NotFound

    MessageError.MediaExpired -> HttpStatusCode.BadRequest
    MessageError.InvalidContent -> HttpStatusCode.BadRequest

    MessageError.OperationFailed -> HttpStatusCode.InternalServerError
    MessageError.RevokeFailed -> HttpStatusCode.Conflict // 撤回失败通常是因为时间窗口过了

    is MessageError.Unknown -> HttpStatusCode.InternalServerError
}

fun UserError.mapToStatus(): HttpStatusCode = when (this) {
    UserError.NotFound -> HttpStatusCode.NotFound
    UserError.PermissionDenied -> HttpStatusCode.Forbidden
    UserError.InvalidRequest -> HttpStatusCode.BadRequest
    UserError.UpdateFailed -> HttpStatusCode.InternalServerError
    is UserError.Unknown -> HttpStatusCode.InternalServerError
}

fun ArticleError.mapToStatus(): HttpStatusCode = when (this) {
    ArticleError.NotFound -> HttpStatusCode.NotFound
    ArticleError.PermissionDenied -> HttpStatusCode.Forbidden
    ArticleError.OperationFailed -> HttpStatusCode.InternalServerError
    is ArticleError.Unknown -> HttpStatusCode.InternalServerError
}

fun ConversationError.mapToStatus(): HttpStatusCode = when (this) {
    // 权限不足 / 禁止访问 -> 403 Forbidden
    ConversationError.PermissionDenied -> HttpStatusCode.Forbidden
    ConversationError.NotParticipant -> HttpStatusCode.Forbidden
    ConversationError.NotFriend -> HttpStatusCode.Forbidden
    ConversationError.InviteDisabled -> HttpStatusCode.Forbidden

    // 资源不存在 -> 404 Not Found
    ConversationError.NotFound -> HttpStatusCode.NotFound
    ConversationError.Deleted -> HttpStatusCode.NotFound
    ConversationError.TargetUserNotFound -> HttpStatusCode.NotFound
    ConversationError.RequestNotFound -> HttpStatusCode.NotFound

    // 业务状态冲突 -> 409 Conflict
    ConversationError.UserAlreadyMember -> HttpStatusCode.Conflict
    ConversationError.RequestAlreadyPending -> HttpStatusCode.Conflict
    ConversationError.RequestAlreadyProcessed -> HttpStatusCode.Conflict
    ConversationError.HandleAlreadyExists -> HttpStatusCode.Conflict
    ConversationError.PrivateChatDeleteNotAllowed -> HttpStatusCode.Conflict // 必须先解除好友才能删，属于状态冲突

    // 参数错误 -> 400 Bad Request
    ConversationError.InvalidRequest -> HttpStatusCode.BadRequest

    // 系统/数据错误 -> 500 Internal Server Error
    ConversationError.OperationFailed -> HttpStatusCode.InternalServerError
    ConversationError.DataIntegrityError -> HttpStatusCode.InternalServerError
    is ConversationError.Unknown -> HttpStatusCode.InternalServerError
}