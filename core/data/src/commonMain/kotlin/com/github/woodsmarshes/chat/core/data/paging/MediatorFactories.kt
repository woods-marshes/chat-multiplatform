package com.github.woodsmarshes.chat.core.data.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.RemoteMediator
import io.github.woodsmarshes.chat.db.KeyedArticlesWithAuthor
import io.github.woodsmarshes.chat.db.KeyedMessagesWithRelations
import kotlin.uuid.Uuid

/** Creates a mediator when a Pager is created, pinning that login's identity. */
@OptIn(ExperimentalPagingApi::class)
fun interface ArticleMediatorFactory {
    fun create(ownArticles: Boolean, authorId: Uuid?): RemoteMediator<Uuid, KeyedArticlesWithAuthor>
}

/** Explicit dependency: repositories do not resolve parameterized objects from Koin. */
@OptIn(ExperimentalPagingApi::class)
fun interface MessageMediatorFactory {
    fun create(ownUserId: Uuid, conversationId: Uuid, isGroup: Boolean): RemoteMediator<Uuid, KeyedMessagesWithRelations>
}
