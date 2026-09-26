package com.github.woodsmarshes.chat.feature.article_editor.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.michaelbull.result.onErr
import com.github.michaelbull.result.onOk
import com.github.woodsmarshes.chat.core.data.repository.ArticleRepository
import com.github.woodsmarshes.chat.core.data.repository.AuthRepository
import com.github.woodsmarshes.chat.core.data.repository.UserRepository
import com.github.woodsmarshes.chat.core.model.Article
import com.github.woodsmarshes.chat.core.model.ArticleStatus
import com.github.woodsmarshes.chat.core.network.ktor.NetworkConfig
import com.github.woodsmarshes.chat.core.network.serialization.ProjectJson
import com.github.woodsmarshes.chat.core.ui.resources.getLocaleStrings
import com.github.woodsmarshes.chat.feature.article_editor.model.EditorUiState
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.uuid.Uuid

class ArticleEditorViewModel(
    private val articleRepository: ArticleRepository,
    private val userRepository: UserRepository,
    private val networkConfig: NetworkConfig,
    private val authRepository: AuthRepository,
    private val articleId: Uuid? = null,
) : ViewModel() {
    private val log = KotlinLogging.logger {}

    // User-facing strings for ViewModel-produced messages (no CompositionLocal here).
    private val strings = getLocaleStrings()

    private val _uiState = MutableStateFlow(EditorUiState(isNew = articleId == null))
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private var activeArticleId: Uuid? = articleId

    init {
        if (articleId != null) {
            loadArticle(articleId)
        } else {
            createBlankArticle()
        }
    }

    /** Re-run the initial load after a failure so the screen can offer a retry. */
    fun reload() {
        _uiState.update { it.copy(error = null, isLoading = true) }
        val id = articleId ?: activeArticleId
        if (id != null) loadArticle(id) else createBlankArticle()
    }

    private fun loadArticle(id: Uuid) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            // Load via own-article API so drafts are visible
            articleRepository.getArticle(getMyArticle = true, articleId = id)
                .collect { result ->
                    result.onOk { article ->
                        if (article != null) {
                            val jsonStr = ProjectJson.encodeToString(
                                JsonElement.serializer(), article.content
                            )

                            val user = userRepository.getMeFlow().first()
                            val token = authRepository.jwtToken.first()

                            val resolvedCollabUrl = resolveCollabUrl(networkConfig)

                            _uiState.update {
                                it.copy(
                                    title = article.title,
                                    contentJsonStr = jsonStr,
                                    isLoading = false,
                                    isNew = false,
                                    collabUrl = resolvedCollabUrl,
                                    roomId = id.toString(),
                                    token = token,
                                    userInfoName = user?.displayName ?: user?.username ?: "Anonymous",
                                    userInfoColor = getHashColor(user?.id?.toString() ?: "anonymous"),
                                    isCollaborativeEditing = article.author.id != user?.id
                                )
                            }
                        } else {
                            _uiState.update { it.copy(isLoading = false, error = strings.articleNotFound) }
                        }
                    }.onErr { error ->
                        log.error { "[editor] load article $id failed: $error" }
                        _uiState.update { it.copy(isLoading = false, error = strings.articleLoadFailed) }
                    }
                }
        }
    }

    private fun createBlankArticle() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            articleRepository.createBlankArticle()
                .onOk { blankArticle ->
                    activeArticleId = blankArticle.id

                    val user = userRepository.getMeFlow().first()
                    val token = authRepository.jwtToken.first()

                    val resolvedCollabUrl = resolveCollabUrl(networkConfig)

                    _uiState.update {
                        it.copy(
                            title = blankArticle.title,
                            contentJsonStr = ProjectJson.encodeToString(
                                JsonElement.serializer(),
                                blankArticle.content
                            ),
                            isLoading = false,
                            isNew = true,
                            collabUrl = resolvedCollabUrl,
                            roomId = blankArticle.id.toString(),
                            token = token,
                            userInfoName = user?.displayName ?: user?.username ?: "Anonymous",
                            userInfoColor = getHashColor(user?.id?.toString() ?: "anonymous")
                        )
                    }
                }
                .onErr { error ->
                    log.error { "[editor] create blank article failed: $error" }
                    _uiState.update { it.copy(isLoading = false, error = strings.articleLoadFailed) }
                }
        }
    }

    fun updateTitle(title: String) {
        _uiState.update { it.copy(title = title) }
    }

    fun updateContent(jsonStr: String) {
        _uiState.update { it.copy(contentJsonStr = jsonStr) }
    }

    fun saveArticle(status: ArticleStatus) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            // A parse failure must never silently replace the document with
            // `{}` — that would wipe the article body on save.
            val content = try {
                ProjectJson.parseToJsonElement(_uiState.value.contentJsonStr)
            } catch (e: Exception) {
                log.error(e) { "Editor content is not valid JSON; refusing to save" }
                _uiState.update { it.copy(isSaving = false, error = strings.articleSaveFailed) }
                return@launch
            }
            val id = activeArticleId ?: Uuid.generateV7()
            articleRepository.saveArticle(
                id = id,
                title = _uiState.value.title.ifBlank { strings.articleUntitled },
                content = content,
                status = status,
                excerpt = null,
            ).onOk {
                // Pin the id only after a confirmed save, otherwise a failed
                // first save makes every retry mint a new (orphan) article.
                activeArticleId = id
                _uiState.update { it.copy(isSaving = false, isSaved = true, isNew = false, roomId = id.toString()) }
            }.onErr { error ->
                log.error { "[editor] save article $id failed: $error" }
                _uiState.update { it.copy(isSaving = false, error = strings.articleSaveFailed) }
            }
        }
    }

    private fun resolveCollabUrl(networkConfig: NetworkConfig): String {
        val host = networkConfig.host
        val isLocal = host == "localhost" || host == "127.0.0.1"
        val wsProtocol = if (networkConfig.useTls) "wss" else "ws"

        return if (isLocal) {
            "ws://127.0.0.1:1234"
        } else {
            val portStr = if (networkConfig.port == 80 || networkConfig.port == 443) "" else ":${networkConfig.port}"
            "$wsProtocol://$host$portStr/collab"
        }
    }

    private fun getHashColor(seed: String): String {
        val colors = listOf(
            "#f87171", "#fb923c", "#fbbf24", "#34d399",
            "#60a5fa", "#818cf8", "#a78bfa", "#f472b6"
        )
        val hash = seed.hashCode()
        val index = kotlin.math.abs(hash) % colors.size
        return colors[index]
    }
}
