package com.github.woodsmarshes.chat.feature.article_editor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
actual fun TiptapEditorWebView(
    initialTitle: String,
    initialJsonStr: String,
    onTitleChanged: (String) -> Unit,
    onContentChanged: (String) -> Unit,
    collabUrl: String?,
    roomId: String?,
    token: String?,
    userInfoName: String?,
    userInfoColor: String?,
    modifier: Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Article editor is handled by the web module.")
    }
}
