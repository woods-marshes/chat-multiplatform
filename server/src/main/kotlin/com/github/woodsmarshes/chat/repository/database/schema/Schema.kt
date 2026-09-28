package com.github.woodsmarshes.chat.repository.database.schema

import org.jetbrains.exposed.v1.core.Table

/**
 * Every table the server owns, in creation order (children may reference
 * parents). configureSchema creates from this list and clearDatabaseData
 * drops it in reverse — adding a table here is what wires both, so the two
 * lists can never drift apart again.
 */
val ALL_SCHEMA_TABLES: List<Table> = listOf(
    Users,
    Conversations,
    UserSettings,
    GroupProfiles,
    Messages,
    GroupJoinRequests,
    ConversationParticipants,
    Contacts,
    ContactRequests,
    Articles,
    YjsDocuments,
)
