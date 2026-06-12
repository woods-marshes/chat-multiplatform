package com.github.woodsmarshes.chat.core.ui.components.dialog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings

@Composable
fun LoadingDialog(
    message: String = LocalStrings.current.loading,
    onDismissRequest: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = { onDismissRequest?.invoke() },
        confirmButton = {
            if (onDismissRequest != null) {
                TextButton(onClick = { onDismissRequest() }) {
                    Text(LocalStrings.current.dismiss)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 3.dp,
                )
                Spacer(Modifier.height(16.dp))
                Text(message)
            }
        },
    )
}
