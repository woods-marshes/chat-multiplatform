package com.github.woodsmarshes.chat.core.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.github.michaelbull.result.getError
import com.github.woodsmarshes.chat.core.data.paging.ArticleMediatorFactory
import com.github.woodsmarshes.chat.core.database.dao.ArticleDaoImpl
import com.github.woodsmarshes.chat.core.database.di.DatabaseHolder
import com.github.woodsmarshes.chat.core.database.di.createDatabase
import com.github.woodsmarshes.chat.core.database.session.ManagedDatabaseSessionGate
import com.github.woodsmarshes.chat.core.model.error.ArticleError
import com.github.woodsmarshes.chat.core.network.api.rest.ArticleApi
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class StoppedSessionMappingTest {

    /**
     * The session gate is never started, so every repository operation is
     * refused with SessionStoppedException before touching api or dao — the
     * exact condition executeResult's onStopped channel exists for. Kept
     * lifecycle-free on purpose: no SessionExecutor scope may outlive the
     * test (an abandoned scope on the test scheduler previously surfaced as
     * UncaughtExceptionsBeforeTest in a later test class on CI).
     */
    @Test
    fun articleOperationOnStoppedSessionMapsToPermissionDenied() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val db = createDatabase { schema -> schema.create(driver).await(); driver }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gate = ManagedDatabaseSessionGate(dispatcher, { db }, { 1L })
        val holder = mockk<DatabaseHolder>()
        every { holder.sessionGate } returns gate

        val repo = OfflineFirstArticleRepositoryImpl(
            articleApi = mockk<ArticleApi>(relaxed = true),
            articleDao = ArticleDaoImpl({ db }, dispatcher, gate),
            databaseHolder = holder,
            mediatorFactory = mockk<ArticleMediatorFactory>(relaxed = true),
        )

        val result = repo.createBlankArticle()

        assertEquals(ArticleError.PermissionDenied, result.getError())
    }
}
