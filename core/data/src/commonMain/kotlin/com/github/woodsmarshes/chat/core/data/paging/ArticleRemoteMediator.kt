package com.github.woodsmarshes.chat.core.data.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.github.woodsmarshes.chat.core.common.AppDispatchers
import com.github.woodsmarshes.chat.core.common.utils.debug
import com.github.woodsmarshes.chat.core.common.utils.error
import com.github.woodsmarshes.chat.core.data.model.toArticle
import com.github.woodsmarshes.chat.core.data.model.toUserEntity
import com.github.woodsmarshes.chat.core.database.dao.ArticleDao
import com.github.woodsmarshes.chat.core.database.dao.UserDao
import com.github.woodsmarshes.chat.core.network.api.rest.ArticleApi
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.woodsmarshes.chat.db.KeyedArticlesWithAuthor
import io.github.woodsmarshes.chat.db.ListAllArticlesWithAuthor
import kotlinx.coroutines.withContext
import com.github.woodsmarshes.chat.core.database.di.DatabaseHolder
import com.github.woodsmarshes.chat.core.database.di.BoundDatabaseElement
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.uuid.Uuid

@ExperimentalPagingApi
class ArticleRemoteMediator(
    private val getMyArticle: Boolean,
    private val authorId: Uuid? = null,
    private val articleApi: ArticleApi,
    private val articleDao: ArticleDao,
    private val userDao: UserDao,
    private val appDispatchers: AppDispatchers,
    private val databaseHolder: DatabaseHolder,
) : SessionBoundRemoteMediator<Uuid, KeyedArticlesWithAuthor>(databaseHolder.sessionGate) {
    private val log = KotlinLogging.logger {}

    override suspend fun loadInSession(
        loadType: LoadType,
        state: PagingState<Uuid, KeyedArticlesWithAuthor>
    ): MediatorResult {
        return run {
            val cursor: Uuid? = when (loadType) {
                LoadType.REFRESH -> {
                    log.debug(tag = "ArticleRemoteMediator", message = "REFRESH triggering, cursor = null")
                    null
                }
                LoadType.PREPEND -> {
                    log.debug(tag = "ArticleRemoteMediator", message = "PREPEND triggering, skipping")
                    return MediatorResult.Success(endOfPaginationReached = true)
                }
                LoadType.APPEND -> {
                    state.lastItemOrNull()?.id
                }
            }
            log.debug(tag = "ArticleRemoteMediator", message = "cursor: $cursor")

            val pageSize = state.config.pageSize
            val response = if (!getMyArticle) {
                articleApi.listArticles(
                    beforeId = cursor,
                    limit = pageSize,
                    authorId = authorId,
                )
            } else {
                articleApi.listMyArticles(
                    beforeId = cursor,
                    limit = pageSize
                )
            }

            currentCoroutineContext().ensureActive()
            if (response.isEmpty()) {
                return MediatorResult.Success(endOfPaginationReached = true)
            }

            val articles = response.map { it.toArticle() }
            val users = response
                .map { it.toUserEntity() }
                .distinct()

            withContext(appDispatchers.io) {
                val db = checkNotNull(currentCoroutineContext()[BoundDatabaseElement]?.database)
                db.articleQueries.transaction {
                    userDao.insertUsers(users)
                    articleDao.upsertAll(articles)
                }
            }

            MediatorResult.Success(
                endOfPaginationReached = response.size < pageSize
            )
        }
    }

    override fun onLoadFailure(failure: Exception) {
        log.error(tag = "ArticleRemoteMediator", message = "Load failed", throwable = failure)
    }
}
