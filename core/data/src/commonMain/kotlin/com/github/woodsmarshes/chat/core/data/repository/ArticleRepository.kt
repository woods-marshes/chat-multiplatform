package com.github.woodsmarshes.chat.core.data.repository

import androidx.paging.PagingData
import com.github.michaelbull.result.Result
import com.github.woodsmarshes.chat.core.model.ArticleStatus
import com.github.woodsmarshes.chat.core.model.error.ArticleError
import com.github.woodsmarshes.chat.core.model.ui.ArticleListUiModel
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement
import kotlin.uuid.Uuid

interface ArticleRepository {

    val invalidationEvents: Flow<Unit>

    fun getArticles(
        limit: Int = 20,
        authorId: Uuid? = null,
    ): Flow<PagingData<ArticleListUiModel>>

    suspend fun saveArticle(
        id: Uuid,
        title: String?,
        content: JsonElement?,
        status: ArticleStatus,
        excerpt: String?,
    ): Result<Unit, ArticleError>

    suspend fun deleteArticle(id: Uuid): Result<Unit, ArticleError>
}
