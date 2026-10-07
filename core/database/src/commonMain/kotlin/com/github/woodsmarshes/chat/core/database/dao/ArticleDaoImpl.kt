package com.github.woodsmarshes.chat.core.database.dao

import androidx.paging.PagingSource
import com.github.woodsmarshes.chat.core.database.session.SessionBoundPagingSource
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
import com.github.woodsmarshes.chat.core.database.di.BoundDatabaseElement
import com.github.woodsmarshes.chat.core.database.session.DatabaseSessionGate
import com.github.woodsmarshes.chat.core.database.session.DirectDatabaseSessionGate
import com.github.woodsmarshes.chat.core.database.session.sessionBoundQueryFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.CoroutineContext
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ArticleDaoImpl(
    private val dbProvider: () -> ChatDatabase,
    private val ioContext: CoroutineContext,
    private val sessionGate: DatabaseSessionGate = DirectDatabaseSessionGate(dbProvider),
) : ArticleDao {
    private suspend fun writeQueries() = (currentCoroutineContext()[BoundDatabaseElement]?.database ?: dbProvider()).also { currentCoroutineContext().ensureActive() }.articleQueries

    fun pinForPaging(): ArticleDaoImpl {
        val db = dbProvider()
        return ArticleDaoImpl({ db }, ioContext, com.github.woodsmarshes.chat.core.database.session.PinnedDatabaseSessionGate(db, sessionGate))
    }

    private val queries
        get() = dbProvider().articleQueries

    override suspend fun upsert(article: Article) {
        writeQueries().upsertArticle(article)
    }

    override suspend fun upsertAll(articles: List<Article>) {
        if (articles.isEmpty()) return
        writeQueries().transaction {
            articles.forEach { upsert(it) }
        }
    }

    override fun getById(id: Uuid): Flow<Article?> {
        return sessionBoundQueryFlow(sessionGate, ioContext, { db -> db.articleQueries.getArticleById(id) }, { it.executeAsOneOrNull() })
    }

    override fun getByIdWithAuthor(id: Uuid): Flow<GetArticleByIdWithAuthor?> {
        return sessionBoundQueryFlow(sessionGate, ioContext, { db -> db.articleQueries.getArticleByIdWithAuthor(id) }, { it.executeAsOneOrNull() })
    }

    override fun listAll(
        offset: Long,
        limit: Int
    ): Flow<List<ListAllArticlesWithAuthor>> {
        return sessionBoundQueryFlow(sessionGate, ioContext, { db -> db.articleQueries.listAllArticlesWithAuthor(limit = limit.toLong(), offset = offset) }, { it.executeAsList() })
    }

    override fun listByAuthor(
        authorId: Uuid,
        offset: Long,
        limit: Int
    ): Flow<List<ListArticlesByAuthorWithAuthor>> {
        return sessionBoundQueryFlow(sessionGate, ioContext, { db -> db.articleQueries.listArticlesByAuthorWithAuthor(
            author_id = authorId,
            limit = limit.toLong(),
            offset = offset
        ) }, { it.executeAsList() })
    }

    override fun listByAuthorAndStatus(
        authorId: Uuid,
        status: ArticleStatus,
        offset: Long,
        limit: Int
    ): Flow<List<ListArticlesByAuthorAndStatusWithAuthor>> {
        return sessionBoundQueryFlow(sessionGate, ioContext, { db -> db.articleQueries.listArticlesByAuthorAndStatusWithAuthor(
            author_id = authorId,
            status = status,
            limit = limit.toLong(),
            offset = offset
        ) }, { it.executeAsList() })
    }

    override fun pagingSource(
        pageSize: Long,
        authorId: Uuid?,
        status: ArticleStatus?,
    ): PagingSource<Uuid, KeyedArticlesWithAuthor> {
        // The status is bound as the enum (SQLDelight encodes it as its name) and cast to
        // text in the query, which keeps the "no filter" case expressible as a null parameter.
        return SessionBoundPagingSource(sessionGate, sessionGate.boundDatabase, sessionGate.currentGeneration) { db ->
        val queries = db.articleQueries
        QueryPagingSource(
            transacter = queries,
            context = ioContext,
            pageBoundariesProvider = { anchorId, limit ->
                queries.articleBoundaries(
                    limit = limit,
                    referenceId = anchorId,
                    authorId = authorId,
                    status = status,
                )
            },
            queryProvider = { beginInclusive, endExclusive ->
                queries.keyedArticlesWithAuthor(
                    beginInclusive = beginInclusive,
                    endExclusive = endExclusive,
                    authorId = authorId,
                    status = status,
                )
            }
        )
        }
    }

    override suspend fun softDelete(id: Uuid, deletedAt: Instant) {
        writeQueries().softDeleteArticle(
            deleted_at = deletedAt,
            id = id
        )
    }

    override suspend fun hardDelete(id: Uuid) {
        writeQueries().hardDeleteArticle(id)
    }
}
