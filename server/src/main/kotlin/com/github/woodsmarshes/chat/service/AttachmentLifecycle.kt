package com.github.woodsmarshes.chat.service

import com.github.woodsmarshes.chat.core.model.FileContent
import com.github.woodsmarshes.chat.core.model.MediaContent
import com.github.woodsmarshes.chat.core.model.Message
import com.github.woodsmarshes.chat.repository.ConversationParticipantRepository
import com.github.woodsmarshes.chat.repository.MessageRepository
import com.github.woodsmarshes.chat.repository.PrivateFileRepository
import com.github.woodsmarshes.chat.utils.PRIVATE_FILE_URL_PREFIX
import com.github.woodsmarshes.chat.utils.PUBLIC_UPLOAD_URL_PREFIX
import com.github.woodsmarshes.chat.utils.TemporaryUploadStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.Uuid

/**
 * Everything that happens to an uploaded file between upload and deletion:
 * trusting a URL at send time, and garbage-collecting it when its last
 * message is withdrawn. Extracted from MessageService so the send/read
 * business rules stay separate from attachment bookkeeping.
 */
class AttachmentLifecycle(
    private val messageRepository: MessageRepository,
    private val fileService: FileService,
    private val uploadStore: TemporaryUploadStore,
    private val privateFileRepository: PrivateFileRepository,
    private val participantRepository: ConversationParticipantRepository,
) {
    /**
     * Fixed-size striped coroutine mutexes keyed by storage `fileName`.
     * Bounded in memory (unlike an ever-growing per-file map), stable for a
     * given `fileName`, and only serializes operations on the same stripe
     * rather than locking all attachment sends globally.
     */
    private val attachmentLockStripes = Array(LOCK_STRIPE_COUNT) { Mutex() }

    suspend fun <T> withAttachmentLock(fileName: String, action: suspend () -> T): T {
        val stripeIndex = (fileName.hashCode() and Int.MAX_VALUE) % attachmentLockStripes.size
        return attachmentLockStripes[stripeIndex].withLock {
            action()
        }
    }

    /**
     * An upload is trusted either while it is still pending in the upload
     * store for [senderId] (first send) or once it has already been persisted
     * as part of a message in a conversation [senderId] belongs to
     * (forwarding / re-attaching an old URL), provided the underlying file
     * still exists on disk. Public /uploads media additionally accepts an
     * existing file on disk, since that tree is public by design.
     */
    suspend fun resolveTrustedMedia(content: MediaContent, senderId: Uuid): MediaContent? {
        val fromPending = uploadStore.retrieveAndConfirm(content.url, senderId)
        if (fromPending != null) {
            if (content.url.startsWith(PRIVATE_FILE_URL_PREFIX)) {
                val fileName = content.url.substringAfterLast('/')
                if (fileService.resolvePrivateFile(fileName) == null) {
                    uploadStore.invalidate(content.url)
                    return null
                }
            }
            return fromPending
        }
        return when {
            content.url.startsWith(PRIVATE_FILE_URL_PREFIX) -> {
                val fileName = content.url.substringAfterLast('/')
                if (fileService.resolvePrivateFile(fileName) == null) {
                    return null
                }
                val conversations = privateFileRepository.getConversationsForFile(fileName)
                val authorized = conversations.isNotEmpty() && conversations.any { convId ->
                    participantRepository.getConversationParticipant(senderId, convId) != null
                }
                content.takeIf { authorized }
            }
            content.url.startsWith(PUBLIC_UPLOAD_URL_PREFIX) ->
                content.takeIf { fileService.publicFileExists(content.url) }
            else -> null
        }
    }

    /**
     * When the withdrawn message was the last live reference to a private
     * attachment, invalidate any cached pending/confirmed upload entry and
     * delete both the conversation mappings and the bytes on disk under the
     * striped per-attachment lock.
     */
    suspend fun gcAfterWithdraw(message: Message) {
        val content = message.content as? FileContent ?: return
        if (!content.url.startsWith(PRIVATE_FILE_URL_PREFIX)) return
        val fileName = content.url.substringAfterLast('/')
        withAttachmentLock(fileName) {
            if (messageRepository.countLiveFileReferences(fileName) == 0L) {
                uploadStore.invalidate(content.url)
                fileService.deletePrivateFile(fileName)
            }
        }
    }

    private companion object {
        const val LOCK_STRIPE_COUNT = 64
    }
}
