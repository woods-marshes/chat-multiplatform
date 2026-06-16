package com.github.woodsmarshes.chat.core.database.dao

import androidx.paging.PagingSource
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import app.cash.sqldelight.paging3.QueryPagingSource
import com.github.woodsmarshes.chat.core.model.ArticleStatus
import io.github.woodsmarshes.chat.db.Article
import io.github.woodsmarshes.chat.db.ChatDatabase
import io.github.woodsmarshes.chat.db.GetArticleByIdWithAuthor
import io.github.woodsmarshes.chat.db.KeyedArticlesWithAuthor
import io.github.woodsmarshes.chat.db.ListAllArticlesWithAuthor
import io.github.woodsmarshes.chat.db.ListArticlesByAuthorAndStatusWithAuthor
import io.github.woodsmarshes.chat.db.ListArticlesByAuthorWithAuthor
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.CoroutineContext
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ArticleDaoImpl(
    private val dbProvider: () -> ChatDatabase,
    private val ioContext: CoroutineContext,
) : ArticleDao {
    private val queries
        get() = dbProvider().articleQueries

    override suspend fun upsert(article: Article) {
        queries.upsertArticle(article)
    }

    override suspend fun upsertAll(articles: List<Article>) {
        if (articles.isEmpty()) return
        queries.transaction {
            articles.forEach { upsert(it) }
        }
    }

    override fun getById(id: Uuid): Flow<Article?> {
        return queries.getArticleById(id)
            .asFlow()
            .mapToOneOrNull(ioContext)
    }

    override fun getByIdWithAuthor(id: Uuid): Flow<GetArticleByIdWithAuthor?> {
        return queries.getArticleByIdWithAuthor(id)
            .asFlow()
            .mapToOneOrNull(ioContext)
    }

    override fun listAll(
        offset: Long,
        limit: Int
    ): Flow<List<ListAllArticlesWithAuthor>> {
        return queries.listAllArticlesWithAuthor(limit = limit.toLong(), offset = offset)
            .asFlow()
            .mapToList(ioContext)
    }

    override fun listByAuthor(
        authorId: Uuid,
        offset: Long,
        limit: Int
    ): Flow<List<ListArticlesByAuthorWithAuthor>> {
        return queries.listArticlesByAuthorWithAuthor(
            author_id = authorId,
            limit = limit.toLong(),
            offset = offset
        )
            .asFlow()
            .mapToList(ioContext)
    }

    override fun listByAuthorAndStatus(
        authorId: Uuid,
        status: ArticleStatus,
        offset: Long,
        limit: Int
    ): Flow<List<ListArticlesByAuthorAndStatusWithAuthor>> {
        return queries.listArticlesByAuthorAndStatusWithAuthor(
            author_id = authorId,
            status = status,
            limit = limit.toLong(),
            offset = offset
        )
            .asFlow()
            .mapToList(ioContext)
    }

    override fun pagingSource(pageSize: Long, authorId: Uuid?): PagingSource<Uuid, KeyedArticlesWithAuthor> {
        return QueryPagingSource(
            transacter = queries,
            context = ioContext,
            pageBoundariesProvider = { anchorId, limit ->
                queries.articleBoundaries(
                    limit = limit,
                    referenceId = anchorId ?: Uuid.NIL,
                    authorId = authorId,
                )
            },
            queryProvider = { beginInclusive, endExclusive ->
                queries.keyedArticlesWithAuthor(
                    beginInclusive = beginInclusive,
                    endExclusive = endExclusive,
                    authorId = authorId,
                )
            }
        )
    }

    override suspend fun softDelete(id: Uuid, deletedAt: Instant) {
        queries.softDeleteArticle(
            deleted_at = deletedAt,
            id = id
        )
    }

    override suspend fun hardDelete(id: Uuid) {
        queries.hardDeleteArticle(id)
    }
}
