package com.github.woodsmarshes.chat.feature.article_editor.model

data class EditorUiState(
    val title: String = "",
    val contentJsonStr: String = "{}",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val isSaved: Boolean = false,
    val isNew: Boolean = true,

    val collabUrl: String? = null,
    val roomId: String? = null,
    val token: String? = null,
    val userInfoName: String? = null,
    val userInfoColor: String? = null,

    val isCollaborativeEditing: Boolean = false,
)
