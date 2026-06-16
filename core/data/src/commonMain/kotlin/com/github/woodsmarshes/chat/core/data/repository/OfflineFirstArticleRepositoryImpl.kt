package com.github.woodsmarshes.chat.core.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.woodsmarshes.chat.core.data.model.toArticleListUiModel
import com.github.woodsmarshes.chat.core.data.model.toDBArticle
import com.github.woodsmarshes.chat.core.data.paging.ArticleRemoteMediator
import com.github.woodsmarshes.chat.core.database.dao.ArticleDao
import com.github.woodsmarshes.chat.core.model.Article
import com.github.woodsmarshes.chat.core.model.ArticleStatus
import com.github.woodsmarshes.chat.core.model.error.ArticleError
import com.github.woodsmarshes.chat.core.model.ui.ArticleListUiModel
import com.github.woodsmarshes.chat.core.network.api.rest.ArticleApi
import com.github.woodsmarshes.chat.core.network.dto.article.UpdateArticleRequest
import com.github.woodsmarshes.chat.core.network.ktor.bindApi
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.parameter.parametersOf
import kotlin.time.Clock
import kotlin.uuid.Uuid

class OfflineFirstArticleRepositoryImpl(
    private val articleApi: ArticleApi,
    private val articleDao: ArticleDao,
    private val scope: CoroutineScope
) : ArticleRepository, KoinComponent {
    private val log = KotlinLogging.logger {}

    private val _invalidationEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val invalidationEvents: Flow<Unit>
        get() = _invalidationEvents.asSharedFlow()

    @OptIn(ExperimentalPagingApi::class)
    override fun getArticles(
        limit: Int,
        authorId: Uuid?
    ): Flow<PagingData<ArticleListUiModel>> {
        return Pager(
            config = PagingConfig(
                pageSize = limit,
                enablePlaceholders = false,
            ),
            remoteMediator = get<ArticleRemoteMediator> {
                parametersOf(authorId != null, authorId)
            },
            pagingSourceFactory = {
                val source = articleDao.pagingSource(
                    pageSize = limit.toLong(),
                    authorId = authorId
                )

                val job = scope.launch {
                    invalidationEvents.collect {
                        if (!source.invalid) {
                            source.invalidate()
                        }
                    }
                }

                source.registerInvalidatedCallback {
                    job.cancel()
                }

                source
            }
        ).flow.map { pagingData ->
            pagingData.map { it.toArticleListUiModel() }
        }
    }

    override suspend fun saveArticle(
        id: Uuid,
        title: String?,
        content: JsonElement?,
        status: ArticleStatus,
        excerpt: String?,
    ): Result<Unit, ArticleError>  = coroutineBinding {
        val article = bindApi(ArticleError::Unknown) {
            articleApi.saveArticle(
                id = id,
                request = UpdateArticleRequest(
                    title = title,
                    content = content,
                    status = status,
                    excerpt = excerpt,
                )
            )
        }
        articleDao.upsert(article.toDBArticle())
        _invalidationEvents.tryEmit(Unit)
    }

    override suspend fun deleteArticle(id: Uuid): Result<Unit, ArticleError> = coroutineBinding {
        bindApi(ArticleError::Unknown) {
            articleApi.deleteArticle(id)
        }.also {
            _invalidationEvents.tryEmit(Unit)
            articleDao.softDelete(
                id = id,
                deletedAt = Clock.System.now()
            )
        }
    }
}
