package com.github.woodsmarshes.chat.feature.article_editor.ui

import androidx.paging.PagingData
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.woodsmarshes.chat.core.data.repository.ArticleRepository
import com.github.woodsmarshes.chat.core.data.repository.AuthRepository
import com.github.woodsmarshes.chat.core.data.repository.UserRepository
import com.github.woodsmarshes.chat.core.model.Article
import com.github.woodsmarshes.chat.core.model.ArticleStatus
import com.github.woodsmarshes.chat.core.model.SimpleUser
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.UserPreference
import com.github.woodsmarshes.chat.core.model.UserRole
import com.github.woodsmarshes.chat.core.model.UserSetting
import com.github.woodsmarshes.chat.core.model.error.ArticleError
import com.github.woodsmarshes.chat.core.model.error.AuthError
import com.github.woodsmarshes.chat.core.model.error.UserError
import com.github.woodsmarshes.chat.core.model.ui.ArticleListUiModel
import com.github.woodsmarshes.chat.core.network.ktor.NetworkConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * The editor body defaults to a valid-looking `{}`, so a save issued before the
 * document arrived — or after the load failed — would overwrite the stored
 * article with that placeholder. These tests pin the gate that prevents it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArticleEditorViewModelTest {

    private val articleId = Uuid.parse("00000000-0000-0000-0000-0000000000a1")
    private val authorId = Uuid.parse("00000000-0000-0000-0000-0000000000a2")
    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)

    private val storedDocument: JsonElement =
        Json.parseToJsonElement("""{"type":"doc","content":[]}""")

    private data class SavedArticle(
        val id: Uuid,
        val content: JsonElement?,
        val status: ArticleStatus,
    )

    private val saved = mutableListOf<SavedArticle>()

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun saveIsRefusedWhenTheArticleFailedToLoad() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val viewModel = editorViewModel(loadResult = Err(ArticleError.NotFound))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoaded, "a failed load must not mark the editor ready")
        assertNotNull(viewModel.uiState.value.error, "the screen still reports the load failure")

        viewModel.saveArticle(ArticleStatus.DRAFT)
        advanceUntilIdle()

        assertEquals(
            emptyList(),
            saved,
            "saving after a failed load would overwrite the stored article with the placeholder",
        )
    }

    @Test
    fun saveIsRefusedWhileTheDocumentHasNotArrivedYet() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        // The load neither succeeds nor fails: the document is simply not here yet.
        val viewModel = editorViewModel(loadResult = null)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoaded)

        viewModel.saveArticle(ArticleStatus.PUBLISHED)
        advanceUntilIdle()

        assertEquals(emptyList(), saved, "nothing may be persisted before the document arrives")
    }

    @Test
    fun saveRunsOnceTheDocumentLoaded() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val viewModel = editorViewModel(loadResult = Ok(storedArticle()))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isLoaded)

        viewModel.saveArticle(ArticleStatus.PUBLISHED)
        advanceUntilIdle()

        val written = saved.single()
        assertEquals(articleId, written.id, "the loaded article is updated, not replaced")
        assertEquals(storedDocument, written.content, "the loaded body is saved, not the placeholder")
        assertEquals(ArticleStatus.PUBLISHED, written.status)
    }

    @Test
    fun saveIsRefusedWhenTheBodyIsNotADocumentObject() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val viewModel = editorViewModel(loadResult = Ok(storedArticle()))
        advanceUntilIdle()

        viewModel.updateContent("""["not","a","document"]""")
        viewModel.saveArticle(ArticleStatus.DRAFT)
        advanceUntilIdle()

        assertEquals(emptyList(), saved, "a non-object body must never reach the repository")
    }

    /** [loadResult] null models a load that emits nothing at all. */
    private fun editorViewModel(loadResult: Result<Article?, ArticleError>?) = ArticleEditorViewModel(
        articleRepository = FakeArticleRepository(loadResult),
        userRepository = FakeUserRepository(me()),
        networkConfig = NetworkConfig(host = "127.0.0.1"),
        authRepository = FakeAuthRepository(),
        articleId = articleId,
    )

    private fun storedArticle() = Article(
        id = articleId,
        title = "Stored title",
        content = storedDocument,
        author = SimpleUser(
            id = authorId,
            username = "author",
            displayName = "Author",
            avatarUrl = null,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
            role = UserRole.MEMBER,
        ),
        status = ArticleStatus.DRAFT,
        excerpt = null,
        createdAt = now,
        updatedAt = now,
        publishedAt = null,
    )

    /** The signed-in user is also the author, so the editor is not in collaborative mode. */
    private fun me() = User(
        id = authorId,
        username = "author",
        email = null,
        displayName = "Author",
        avatarUrl = null,
        bio = null,
        createdAt = now,
        updatedAt = now,
        deletedAt = null,
    )

    private inner class FakeArticleRepository(
        private val loadResult: Result<Article?, ArticleError>?,
    ) : ArticleRepository {

        override suspend fun getArticle(
            getMyArticle: Boolean,
            articleId: Uuid,
        ): Flow<Result<Article?, ArticleError>> = loadResult?.let { flowOf(it) } ?: emptyFlow()

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
        ): Result<Unit, ArticleError> {
            saved += SavedArticle(id = id, content = content, status = status)
            return Ok(Unit)
        }

        override suspend fun createBlankArticle(): Result<Article, ArticleError> = Ok(storedArticle())

        override suspend fun deleteArticle(id: Uuid): Result<Unit, ArticleError> = Ok(Unit)
    }

    private class FakeUserRepository(private val user: User) : UserRepository {
        override fun getMeFlow(): Flow<User?> = flowOf(user)

        override suspend fun syncMe(): Result<User, UserError> = error("not used")

        override suspend fun updateMyProfile(
            displayName: String?,
            avatarUrl: String?,
            bio: String?,
        ): Result<User, UserError> = error("not used")

        override fun getGlobalSettingsFlow(): Flow<UserSetting?> = emptyFlow()

        override suspend fun syncGlobalSettings(): Result<UserSetting, UserError> = error("not used")

        override suspend fun updateGlobalSettings(
            privacy: com.github.woodsmarshes.chat.core.model.PrivacySetting?,
            preferences: UserPreference?,
        ): Result<Boolean, UserError> = error("not used")

        override suspend fun fetchUserDetail(userId: Uuid): Result<User, UserError> = error("not used")

        override fun getUserFlow(userId: Uuid): Flow<User?> = emptyFlow()

        override suspend fun searchUsers(keyword: String): Result<List<User>, UserError> = error("not used")
    }

    private class FakeAuthRepository : AuthRepository {
        override val jwtToken: Flow<String?> = flowOf("test-token")

        override fun observeIsLoggedIn(): Flow<Boolean> = flowOf(true)

        override suspend fun login(email: String, password: String): Result<User, AuthError> = error("not used")

        override suspend fun register(
            username: String,
            email: String,
            password: String,
        ): Result<User, AuthError> = error("not used")

        override suspend fun logout() = Unit

        override suspend fun tryAutoLogin(): Result<User, AuthError> = error("not used")
    }
}
