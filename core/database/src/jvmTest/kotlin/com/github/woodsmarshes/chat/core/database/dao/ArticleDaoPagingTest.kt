package com.github.woodsmarshes.chat.core.database.dao

import androidx.paging.PagingSource
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.github.woodsmarshes.chat.core.database.di.createDatabase
import com.github.woodsmarshes.chat.core.model.ArticleStatus
import com.github.woodsmarshes.chat.core.model.UserRole
import io.github.woodsmarshes.chat.db.Article
import io.github.woodsmarshes.chat.db.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * The paged article list reads from the local cache, which also holds the drafts of the
 * signed-in user. The "all" tab must show what the server publishes, so the status filter has
 * to be part of the paging query, not only of the remote page.
 */
class ArticleDaoPagingTest {

    private val authorId = Uuid.parse("00000000-0000-0000-0000-0000000000a1")
    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)

    @Test
    fun publishedFilterExcludesDraftsFromThePagedList() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val database = createDatabase { schema ->
            schema.create(driver).await()
            driver
        }
        val dao = ArticleDaoImpl(dbProvider = { database }, ioContext = Dispatchers.IO)
        val userDao = UserDaoImpl(dbProvider = { database }, ioContext = Dispatchers.IO)

        userDao.insertUser(
            UserEntity(
                id = authorId,
                username = "author",
                email = null,
                display_name = null,
                avatar = null,
                bio = null,
                created_at = now,
                updated_at = now,
                deleted_at = null,
                role = UserRole.MEMBER,
            )
        )
        database.articleQueries.upsertArticle(article("000000000001", ArticleStatus.PUBLISHED))
        database.articleQueries.upsertArticle(article("000000000002", ArticleStatus.DRAFT))
        database.articleQueries.upsertArticle(article("000000000003", ArticleStatus.PUBLISHED))

        val unfiltered = dao.pagingSource(pageSize = 10).loadRefresh()
        assertEquals(
            listOf("0003", "0002", "0001"),
            unfiltered.map { it.id.toString().takeLast(4) },
            "without a status filter every cached article is paged",
        )

        val published = dao.pagingSource(pageSize = 10, status = ArticleStatus.PUBLISHED).loadRefresh()
        assertEquals(
            listOf("0003", "0001"),
            published.map { it.id.toString().takeLast(4) },
            "the published list must not contain drafts",
        )

        driver.close()
    }

    @Test
    fun authorFilterStillAppliesTogetherWithTheStatusFilter() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val database = createDatabase { schema ->
            schema.create(driver).await()
            driver
        }
        val dao = ArticleDaoImpl(dbProvider = { database }, ioContext = Dispatchers.IO)
        val userDao = UserDaoImpl(dbProvider = { database }, ioContext = Dispatchers.IO)

        val otherAuthor = Uuid.parse("00000000-0000-0000-0000-0000000000b2")
        for (id in listOf(authorId, otherAuthor)) {
            userDao.insertUser(
                UserEntity(
                    id = id,
                    username = "user-$id",
                    email = null,
                    display_name = null,
                    avatar = null,
                    bio = null,
                    created_at = now,
                    updated_at = now,
                    deleted_at = null,
                    role = UserRole.MEMBER,
                )
            )
        }
        database.articleQueries.upsertArticle(article("000000000001", ArticleStatus.PUBLISHED))
        database.articleQueries.upsertArticle(article("000000000002", ArticleStatus.PUBLISHED, otherAuthor))

        val mine = dao
            .pagingSource(pageSize = 10, authorId = authorId, status = ArticleStatus.PUBLISHED)
            .loadRefresh()

        assertEquals(listOf("0001"), mine.map { it.id.toString().takeLast(4) })
        driver.close()
    }

    private fun article(
        idSuffix: String,
        status: ArticleStatus,
        author: Uuid = authorId,
    ): Article = Article(
        id = Uuid.parse("00000000-0000-0000-0000-$idSuffix"),
        title = "title $idSuffix",
        content = null,
        author_id = author,
        status = status,
        excerpt = null,
        created_at = now,
        updated_at = now,
        published_at = null,
        cover_image = null,
        deleted_at = null,
        slug = null,
        stats = null,
    )

    private suspend fun <T : Any> androidx.paging.PagingSource<Uuid, T>.loadRefresh(): List<T> {
        val result = load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 10,
                placeholdersEnabled = false,
            )
        )
        return (result as PagingSource.LoadResult.Page).data
    }
}
