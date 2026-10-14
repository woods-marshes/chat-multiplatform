package com.github.woodsmarshes.chat.core.data.repository

import com.github.woodsmarshes.chat.core.data.model.toConversation
import com.github.woodsmarshes.chat.core.data.model.toEntity
import com.github.woodsmarshes.chat.core.data.model.toGroupOwnerUserEntity
import com.github.woodsmarshes.chat.core.data.model.toGroupProfileEntity
import com.github.woodsmarshes.chat.core.data.model.toMessageEntity
import com.github.woodsmarshes.chat.core.data.model.toParticipantEntity
import com.github.woodsmarshes.chat.core.data.model.toPeerParticipantEntity
import com.github.woodsmarshes.chat.core.data.model.toUserEntity
import com.github.woodsmarshes.chat.core.database.dao.ContactDao
import com.github.woodsmarshes.chat.core.database.dao.ContactRequestDao
import com.github.woodsmarshes.chat.core.database.dao.ConversationDao
import com.github.woodsmarshes.chat.core.database.dao.GroupJoinRequestDao
import com.github.woodsmarshes.chat.core.database.dao.GroupProfileDao
import com.github.woodsmarshes.chat.core.database.dao.MessageDao
import com.github.woodsmarshes.chat.core.database.dao.ParticipantDao
import com.github.woodsmarshes.chat.core.database.dao.UserDao
import com.github.woodsmarshes.chat.core.model.Contact
import com.github.woodsmarshes.chat.core.model.ContactRequest
import com.github.woodsmarshes.chat.core.model.RequestStatus
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.network.api.rest.ContactApi
import com.github.woodsmarshes.chat.core.network.api.rest.ConversationApi
import com.github.woodsmarshes.chat.core.network.dto.conversation.ConversationResponse
import com.github.woodsmarshes.chat.core.network.dto.events.ContactEventResponse
import com.github.woodsmarshes.chat.core.network.dto.events.ConversationEventResponse
import com.github.woodsmarshes.chat.core.network.dto.events.MessageEventResponse
import com.github.woodsmarshes.chat.core.network.dto.events.RealtimeEvent
import io.github.woodsmarshes.chat.db.GroupJoinRequestEntity
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlin.uuid.Uuid

/**
 * A USER journal entry can no longer be applied incrementally — e.g. a
 * historical `UserJoinedConversation` whose membership has since lapsed, so
 * the server refuses the conversation detail (403/404). The stream must
 * re-sync from a controlled USER snapshot instead of retrying this batch
 * forever. Transient failures (5xx, IO, timeouts) never raise this.
 */
internal class UserSyncResetRequiredException(message: String) : IllegalStateException(message)

/**
 * Prepares USER-scope network state before the caller opens its database
 * transaction and applies it inside that transaction.
 *
 * Event contract: the server only journals events that concern the owning
 * account into its `USER` stream (joined/left carry the account's own id,
 * reads carry `readerId` = the account), so [apply] can trust event payloads
 * without re-resolving the local account id.
 */
