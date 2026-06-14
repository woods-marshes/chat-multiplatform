package com.github.woodsmarshes.chat.repository

import com.github.woodsmarshes.chat.core.model.*
import com.github.woodsmarshes.chat.core.model.Article
import com.github.woodsmarshes.chat.repository.database.schema.ContactRequests
import com.github.woodsmarshes.chat.repository.database.schema.Contacts
import com.github.woodsmarshes.chat.repository.database.schema.ConversationParticipants
import com.github.woodsmarshes.chat.repository.database.schema.Conversations
import com.github.woodsmarshes.chat.repository.database.schema.GroupJoinRequests
import com.github.woodsmarshes.chat.repository.database.schema.GroupProfiles
import com.github.woodsmarshes.chat.repository.database.schema.Messages
import com.github.woodsmarshes.chat.repository.database.schema.UserSettings
import com.github.woodsmarshes.chat.repository.database.schema.Users
import com.github.woodsmarshes.chat.repository.database.schema.Articles
import org.jetbrains.exposed.v1.core.ResultRow
import kotlin.time.Clock

// User 相关扩展
fun ResultRow.toUser(): User = User(
    id = this[Users.id].value,
    username = this[Users.username],
    email = this[Users.email],
    displayName = this[Users.displayName],
    avatarUrl = this[Users.avatarUrl],
    bio = this[Users.bio],
    createdAt = this[Users.createdAt],
    updatedAt = this[Users.updatedAt],
    deletedAt = this[Users.deletedAt],
    role = this[Users.role]
)

fun ResultRow.toUserAuthInfo(): UserAuthInfo = UserAuthInfo(
    userId = this[Users.id].value,
    passwordHash = this[Users.passwordHash],
    salt = this[Users.salt],
    domainUser = toUser()
)

// Conversation 相关扩展
fun ResultRow.toConversation(): Conversation = Conversation(
    id = this[Conversations.id].value,
    type = this[Conversations.type],
    metadata = this[Conversations.metadata],
    createdAt = this[Conversations.createdAt],
    updatedAt = this[Conversations.updatedAt],
    deletedAt = this[Conversations.deletedAt],
    lastMessageId = this[Conversations.lastMessageId],
)

// ConversationParticipant 相关扩展
fun ResultRow.toConversationParticipant(): ConversationParticipant = ConversationParticipant(
    conversationId = this[ConversationParticipants.conversationId].value,
    userId = this[ConversationParticipants.userId].value,
    role = this[ConversationParticipants.role],
    lastReadMessageId = this[ConversationParticipants.lastReadMessageId],
    joinedAt = this[ConversationParticipants.joinedAt],
    mutedUntil = this[ConversationParticipants.mutedUntil],
    settings = this[ConversationParticipants.settings]
)

// GroupProfile 相关扩展
fun ResultRow.toGroupProfile(): GroupProfile = GroupProfile(
    conversationId = this[GroupProfiles.conversationId].value,
    name = this[GroupProfiles.name],
    handle = this[GroupProfiles.handle],
    description = this[GroupProfiles.description],
    avatarUrl = this[GroupProfiles.avatarUrl],
    ownerId = this[GroupProfiles.ownerId].value,
    settings = this[GroupProfiles.settings],
    createdAt = this[GroupProfiles.createdAt],
    updatedAt = this[GroupProfiles.updatedAt]
)

// UserSetting 相关扩展
fun ResultRow.toUserSetting(): UserSetting = UserSetting(
    userId = this[UserSettings.userId].value,
    privacy = PrivacySetting(
        allowSearch = this[UserSettings.allowSearch],
        friendRequestPolicy = this[UserSettings.friendRequestPolicy],
        showOnlineStatus = this[UserSettings.showOnlineStatus],
        profileVisibility = this[UserSettings.profileVisibility],
        allowStrangerChat = this[UserSettings.allowStrangerChat],
    ),
    preferences = UserPreference(
        themeBrand = this[UserSettings.preferences].themeBrand,
        notificationSound = this[UserSettings.preferences].notificationSound,
        darkThemeConfig = this[UserSettings.preferences].darkThemeConfig,
        useDynamicColor = this[UserSettings.preferences].useDynamicColor,
        shouldHideOnboarding = this[UserSettings.preferences].shouldHideOnboarding,
    ),
    updatedAt = this[UserSettings.updatedAt]
)

