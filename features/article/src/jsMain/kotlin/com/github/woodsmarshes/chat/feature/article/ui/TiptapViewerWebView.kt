package com.github.woodsmarshes.chat.feature.article.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
actual fun TiptapViewerWebView(
    jsonContentStr: String,
    onScrollUp: () -> Unit,
    onScrollDown: () -> Unit,
    modifier: Modifier,
) {
    // JS target uses web module's React-based rendering instead.
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Article viewer is handled by the web module.")
    }
}
