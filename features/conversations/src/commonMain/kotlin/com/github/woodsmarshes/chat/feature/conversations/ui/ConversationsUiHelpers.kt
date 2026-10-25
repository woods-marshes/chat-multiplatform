package com.github.woodsmarshes.chat.feature.conversations.ui

import com.github.michaelbull.result.onErr
import com.github.michaelbull.result.onOk
import com.github.woodsmarshes.chat.core.data.repository.ConversationRepository
import com.github.woodsmarshes.chat.core.model.Contact
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.presentation.ContactUiModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Friend picker display name resolution shared by the create-group and
 * invite-friend flows: explicit alias beats cached nickname beats the user's
 * own display name.
 */
internal fun toContactUiModel(contact: Contact, user: User): ContactUiModel = ContactUiModel(
    id = user.id,
    username = user.username,
    displayName = contact.alias?.takeIf { it.isNotBlank() }
        ?: contact.nickname?.takeIf { it.isNotBlank() }
        ?: user.displayName,
    avatarUrl = user.avatarUrl,
    bio = user.bio,
)

/**
 * Debounced group-handle availability check shared by the create-group and
 * edit-group flows. Reports (isChecking, isAvailable); availability is null
 * when the check itself failed.
 */
internal fun CoroutineScope.launchGroupHandleCheck(
    handle: String,
    repository: ConversationRepository,
    onResult: (isChecking: Boolean, isAvailable: Boolean?) -> Unit,
): Job = launch {
    delay(400)
    repository.checkHandleExists(handle).onOk { exists ->
        onResult(false, !exists)
    }.onErr {
        onResult(false, null)
    }
}
