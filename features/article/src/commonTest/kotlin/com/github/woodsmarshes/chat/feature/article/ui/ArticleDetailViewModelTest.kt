package com.github.woodsmarshes.chat.feature.article.ui

import androidx.paging.PagingData
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.woodsmarshes.chat.core.data.repository.ArticleRepository
import com.github.woodsmarshes.chat.core.data.repository.UserRepository
import com.github.woodsmarshes.chat.core.model.Article
import com.github.woodsmarshes.chat.core.model.ArticleStatus
import com.github.woodsmarshes.chat.core.model.PrivacySetting
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.UserPreference
import com.github.woodsmarshes.chat.core.model.UserSetting
import com.github.woodsmarshes.chat.core.model.error.ArticleError
import com.github.woodsmarshes.chat.core.model.error.UserError
import com.github.woodsmarshes.chat.core.model.ui.ArticleListUiModel
import com.github.woodsmarshes.chat.core.ui.resources.getLocaleStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonElement
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class ArticleDetailViewModelTest {

    private val articleId = Uuid.parse("00000000-0000-0000-0000-0000000000a1")
    private val authorId = Uuid.parse("00000000-0000-0000-0000-0000000000a2")

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun notFoundErrorMapsToLocalizedStringInsteadOfRawErrorToString() = runTest {
        val vm = ArticleDetailViewModel(
            articleRepository = FakeArticleRepository(Err(ArticleError.NotFound)),
            userRepository = FakeUserRepository(),
            articleId = articleId,
            authorId = authorId,
        )

        val state = vm.uiState.first { !it.isLoading }
        assertEquals(getLocaleStrings().articleNotFound, state.error)
    }

    @Test
    fun networkErrorDoesNotExposeRawExceptionOrUrlInUiState() = runTest {
        val rawLeak = "https://internal.example.com/v1/articles?access_token=secret-jwt-token"
        val vm = ArticleDetailViewModel(
            articleRepository = FakeArticleRepository(Err(ArticleError.Unknown(rawLeak))),
            userRepository = FakeUserRepository(),
            articleId = articleId,
            authorId = authorId,
        )

        val state = vm.uiState.first { !it.isLoading }
        assertEquals(getLocaleStrings().articleLoadFailed, state.error)
        assertFalse(state.error.orEmpty().contains("secret-jwt-token"))
        assertFalse(state.error.orEmpty().contains("internal.example.com"))
    }

    private class FakeArticleRepository(
        private val result: Result<Article?, ArticleError>,
    ) : ArticleRepository {
        override suspend fun getArticle(
            getMyArticle: Boolean,
            articleId: Uuid,
        ): Flow<Result<Article?, ArticleError>> = flowOf(result)

        override fun getArticles(
            getMyArticle: Boolean,
            limit: Int,
            authorId: Uuid?,
        ): Flow<PagingData<ArticleListUiModel>> = emptyFlow()

        override suspend fun saveArticle(
            id: Uuid,
            title: String?,
            content: JsonElement?,
            status: ArticleStatus,
            excerpt: String?,
        ): Result<Unit, ArticleError> = Ok(Unit)

        override suspend fun createBlankArticle(): Result<Article, ArticleError> = error("not used")

        override suspend fun deleteArticle(id: Uuid): Result<Unit, ArticleError> = Ok(Unit)
    }

    private class FakeUserRepository : UserRepository {
        override fun getMeFlow(): Flow<User?> = flowOf(null)
        override suspend fun syncMe(): Result<User, UserError> = error("not used")
        override suspend fun updateMyProfile(
            displayName: String?,
            avatarUrl: String?,
            bio: String?,
        ): Result<User, UserError> = error("not used")
        override fun getGlobalSettingsFlow(): Flow<UserSetting?> = emptyFlow()
        override suspend fun syncGlobalSettings(): Result<UserSetting, UserError> = error("not used")
        override suspend fun updateGlobalSettings(
            privacy: PrivacySetting?,
            preferences: UserPreference?,
        ): Result<Boolean, UserError> = error("not used")
        override suspend fun fetchUserDetail(userId: Uuid): Result<User, UserError> = error("not used")
        override fun getUserFlow(userId: Uuid): Flow<User?> = emptyFlow()
        override suspend fun searchUsers(keyword: String): Result<List<User>, UserError> = error("not used")
    }
}