// Message 相关扩展
fun ResultRow.toMessage(user: SimpleUser? = null, replyTo: Message? = null): Message = Message(
    id = this[Messages.id].value,
    conversationId = this[Messages.conversationId].value,
    sender = user,
    category = this[Messages.category],
    content = this[Messages.content],
    replyTo = replyTo,
    createdAt = this[Messages.createdAt],
    revokedAt = this[Messages.revokedAt],
    senderContext = null
)

fun ResultRow.toArticle(user: SimpleUser? = null): Article = Article(
    id = this[Articles.id].value,
    title = this[Articles.title],
    content = this[Articles.content],
    author = user ?: toSimpleUser(),
    status = this[Articles.status],
    excerpt = this[Articles.excerpt],
    createdAt = this[Articles.createdAt],
    updatedAt = this[Articles.updatedAt],
    publishedAt = this[Articles.publishedAt],
)

fun ResultRow.toSimpleUser() = SimpleUser(
    id = this[Users.id].value,
    username = this[Users.username],
    displayName = this[Users.displayName],
    avatarUrl = this[Users.avatarUrl],
    createdAt = this[Users.createdAt],
    updatedAt = this[Users.updatedAt],
    deletedAt = this[Users.deletedAt],
    role = this[Users.role],
)

fun ResultRow.toMessageSenderContext() = MessageSenderContext(
    conversationRole = this.getOrNull(ConversationParticipants.role) ?: ConversationRole.UNKNOWN,
    participantSettings = this.getOrNull(ConversationParticipants.settings)?.copy(
        alias = null,
        backgroundImage = null,
        enableNotification = true,
        pinnedAt = null
    ),
    joinedAt = this.getOrNull(ConversationParticipants.joinedAt) ?: Clock.System.now(),
    lastReadMessageId = this.getOrNull(ConversationParticipants.lastReadMessageId),
    mutedUntil = this.getOrNull(ConversationParticipants.mutedUntil)
)
fun ResultRow.toFilteredUser(replyTo: Message? = null): Message = Message(
    id = this[Messages.id].value,
    conversationId = this[Messages.conversationId].value,
    sender = toSimpleUser(),
    category = this[Messages.category],
    content = this[Messages.content],
    replyTo = replyTo,
    createdAt = this[Messages.createdAt],
    revokedAt = this[Messages.revokedAt],
    senderContext = toMessageSenderContext(),
)

fun ResultRow.toContact() = Contact(
    userId = this[Contacts.userId].value,
    contactId = this[Contacts.contactId].value,
    status = this[Contacts.status],
    nickname = this[Contacts.nickname],
    alias = this[Contacts.alias],
    createdAt = this[Contacts.createdAt],
    updatedAt = this[Contacts.updatedAt]
)

fun ResultRow.toContactRequest() = ContactRequest(
    id = this[ContactRequests.id].value,
    senderId = this[ContactRequests.senderId].value,
    receiverId = this[ContactRequests.receiverId].value,
    message = this[ContactRequests.message],
    status = this[ContactRequests.status],
    createdAt = this[ContactRequests.createdAt],
    updatedAt = this[ContactRequests.updatedAt]
)

fun ResultRow.toGroupJoinRequest() = GroupJoinRequest(
    id = this[GroupJoinRequests.id].value,
    conversationId = this[GroupJoinRequests.conversationId].value,
    applicantId = this[GroupJoinRequests.applicantId].value,
    handledById = this[GroupJoinRequests.handledById]?.value,
    message = this[GroupJoinRequests.message],
    status = this[GroupJoinRequests.status],
    createdAt = this[GroupJoinRequests.createdAt],
    updatedAt = this[GroupJoinRequests.updatedAt]
)
