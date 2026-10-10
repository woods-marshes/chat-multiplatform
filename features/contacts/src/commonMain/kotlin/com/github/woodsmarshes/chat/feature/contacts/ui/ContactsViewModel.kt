package com.github.woodsmarshes.chat.feature.contacts.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.michaelbull.result.onErr
import com.github.michaelbull.result.onOk
import com.github.woodsmarshes.chat.core.data.repository.ContactRepository
import com.github.woodsmarshes.chat.core.data.repository.ConversationRepository
import com.github.woodsmarshes.chat.core.data.repository.UserRepository
import com.github.woodsmarshes.chat.core.model.RequestStatus
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.ui.ContactUiModel
import com.github.woodsmarshes.chat.core.ui.resources.getLocaleStrings
import com.github.woodsmarshes.chat.feature.contacts.model.ContactsUiState
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

class ContactsViewModel(
    private val contactRepository: ContactRepository,
    private val userRepository: UserRepository,
    private val conversationRepository: ConversationRepository? = null,
) : ViewModel() {

    private val strings
        get() = getLocaleStrings()

    private val log = KotlinLogging.logger {}

    private val _uiState = MutableStateFlow(ContactsUiState())
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var observeFriendsJob: Job? = null
    private var lastSearchQuery: String = ""

    /**
     * Groups whose detail fetch already failed once (sent requests to groups I
     * have not joined answer 403 NotParticipant). Without this, every ledger
     * emission would re-fire a doomed request; after approval the profile
     * arrives through the conversation sync and resolves via the cache path.
     */
    private val attemptedGroupNameLookups = mutableSetOf<Uuid>()

    init {
        observeBlockedContacts()
        observeFriendRequests()
        observeGroupNotifications()
        refresh()
    }

    private fun observeFriends() {
        observeFriendsJob?.cancel()
        observeFriendsJob = viewModelScope.launch {
            contactRepository.getFriendsFlow()
                .onStart { _uiState.update { it.copy(isLoading = it.contacts.isEmpty(), error = null) } }
                .catch { e ->
                    log.error(e) { "[Contacts] loading friends failed" }
                    _uiState.update { it.copy(isLoading = false, error = strings.loadFailed) }
                }
                .collect { pairs ->
                    val contacts = pairs.map { (contact, user) ->
                        ContactUiModel(
                            id = user.id,
                            username = user.username,
                            displayName = contact.alias?.takeIf { it.isNotBlank() }
                                ?: contact.nickname?.takeIf { it.isNotBlank() }
                                ?: user.displayName,
                            avatarUrl = user.avatarUrl,
                            bio = user.bio,
                        )
                    }
                    _uiState.update { it.copy(contacts = contacts, isLoading = false, error = null) }
                }
        }
    }

    private fun observeBlockedContacts() {
        viewModelScope.launch {
            contactRepository.getBlockedContactsFlow()
                .catch { e -> log.warn(e) { "[Contacts] observing blocked contacts failed" } }
                .collect { pairs ->
                    val blocked = pairs.map { (contact, user) ->
                        ContactUiModel(
                            id = user.id,
                            username = user.username,
                            displayName = contact.alias?.takeIf { it.isNotBlank() }
                                ?: contact.nickname?.takeIf { it.isNotBlank() }
                                ?: user.displayName,
                            avatarUrl = user.avatarUrl,
                            bio = user.bio,
                        )
                    }
                    _uiState.update { it.copy(blockedContacts = blocked) }
                }
        }
    }

    private fun observeFriendRequests() {
        viewModelScope.launch {
            combine(
                contactRepository.observeAllRequests(),
                userRepository.getMeFlow(),
            ) { requests, me ->
                requests to me
            }.catch { e ->
                log.warn(e) { "[Contacts] observing friend requests failed" }
            }.collect { (requests, me) ->
                val myId = me?.id
                val received = if (myId != null) {
                    requests.filter { it.receiverId == myId }
                } else {
                    emptyList()
                }
                val sent = if (myId != null) {
                    requests.filter { it.senderId == myId }
                } else {
                    emptyList()
                }
                val pendingCount = received.count { it.status == RequestStatus.PENDING }
                _uiState.update {
                    it.copy(
                        receivedRequests = received,
                        sentRequests = sent,
                        pendingReceivedCount = pendingCount,
                    )
                }
                val userIdsToFetch = (received.map { it.senderId } + sent.map { it.receiverId })
                    .distinct()
                    .filter { it !in _uiState.value.requestUsers }
                if (userIdsToFetch.isNotEmpty()) {
                    val resolved = mutableMapOf<Uuid, User>()
                    userIdsToFetch.forEach { uid ->
                        userRepository.fetchUserDetail(uid).onOk { u ->
                            resolved[uid] = u
                        }
                    }
                    if (resolved.isNotEmpty()) {
                        _uiState.update { it.copy(requestUsers = it.requestUsers + resolved) }
                    }
                }
            }
        }
    }

    fun refresh() {
        observeFriends()
        viewModelScope.launch {
            try {
                contactRepository.syncFriends()
                contactRepository.syncContactRequests()
                refreshGroupNotifications()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                log.warn(e) { "[Contacts] syncFriends failed" }
                if (_uiState.value.contacts.isEmpty()) {
                    _uiState.update { it.copy(isLoading = false, error = strings.loadFailed) }
                }
            }
        }
    }

    /**
     * The local request ledger is the display source of truth: realtime
     * events (new request, approval decision) land in the database and these
     * flows push the lists and badge into the UI without any polling. The
     * HTTP fetch in [refreshGroupNotifications] only seeds/refreshes the
     * ledger.
     */
    private fun observeGroupNotifications() {
        val convRepo = conversationRepository ?: return
        viewModelScope.launch {
            convRepo.observeIncomingGroupRequests().collect { incoming ->
                _uiState.update { state ->
                    state.copy(
                        incomingGroupRequests = incoming,
                        pendingGroupRequestCount = incoming.count { it.status == RequestStatus.PENDING },
                    )
                }
                incoming.map { it.applicantId }.distinct()
                    .filter { it !in _uiState.value.requestUsers }
                    .forEach { uid ->
                        viewModelScope.launch {
                            userRepository.fetchUserDetail(uid).onOk { u ->
                                _uiState.update { it.copy(requestUsers = it.requestUsers + (uid to u)) }
                            }
                        }
                    }
                incoming.map { it.conversationId }.distinct()
                    .filter { it !in _uiState.value.groupNames }
                    .forEach { cid ->
                        viewModelScope.launch { resolveGroupName(convRepo, cid) }
                    }
            }
        }
        viewModelScope.launch {
            convRepo.observeSentGroupRequests().collect { sent ->
                _uiState.update { it.copy(sentGroupRequests = sent) }
                sent.map { it.conversationId }.distinct()
                    .filter { it !in _uiState.value.groupNames }
                    .forEach { cid ->
                        viewModelScope.launch { resolveGroupName(convRepo, cid) }
                    }
            }
        }
    }

    private suspend fun resolveGroupName(convRepo: ConversationRepository, conversationId: Uuid) {
        val cached = convRepo.getGroupProfileFlow(conversationId).firstOrNull()
        if (cached != null) {
            _uiState.update { it.copy(groupNames = it.groupNames + (conversationId to cached.name)) }
            return
        }
        // Best effort, one network attempt per group per screen lifetime.
        if (conversationId in attemptedGroupNameLookups) return
        attemptedGroupNameLookups += conversationId
        convRepo.refreshGroupDetail(conversationId)
        convRepo.getGroupProfileFlow(conversationId).firstOrNull()?.let { profile ->
            _uiState.update { it.copy(groupNames = it.groupNames + (conversationId to profile.name)) }
        }
    }

    fun refreshGroupNotifications() {
        val convRepo = conversationRepository ?: return
        if (_uiState.value.isRefreshingGroupNotifications) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingGroupNotifications = true, groupNotificationsError = null) }
            val incomingRes = convRepo.getIncomingGroupRequests(status = null)
            val sentRes = convRepo.getSentGroupRequests(status = null)
            // A failed refresh must be distinguishable from "no requests" —
            // surface the error with a retry. The lists themselves are owned by
            // the ledger flows and update as soon as the fetch persists.
            if (incomingRes.isErr && sentRes.isErr) {
                _uiState.update {
                    it.copy(
                        isRefreshingGroupNotifications = false,
                        groupNotificationsError = strings.loadFailed,
                    )
                }
                return@launch
            }
            _uiState.update { it.copy(isRefreshingGroupNotifications = false) }
        }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(actionMessage = null) }
    }

    // ---------------- New Friends ----------------

    fun showNewFriends() {
        _uiState.update { it.copy(showNewFriendsSheet = true) }
        viewModelScope.launch {
            runCatching { contactRepository.syncContactRequests() }
        }
    }

    fun dismissNewFriends() {
        _uiState.update { it.copy(showNewFriendsSheet = false) }
    }

    fun onNewFriendsTabSelected(index: Int) {
        _uiState.update { it.copy(newFriendsTabIndex = index) }
    }

    fun handleFriendRequest(requestId: Uuid, approve: Boolean) {
        viewModelScope.launch {
            contactRepository.handleFriendRequest(requestId, approve).onOk {
                contactRepository.syncFriends()
                contactRepository.syncContactRequests()
            }.onErr {
                _uiState.update { it.copy(actionMessage = strings.authOperationFailed) }
            }
        }
    }

    fun cancelFriendRequest(requestId: Uuid) {
        viewModelScope.launch {
            contactRepository.cancelFriendRequest(requestId).onOk {
                contactRepository.syncContactRequests()
            }.onErr {
                _uiState.update { it.copy(actionMessage = strings.authOperationFailed) }
            }
        }
    }

    // ---------------- Group Notifications ----------------

    fun showGroupNotifications() {
        _uiState.update { it.copy(showGroupNotificationsSheet = true) }
        refreshGroupNotifications()
    }

    fun dismissGroupNotifications() {
        _uiState.update { it.copy(showGroupNotificationsSheet = false) }
    }

    fun onGroupNotificationsTabSelected(index: Int) {
        _uiState.update { it.copy(groupNotificationsTabIndex = index) }
    }

    fun handleGroupJoinRequest(conversationId: Uuid, requestId: Uuid, approve: Boolean) {
        val convRepo = conversationRepository ?: return
        viewModelScope.launch {
            convRepo.handleGroupJoinRequest(conversationId, requestId, approve).onOk {
                refreshGroupNotifications()
            }.onErr {
                _uiState.update { it.copy(actionMessage = strings.authOperationFailed) }
            }
        }
    }

    // ---------------- Blocked Users ----------------

    fun showBlockedUsers() {
        _uiState.update { it.copy(showBlockedUsersSheet = true) }
    }

    fun dismissBlockedUsers() {
        _uiState.update { it.copy(showBlockedUsersSheet = false) }
    }

    fun unblockUser(userId: Uuid) {
        viewModelScope.launch {
            contactRepository.unblockUser(userId).onOk {
                contactRepository.syncFriends()
            }.onErr {
                _uiState.update { it.copy(actionMessage = strings.authOperationFailed) }
            }
        }
    }

    // ---------------- In-place search ----------------

    fun onSearchQuery(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            lastSearchQuery = ""
            _uiState.update {
                it.copy(
                    searchResults = emptyList(),
                    isSearching = false,
                    searchError = null,
                )
            }
            return
        }
        lastSearchQuery = query
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, searchError = null) }
            userRepository.searchUsers(query).onOk { users ->
                _uiState.update {
                    it.copy(
                        searchResults = users.map { user ->
                            ContactUiModel(
                                id = user.id,
                                username = user.username,
                                displayName = user.displayName,
                                avatarUrl = user.avatarUrl,
                                bio = user.bio,
                            )
                        },
                        isSearching = false,
                    )
                }
            }.onErr {
                _uiState.update { it.copy(isSearching = false, searchError = strings.searchFailed) }
            }
        }
    }

    /** Re-runs the search for the last query (error retry affordance). */
    fun retrySearch() {
        if (lastSearchQuery.isNotBlank()) {
            onSearchQuery(lastSearchQuery)
        }
    }
}

