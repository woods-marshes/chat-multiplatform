package com.github.woodsmarshes.chat.core.ui.components.dialog

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings

@Composable
fun ErrorDialog(
    title: String = LocalStrings.current.loadFailed,
    message: String,
    onDismiss: () -> Unit,
    onRetry: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(LocalStrings.current.dismiss)
            }
        },
        dismissButton = if (onRetry != null) {
            {
                TextButton(onClick = onRetry) {
                    Text(LocalStrings.current.retry)
                }
            }
        } else {
            null
        },
    )
}
