package com.github.woodsmarshes.chat.core.ui.components.dialog

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.woodsmarshes.chat.core.ui.components.AppAlertDialog
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings

@Composable
fun ErrorDialog(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = LocalStrings.current.loadFailed,
    onRetry: (() -> Unit)? = null,
) {
    AppAlertDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = title,
        modifier = modifier,
        summary = message,
        confirmLabel = LocalStrings.current.dismiss,
        onConfirm = onDismiss,
        dismissLabel = if (onRetry != null) LocalStrings.current.retry else null,
        onDismiss = onRetry,
    )
}

