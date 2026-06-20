package com.github.woodsmarshes.chat.feature.article_editor.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun TiptapEditorWebView(
    initialTitle: String,
    initialJsonStr: String,
    onTitleChanged: (String) -> Unit,
    onContentChanged: (String) -> Unit,
    collabUrl: String? = null,
    roomId: String? = null,
    token: String? = null,
    userInfoName: String? = null,
    userInfoColor: String? = null,
    modifier: Modifier = Modifier,
)
