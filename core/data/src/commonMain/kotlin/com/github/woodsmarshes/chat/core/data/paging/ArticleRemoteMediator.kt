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
import kotlin.uuid.Uuid

@ExperimentalPagingApi
class ArticleRemoteMediator(
    private val getMyArticle: Boolean,
    private val authorId: Uuid? = null,
    private val articleApi: ArticleApi,
    private val articleDao: ArticleDao,
    private val userDao: UserDao,
    private val appDispatchers: AppDispatchers,
) : RemoteMediator<Uuid, KeyedArticlesWithAuthor>() {
    private val log = KotlinLogging.logger {}

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Uuid, KeyedArticlesWithAuthor>
    ): MediatorResult {
        return try {
            val cursor: Uuid? = when (loadType) {
                LoadType.REFRESH -> {
                    log.info { "DEBUG-MEDIATOR: REFRESH triggering, cursor = null" }
                    null
                }
                LoadType.PREPEND -> {
                    log.info { "DEBUG-MEDIATOR: PREPEND triggering, skipping" }
                    return MediatorResult.Success(endOfPaginationReached = true)
                }
                LoadType.APPEND -> {
                    val lastId = state.lastItemOrNull()?.id
                    log.info { "DEBUG-MEDIATOR: APPEND triggering, lastItem = $lastId" }
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

            if (response.isEmpty()) {
                return MediatorResult.Success(endOfPaginationReached = true)
            }

            val articles = response.map { it.toArticle() }
            val users = response
                .map { it.toUserEntity() }
                .distinct()

            withContext(appDispatchers.io) {
                userDao.insertUsers(users)
                articleDao.upsertAll(articles)
            }

            MediatorResult.Success(
                endOfPaginationReached = response.size < pageSize
            )
        } catch (e: Exception) {
            log.error(tag = "ArticleRemoteMediator", message = "Load failed", throwable = e)
            MediatorResult.Error(e)
        }
    }
}
