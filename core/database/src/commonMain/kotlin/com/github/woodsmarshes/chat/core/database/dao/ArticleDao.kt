package com.github.woodsmarshes.chat.core.database.dao

import androidx.paging.PagingSource
import com.github.woodsmarshes.chat.core.model.ArticleStatus
import io.github.woodsmarshes.chat.db.Article
import io.github.woodsmarshes.chat.db.GetArticleByIdWithAuthor
import io.github.woodsmarshes.chat.db.KeyedArticlesWithAuthor
import io.github.woodsmarshes.chat.db.ListAllArticlesWithAuthor
import io.github.woodsmarshes.chat.db.ListArticlesByAuthorAndStatusWithAuthor
import io.github.woodsmarshes.chat.db.ListArticlesByAuthorWithAuthor
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface ArticleDao {
    // 写入与同步
    suspend fun upsert(article: Article)
    suspend fun upsertAll(articles: List<Article>)

    // 查询 - 基础（仅 Article 字段，用于内部操作）
    fun getById(id: Uuid): Flow<Article?>
    // 查询 - 单篇带作者信息
    fun getByIdWithAuthor(id: Uuid): Flow<GetArticleByIdWithAuthor?>

    // 查询 - 已发布文章列表（JOIN 作者信息，供 UI 展示）
    fun listAll(offset: Long = 0, limit: Int = 50): Flow<List<ListAllArticlesWithAuthor>>

    // 查询 - 某人全部文章（JOIN 作者信息）
    fun listByAuthor(
        authorId: Uuid,
        offset: Long = 0,
        limit: Int = 50
    ): Flow<List<ListArticlesByAuthorWithAuthor>>

    // 查询 - 某人按状态过滤（JOIN 作者信息）
    fun listByAuthorAndStatus(
        authorId: Uuid,
        status: ArticleStatus,
        offset: Long = 0,
        limit: Int = 50
    ): Flow<List<ListArticlesByAuthorAndStatusWithAuthor>>

    // 分页（authorId 为 null 则查全量）
    fun pagingSource(pageSize: Long, authorId: Uuid? = null): PagingSource<Uuid, KeyedArticlesWithAuthor>

    // 删除逻辑
    suspend fun softDelete(id: Uuid, deletedAt: Instant)
    suspend fun hardDelete(id: Uuid)
}
