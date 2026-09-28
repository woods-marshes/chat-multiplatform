package com.github.woodsmarshes.chat.service

import com.github.woodsmarshes.chat.core.model.FileContent
import com.github.woodsmarshes.chat.core.model.MediaContent
import com.github.woodsmarshes.chat.core.model.Message
import com.github.woodsmarshes.chat.repository.MessageRepository
import com.github.woodsmarshes.chat.repository.PrivateFileRepository
import com.github.woodsmarshes.chat.utils.PRIVATE_FILE_URL_PREFIX
import com.github.woodsmarshes.chat.utils.PUBLIC_UPLOAD_URL_PREFIX
import com.github.woodsmarshes.chat.utils.TemporaryUploadStore

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
) {

    /**
     * An upload is trusted either while it is still pending in the upload
     * store (first send) or once it has already been persisted as part of a
     * message somewhere (forwarding / re-attaching an old URL). Public
     * /uploads media additionally accepts an existing file on disk, since
     * that tree is public by design.
     */
    suspend fun resolveTrustedMedia(content: MediaContent): MediaContent? =
        uploadStore.retrieveAndConfirm(content.url)
            ?: when {
                content.url.startsWith(PRIVATE_FILE_URL_PREFIX) ->
                    content.takeIf { privateFileRepository.hasMapping(content.url.substringAfterLast('/')) }
                content.url.startsWith(PUBLIC_UPLOAD_URL_PREFIX) ->
                    content.takeIf { fileService.publicFileExists(content.url) }
                else -> null
            }

    /**
     * When the withdrawn message was the last live reference to a private
     * attachment, remove the bytes and its conversation mappings. A
     * concurrent forward of the same URL re-registers the mapping on insert,
     * so the only visible race is a forward whose file vanishes mid-flight.
     */
    suspend fun gcAfterWithdraw(message: Message) {
        val content = message.content as? FileContent ?: return
        if (!content.url.startsWith(PRIVATE_FILE_URL_PREFIX)) return
        val fileName = content.url.substringAfterLast('/')
        if (messageRepository.countLiveFileReferences(fileName) > 0L) return
        fileService.deletePrivateFile(fileName)
    }
}
