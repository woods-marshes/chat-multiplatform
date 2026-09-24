package com.github.woodsmarshes.chat.utils

import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import com.github.woodsmarshes.chat.core.model.error.ArticleError
import com.github.woodsmarshes.chat.core.model.error.AuthError
import com.github.woodsmarshes.chat.core.model.error.ContactError
import com.github.woodsmarshes.chat.core.model.error.ConversationError
import com.github.woodsmarshes.chat.core.model.error.DomainError
import com.github.woodsmarshes.chat.core.model.error.FileError
import com.github.woodsmarshes.chat.core.model.error.MessageError
import com.github.woodsmarshes.chat.core.model.error.UserError

/**
 * Exhaustive mapping table from every [DomainError] variant to its HTTP status.
 * The `when` in [DomainError.toHttpStatusCode] is compiler-checked for
 * exhaustiveness; this test pins the actual status codes.
 */
class ErrorMappingTest {

    private fun assertStatus(expected: HttpStatusCode, error: DomainError) {
        assertEquals(expected, error.toHttpStatusCode(), "wrong status for $error")
    }

    @Test
    fun `auth errors map to their documented status codes`() {
        assertStatus(HttpStatusCode.Unauthorized, AuthError.InvalidCredentials)
        assertStatus(HttpStatusCode.Conflict, AuthError.UserAlreadyExists)
        assertStatus(HttpStatusCode.BadRequest, AuthError.WeakPassword)
        assertStatus(HttpStatusCode.InternalServerError, AuthError.InsertionFailed)
        assertStatus(HttpStatusCode.InternalServerError, AuthError.Unknown("boom"))
    }

    @Test
    fun `contact errors map to their documented status codes`() {
        assertStatus(HttpStatusCode.Forbidden, ContactError.UserBlocked)
        assertStatus(HttpStatusCode.Forbidden, ContactError.BlockedByTarget)
        assertStatus(HttpStatusCode.Forbidden, ContactError.PermissionDenied)
        assertStatus(HttpStatusCode.Conflict, ContactError.AlreadyFriends)
        assertStatus(HttpStatusCode.Conflict, ContactError.RequestAlreadySent)
        assertStatus(HttpStatusCode.NotFound, ContactError.RequestNotFound)
        assertStatus(HttpStatusCode.InternalServerError, ContactError.OperationFailed)
        assertStatus(HttpStatusCode.InternalServerError, ContactError.Unknown(null))
    }

    @Test
    fun `file errors map to their documented status codes`() {
        assertStatus(HttpStatusCode.PayloadTooLarge, FileError.FileTooLarge)
        assertStatus(HttpStatusCode.UnsupportedMediaType, FileError.UnsupportedFormat)
        assertStatus(HttpStatusCode.BadRequest, FileError.NoFileProvided)
        assertStatus(HttpStatusCode.InternalServerError, FileError.UploadFailed)
        assertStatus(HttpStatusCode.UnprocessableEntity, FileError.ProcessingFailed)
        assertStatus(HttpStatusCode.InternalServerError, FileError.IoError)
        assertStatus(HttpStatusCode.InternalServerError, FileError.Unknown("io"))
    }

    @Test
    fun `message errors map to their documented status codes`() {
        assertStatus(HttpStatusCode.Forbidden, MessageError.PermissionDenied)
        assertStatus(HttpStatusCode.Forbidden, MessageError.NotParticipant)
        assertStatus(HttpStatusCode.Forbidden, MessageError.UserBlocked)
        assertStatus(HttpStatusCode.Forbidden, MessageError.StrangerChatDenied)
        assertStatus(HttpStatusCode.Forbidden, MessageError.ConversationMuted)
        assertStatus(HttpStatusCode.NotFound, MessageError.MessageNotFound)
        assertStatus(HttpStatusCode.NotFound, MessageError.ConversationNotFound)
        assertStatus(HttpStatusCode.NotFound, MessageError.ConversationDeleted)
        assertStatus(HttpStatusCode.BadRequest, MessageError.MediaExpired)
        assertStatus(HttpStatusCode.BadRequest, MessageError.InvalidContent)
        assertStatus(HttpStatusCode.InternalServerError, MessageError.OperationFailed)
        assertStatus(HttpStatusCode.Conflict, MessageError.RevokeFailed)
        assertStatus(HttpStatusCode.InternalServerError, MessageError.Unknown("m"))
    }

    @Test
    fun `user errors map to their documented status codes`() {
        assertStatus(HttpStatusCode.NotFound, UserError.NotFound)
        assertStatus(HttpStatusCode.Forbidden, UserError.PermissionDenied)
        assertStatus(HttpStatusCode.BadRequest, UserError.InvalidRequest)
        assertStatus(HttpStatusCode.InternalServerError, UserError.UpdateFailed)
        assertStatus(HttpStatusCode.InternalServerError, UserError.Unknown("u"))
    }

    @Test
    fun `article errors map to their documented status codes`() {
        assertStatus(HttpStatusCode.NotFound, ArticleError.NotFound)
        assertStatus(HttpStatusCode.Forbidden, ArticleError.PermissionDenied)
        assertStatus(HttpStatusCode.InternalServerError, ArticleError.OperationFailed)
        assertStatus(HttpStatusCode.InternalServerError, ArticleError.Unknown("a"))
    }

    @Test
    fun `conversation errors map to their documented status codes`() {
        // 403 — permission / membership
        assertStatus(HttpStatusCode.Forbidden, ConversationError.PermissionDenied)
        assertStatus(HttpStatusCode.Forbidden, ConversationError.NotParticipant)
        assertStatus(HttpStatusCode.Forbidden, ConversationError.NotFriend)
        assertStatus(HttpStatusCode.Forbidden, ConversationError.InviteDisabled)

        // 404 — missing resources
        assertStatus(HttpStatusCode.NotFound, ConversationError.NotFound)
        assertStatus(HttpStatusCode.NotFound, ConversationError.Deleted)
        assertStatus(HttpStatusCode.NotFound, ConversationError.TargetUserNotFound)
        assertStatus(HttpStatusCode.NotFound, ConversationError.RequestNotFound)

        // 409 — business state conflicts
        assertStatus(HttpStatusCode.Conflict, ConversationError.UserAlreadyMember)
        assertStatus(HttpStatusCode.Conflict, ConversationError.RequestAlreadyPending)
        assertStatus(HttpStatusCode.Conflict, ConversationError.RequestAlreadyProcessed)
        assertStatus(HttpStatusCode.Conflict, ConversationError.HandleAlreadyExists)
        assertStatus(HttpStatusCode.Conflict, ConversationError.PrivateChatDeleteNotAllowed)

        // 400 — bad request
        assertStatus(HttpStatusCode.BadRequest, ConversationError.InvalidRequest)

        // 500 — system errors
        assertStatus(HttpStatusCode.InternalServerError, ConversationError.OperationFailed)
        assertStatus(HttpStatusCode.InternalServerError, ConversationError.DataIntegrityError)
        assertStatus(HttpStatusCode.InternalServerError, ConversationError.Unknown("c"))
    }
}
