package com.github.woodsmarshes.chat.feature.chat.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.github.panpf.zoomimage.CoilZoomAsyncImage
import com.github.woodsmarshes.chat.core.model.ImageContent
import com.github.woodsmarshes.chat.core.ui.components.avatar.LocalServerBaseUrl
import com.github.woodsmarshes.chat.core.ui.components.avatar.resolveMediaUrl
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings

/**
 * Full-screen pinch-zoom image viewer shown when a chat image bubble is tapped.
 * Rendered as a sibling after the chat [androidx.compose.material3.Scaffold] so
 * it covers the whole window, including the top bar.
 */
@Composable
fun ImageViewerOverlay(
    content: ImageContent,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    val resolvedUrl = resolveMediaUrl(content.url, LocalServerBaseUrl.current)

    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = true,
        onBackCompleted = onDismiss,
    )

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        if (resolvedUrl != null) {
            CoilZoomAsyncImage(
                model = resolvedUrl,
                contentDescription = content.fileName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }

        IconButton(
            onClick = onDismiss,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = strings.backCd,
                tint = Color.White,
            )
        }
    }
}
