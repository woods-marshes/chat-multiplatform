package com.github.woodsmarshes.chat.core.ui.components.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.woodsmarshes.chat.core.ui.components.AppAlertDialog
import com.github.woodsmarshes.chat.core.ui.components.AppProgressIndicator
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings

@Composable
fun LoadingDialog(
    modifier: Modifier = Modifier,
    message: String = LocalStrings.current.loading,
    onDismissRequest: (() -> Unit)? = null,
) {
    AppAlertDialog(
        show = true,
        onDismissRequest = { onDismissRequest?.invoke() },
        title = "",
        modifier = modifier,
        confirmLabel = if (onDismissRequest != null) LocalStrings.current.dismiss else null,
        onConfirm = onDismissRequest,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppProgressIndicator(
                size = 36.dp,
                strokeWidth = 3.dp,
            )
            Spacer(Modifier.height(16.dp))
            Text(message)
        }
    }
}

