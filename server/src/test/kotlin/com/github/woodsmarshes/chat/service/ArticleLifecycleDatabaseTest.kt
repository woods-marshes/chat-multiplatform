package com.github.woodsmarshes.chat.service

import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import com.github.woodsmarshes.chat.core.model.ArticleStatus
import com.github.woodsmarshes.chat.core.model.error.ArticleError
import com.github.woodsmarshes.chat.repository.ArticleDataSourceImpl
import com.github.woodsmarshes.chat.support.TestDb
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/**
 * Article lifecycle on a real database — most importantly pinning that
 * updating a soft-deleted article does NOT resurrect it: the update goes
 * through, but the read paths keep hiding the row.
 */
class ArticleLifecycleDatabaseTest {

    private val articleRepository = ArticleDataSourceImpl()
    private val service = ArticleService(articleRepository)

    private val content: JsonElement = Json.parseToJsonElement("""{"type":"doc","content":[]}""")

    @BeforeTest
    fun freshDatabase() {
        TestDb.reset()
    }

    private fun title(prefix: String) = "$prefix-${Uuid.random()}"

    @Test
    fun draftsAreInvisiblePubliclyButVisibleToTheAuthor(): Unit = runBlocking {
        val alice = TestDb.user("alice")
        val article = service.createArticle(alice, title("draft"), content, excerpt = "ex", status = ArticleStatus.DRAFT).get()!!

        assertEquals(ArticleError.NotFound, service.getArticle(article.id).getError())
        assertEquals(article.id, service.getMyArticle(article.id, alice).get()!!.id)
        assertEquals(emptyList(), service.listArticles(limit = 10).get())
        assertEquals(1, service.listMyArticles(alice).get()!!.size)
    }

    @Test
    fun publishingMakesTheArticlePublicAndStampsPublishedAt(): Unit = runBlocking {
        val alice = TestDb.user("alice")
        val article = service.createArticle(alice, title("publish me"), content, excerpt = "ex", status = ArticleStatus.DRAFT).get()!!

        val published = service.updateArticle(alice, article.id, null, null, ArticleStatus.PUBLISHED, null).get()!!

        assertEquals(ArticleStatus.PUBLISHED, published.status)
        assertNotNull(published.publishedAt)
        assertEquals(article.id, service.getArticle(article.id).get()!!.id)
        assertEquals(listOf(article.id), service.listArticles(limit = 10).get()!!.map { it.id })
    }

    @Test
    fun crossAuthorUpdatesAreDenied(): Unit = runBlocking {
        val alice = TestDb.user("alice")
        val bob = TestDb.user("bob")
        val article = service.createArticle(alice, title("mine"), content, excerpt = "ex", status = ArticleStatus.PUBLISHED).get()!!

        val result = service.updateArticle(bob, article.id, "hijacked", null, null, null)

        assertEquals(ArticleError.PermissionDenied, result.getError())
    }

    @Test
    fun deletionHidesTheArticleFromEveryReadPath(): Unit = runBlocking {
        val alice = TestDb.user("alice")
        val article = service.createArticle(alice, title("doomed"), content, excerpt = "ex", status = ArticleStatus.PUBLISHED).get()!!

        service.deleteArticle(alice, article.id)

        assertEquals(ArticleError.NotFound, service.getArticle(article.id).getError())
        assertEquals(ArticleError.NotFound, service.getMyArticle(article.id, alice).getError())
        assertEquals(emptyList(), service.listArticles(limit = 10).get())
        assertNotNull(articleRepository.getById(article.id)!!.deletedAt)
    }

    @Test
    fun updatingASoftDeletedArticleDoesNotResurrectIt(): Unit = runBlocking {
        val alice = TestDb.user("alice")
        val article = service.createArticle(alice, title("zombie-to-be"), content, excerpt = "ex", status = ArticleStatus.PUBLISHED).get()!!
        service.deleteArticle(alice, article.id)

        // A stale client (or attacker) replays an update against the deleted
        // id: the repository update goes through, but deletedAt stays set, so
        // the article remains invisible everywhere.
        val saved = service.saveArticle(
            userId = alice,
            id = article.id,
            title = "resurrect?",
            content = content,
            status = ArticleStatus.PUBLISHED,
            excerpt = "ex",
        )

        assertEquals(article.id, saved.get()!!.id)
        assertNotNull(articleRepository.getById(article.id)!!.deletedAt)
        assertEquals(ArticleError.NotFound, service.getArticle(article.id).getError())
        assertEquals(emptyList(), service.listArticles(limit = 10).get()!!.map { it.id })
    }
}
