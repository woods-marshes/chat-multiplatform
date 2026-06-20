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
                            _uiState.update { it.copy(isLoading = false, error = "文章未找到") }
                        }
                    }.onErr { error ->
                        _uiState.update { it.copy(isLoading = false, error = error.toString()) }
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
                    _uiState.update { it.copy(isLoading = false, error = error.toString()) }
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
            val id = activeArticleId ?: Uuid.generateV7()
            val content = try {
                ProjectJson.parseToJsonElement(_uiState.value.contentJsonStr)
            } catch (e: Exception) {
                ProjectJson.parseToJsonElement("{}")
            }
            articleRepository.saveArticle(
                id = id,
                title = _uiState.value.title.ifBlank { "Untitled" },
                content = content,
                status = status,
                excerpt = null,
            ).onOk {
                _uiState.update { it.copy(isSaving = false, isSaved = true) }
            }.onErr { error ->
                _uiState.update { it.copy(isSaving = false, error = error.toString()) }
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