class UserSyncApplier(
    private val contactApi: ContactApi,
    private val conversationApi: ConversationApi,
    private val contactDao: ContactDao,
    private val contactRequestDao: ContactRequestDao,
    private val userDao: UserDao,
    private val groupJoinRequestDao: GroupJoinRequestDao,
    private val participantDao: ParticipantDao,
    private val conversationDao: ConversationDao,
    private val groupProfileDao: GroupProfileDao,
    private val messageDao: MessageDao,
) {
    data class Prepared internal constructor(
        internal val contactRows: List<Pair<Contact, User>>?,
        internal val requestRows: List<ContactRequest>?,
        internal val joinedDetails: Map<Uuid, ConversationResponse>,
        internal val events: List<RealtimeEvent>,
    )

    suspend fun prepareSnapshot(events: List<RealtimeEvent>): Prepared = prepare(events, refreshContacts = true)

    suspend fun prepareBatch(events: List<RealtimeEvent>): Prepared =
        prepare(events, refreshContacts = events.any { it is ContactEventResponse })

    private suspend fun prepare(events: List<RealtimeEvent>, refreshContacts: Boolean): Prepared {
        events.forEach(::requireSupportedEvent)
        // Joined-conversation details are fetched OUTSIDE any transaction: the
        // journal row alone does not carry the conversation body needed to
        // display it, and HTTP must never run under a database lock.
        val joinedDetails = events
            .mapNotNull { (it as? ConversationEventResponse.UserJoinedConversation)?.conversationId }
            .distinct()
            .associateWith { fetchJoinedDetail(it) }
        if (!refreshContacts && joinedDetails.isEmpty()) {
            return Prepared(null, null, joinedDetails, events)
        }
        return Prepared(
            contactRows = if (refreshContacts) contactApi.getContacts() else null,
            requestRows = if (refreshContacts) contactApi.getContactRequests() else null,
            joinedDetails = joinedDetails,
            events = events,
        )
    }

    /**
     * Fetches the conversation body a historical join needs. `Forbidden`/`NotFound`
     * mean the account no longer has (or never regained) access after an offline
     * join+leave — raising [UserSyncResetRequiredException] routes the stream
     * into a snapshot reset. Every other failure (5xx, IO, timeouts, other
     * statuses) propagates so the batch keeps its cursor and retries later.
     */
    private suspend fun fetchJoinedDetail(conversationId: Uuid): ConversationResponse =
        try {
            conversationApi.getDetail(conversationId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ResponseException) {
            if (e.response.status == HttpStatusCode.Forbidden || e.response.status == HttpStatusCode.NotFound) {
                throw UserSyncResetRequiredException(
                    "Conversation $conversationId is no longer accessible: HTTP ${e.response.status.value}"
                )
            }
            throw e
        }

    /** Applies only prepared data and event payloads; the caller owns the enclosing transaction. */
    suspend fun apply(prepared: Prepared) {
        val contacts = prepared.contactRows
        val requests = prepared.requestRows
        if (contacts != null && requests != null) {
            val remoteContactIds = contacts.mapTo(HashSet(contacts.size)) { it.first.contactId }
            val staleContactIds = contactDao.selectAllContactsForSync()
                .map { it.contact_id }
                .filterNot(remoteContactIds::contains)
            contactDao.deleteContacts(staleContactIds)

            val users = contacts.map { it.second.toUserEntity() }.distinctBy { it.id }
            if (users.isNotEmpty()) userDao.insertUsers(users)
            val contactEntities = contacts.map { it.first.toEntity() }
            if (contactEntities.isNotEmpty()) contactDao.insertContacts(contactEntities)

            val remoteRequestIds = requests.mapTo(HashSet(requests.size)) { it.id }
            val staleRequestIds = contactRequestDao.selectAllContactRequestsForSync()
                .map { it.id }
                .filterNot(remoteRequestIds::contains)
            staleRequestIds.forEach { contactRequestDao.deleteContactRequest(it) }
            val requestEntities = requests.map { it.toEntity() }
            if (requestEntities.isNotEmpty()) contactRequestDao.upsertContactRequests(requestEntities)
        }

        prepared.events.forEach { event ->
            when (event) {
                is ConversationEventResponse.GroupJoinRequest -> groupJoinRequestDao.seedGroupJoinRequest(
                    GroupJoinRequestEntity(
                        id = event.requestId,
                        conversation_id = event.conversationId,
                        applicant_id = event.applicantId,
                        message = event.message,
                        status = RequestStatus.PENDING,
                        handled_by = null,
                        created_at = event.timestamp,
                        updated_at = event.timestamp,
                    )
                )
                is ConversationEventResponse.GroupJoinRequestHandled -> {
                    val seed = GroupJoinRequestEntity(
                        id = event.requestId,
                        conversation_id = event.conversationId,
                        applicant_id = event.applicantId,
                        message = null,
                        status = if (event.approved) RequestStatus.ACCEPTED else RequestStatus.REJECTED,
                        handled_by = event.handlerId,
                        created_at = event.timestamp,
                        updated_at = event.timestamp,
                    )
                    groupJoinRequestDao.upsertGroupJoinRequest(seed)
                }
                is MessageEventResponse.Read -> participantDao.updateLastReadMessage(
                    event.conversationId, event.readerId, event.messageId,
                )
                // Membership is restored from the conversation detail: inserts
                // only, so unrelated conversations and rows are never touched.
                is ConversationEventResponse.UserJoinedConversation -> {
                    val detail = requireNotNull(prepared.joinedDetails[event.conversationId]) {
                        "Missing prepared detail for joined conversation ${event.conversationId}"
                    }
                    applyJoinedConversationDetail(detail)
                }
                is ConversationEventResponse.UserLeftConversation -> {
                    participantDao.removeParticipant(event.conversationId, event.userId)
                    conversationDao.softDeleteConversation(event.conversationId, event.timestamp)
                    groupJoinRequestDao.deleteGroupJoinRequestsForConversation(event.conversationId)
                }
                // Personal preferences only: never overwrites the read cursor,
                // role, or join date of the participant row.
                is ConversationEventResponse.PersonalSettingsUpdated -> participantDao.updateSettings(
                    event.conversationId, event.userId, event.settings,
                )
                else -> Unit
            }
        }
    }

    private suspend fun applyJoinedConversationDetail(response: ConversationResponse) {
        conversationDao.insertConversation(response.toConversation().toEntity())
        response.toUserEntity()?.let { userDao.insertUser(it) }
        response.toGroupOwnerUserEntity()?.let { userDao.insertUser(it) }
        response.lastMessage?.sender?.toUserEntity()?.let { userDao.insertUser(it) }
        response.toGroupProfileEntity()?.let { groupProfileDao.insertGroupProfile(it) }
        participantDao.insertParticipant(response.toParticipantEntity())
        response.toPeerParticipantEntity()?.let { participantDao.insertParticipantIfAbsent(it) }
        response.toMessageEntity()?.let { messageDao.seedMessage(it) }
    }

    private fun requireSupportedEvent(event: RealtimeEvent) {
        when (event) {
            is ContactEventResponse,
            is ConversationEventResponse.GroupJoinRequest,
            is ConversationEventResponse.GroupJoinRequestHandled,
            is ConversationEventResponse.UserJoinedConversation,
            is ConversationEventResponse.UserLeftConversation,
            is ConversationEventResponse.PersonalSettingsUpdated -> Unit
            is MessageEventResponse.Read -> Unit
            else -> error("Unsupported USER sync event: ${event::class.simpleName}")
        }
    }
}
