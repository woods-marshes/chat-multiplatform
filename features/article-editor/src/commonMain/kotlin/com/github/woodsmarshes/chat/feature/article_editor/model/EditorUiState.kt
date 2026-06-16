package com.github.woodsmarshes.chat.feature.article_editor.model

data class EditorUiState(
    val title: String = "",
    val contentJsonStr: String = "{}",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val isSaved: Boolean = false,
    val isNew: Boolean = true,
)
