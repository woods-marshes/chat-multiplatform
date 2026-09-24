package com.github.woodsmarshes.chat.feature.article.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * Overlaid edit action, rendered as a regular Compose overlay on every
 * platform. The desktop WebView is hosted by the Nucleus Tao backend, which
 * composites the native surface into the same window stack as Compose, so
 * overlays work there too.
 */
@Composable
fun ArticleEditFloatingActionButton(
    visible: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
    ) {
        ExtendedFloatingActionButton(
            onClick = onClick,
            icon = { Icon(Icons.Default.Edit, contentDescription = null) },
            text = { Text(label) },
        )
    }
}
