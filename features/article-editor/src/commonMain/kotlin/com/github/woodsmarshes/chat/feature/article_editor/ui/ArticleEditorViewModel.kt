package com.github.woodsmarshes.chat.feature.article_editor.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.michaelbull.result.onErr
import com.github.michaelbull.result.onOk
import com.github.woodsmarshes.chat.core.data.repository.ArticleRepository
import com.github.woodsmarshes.chat.core.model.Article
import com.github.woodsmarshes.chat.core.model.ArticleStatus
import com.github.woodsmarshes.chat.core.network.serialization.ProjectJson
import com.github.woodsmarshes.chat.feature.article_editor.model.EditorUiState
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.uuid.Uuid

class ArticleEditorViewModel(
    private val articleRepository: ArticleRepository,
    private val articleId: Uuid? = null,
) : ViewModel() {
    private val log = KotlinLogging.logger {}

    private val _uiState = MutableStateFlow(EditorUiState(isNew = articleId == null))
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    init {
        if (articleId != null) {
            loadArticle(articleId)
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
                            _uiState.update {
                                it.copy(
                                    title = article.title,
                                    contentJsonStr = jsonStr,
                                    isLoading = false,
                                    isNew = false,
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

    fun updateTitle(title: String) {
        _uiState.update { it.copy(title = title) }
    }

    fun updateContent(jsonStr: String) {
        _uiState.update { it.copy(contentJsonStr = jsonStr) }
    }

    fun saveArticle(status: ArticleStatus) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val id = articleId ?: Uuid.generateV7()
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
}
