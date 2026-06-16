package com.github.woodsmarshes.chat.repository

import com.github.woodsmarshes.chat.core.model.Article
import com.github.woodsmarshes.chat.core.model.ArticleStatus
import com.github.woodsmarshes.chat.repository.database.schema.Articles
import com.github.woodsmarshes.chat.repository.database.schema.Users
import com.github.woodsmarshes.chat.utils.dbQuery
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.coalesce
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.datetime.timestampParam
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.uuid.Uuid

interface ArticleRepository {
    suspend fun create(
        authorId: Uuid,
        title: String? = "",
        content: JsonElement,
        status: ArticleStatus,
        excerpt: String?,
        coverImage: String? = null,
    ): Article?

    suspend fun getById(id: Uuid): Article?

    suspend fun listAll(
        offset: Long,
        limit: Int,
        userId: Uuid? = null,
    ): List<Article>

    suspend fun update(
        id: Uuid,
        title: String?,
        content: JsonElement?,
        status: ArticleStatus?,
        excerpt: String?,
        coverImage: String? = null,
    ): Boolean

    suspend fun save(
        existing: Article?,
        id: Uuid,
        userId: Uuid,
        title: String?,
        content: JsonElement?,
        status: ArticleStatus,
        excerpt: String?,
        coverImage: String? = null,
    ): Article?

    suspend fun delete(id: Uuid): Boolean
}

class ArticleDataSourceImpl : ArticleRepository {

    override suspend fun create(
        authorId: Uuid,
        title: String?,
        content: JsonElement,
        status: ArticleStatus,
        excerpt: String?,
        coverImage: String?,
    ): Article? = dbQuery {
        val now = Clock.System.now()
        Articles.insert {
            it[this.title] = title ?: ""
            it[this.content] = content
            it[this.authorId] = authorId
            it[this.status] = status
            it[this.excerpt] = excerpt
            it[this.createdAt] = now
            it[this.updatedAt] = now
            it[this.coverImage] = coverImage
        }
            .resultedValues
            ?.singleOrNull()
            ?.toArticle(
                Users
                    .selectAll()
                    .where { Users.id eq authorId }
                    .map{ it.toSimpleUser() }
                    .singleOrNull()
            )
    }

    override suspend fun getById(id: Uuid): Article? = dbQuery {
        (Articles innerJoin Users)
            .selectAll()
            .where {
                Articles.id eq id
            }
            .map { it.toArticle() }
            .singleOrNull()
    }

    override suspend fun listAll(
        offset: Long,
        limit: Int,
        userId: Uuid?,
    ): List<Article> = dbQuery {
        return@dbQuery if (userId == null) {
            (Articles innerJoin Users)
                .selectAll()
                .where {
                    Articles.deletedAt.isNull() and
                            (Articles.status eq ArticleStatus.PUBLISHED)
                }
                .orderBy(Articles.createdAt, SortOrder.DESC)
                .limit(limit)
                .offset(offset)
                .map { it.toArticle() }
        } else {
            (Articles innerJoin Users)
                .selectAll()
                .where {
                    (Articles.authorId eq userId) and
                            (Articles.deletedAt.isNull())
                }
                .orderBy(Articles.createdAt, SortOrder.DESC)
                .limit(limit)
                .offset(offset)
                .map { it.toArticle() }
        }
    }

    override suspend fun update(
        id: Uuid,
        title: String?,
        content: JsonElement?,
        status: ArticleStatus?,
        excerpt: String?,
        coverImage: String?,
    ): Boolean = dbQuery {
        val now = Clock.System.now()
        Articles.update(where = { Articles.id eq id }) {
            if (title != null) it[this.title] = title
            if (content != null) it[this.content] = content
            if (status != null) it[this.status] = status
            if (excerpt != null) it[this.excerpt] = excerpt
            it[this.updatedAt] = now
            if (status == ArticleStatus.PUBLISHED) {
                it[Articles.publishedAt] = coalesce(
                    Articles.publishedAt,
                    timestampParam(now)
                )
            }
        } > 0
    }

    override suspend fun save(
        existing: Article?,
        id: Uuid,
        userId: Uuid,
        title: String?,
        content: JsonElement?,
        status: ArticleStatus,
        excerpt: String?,
        coverImage: String?,
    ): Article? = dbQuery {
        val now = Clock.System.now()

        if (existing != null) {
            Articles.update(where = { Articles.id eq id }) {
                it[this.status] = status
                it[this.updatedAt] = now
                if (content != null) it[this.content] = content
                if (title != null) it[this.title] = title
                if (excerpt != null) it[this.excerpt] = excerpt
                if (excerpt != null) it[this.coverImage] = coverImage
                if (status == ArticleStatus.PUBLISHED) {
                    it[Articles.publishedAt] = existing.publishedAt ?: now
                }
            }
        } else {
            if (content == null) return@dbQuery null
            val pubAt = if (status == ArticleStatus.PUBLISHED) now else null
            Articles.insert {
                it[this.id] = id
                it[this.title] = title ?: ""
                it[this.content] = content
                it[this.authorId] = userId
                it[this.status] = status
                it[this.excerpt] = excerpt
                it[this.createdAt] = now
                it[this.updatedAt] = now
                it[this.publishedAt] = pubAt
                it[this.coverImage] = coverImage
            }
        }

        (Articles innerJoin Users)
            .selectAll()
            .where { Articles.id eq id }
            .map { it.toArticle() }
            .singleOrNull()
    }

    override suspend fun delete(id: Uuid): Boolean = dbQuery {
        Articles.update(where = { Articles.id eq id }) {
            it[this.deletedAt] = Clock.System.now()
        } > 0
    }
}
