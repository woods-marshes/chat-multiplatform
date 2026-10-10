package com.github.woodsmarshes.chat.core.ui.components.feedback

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Info

enum class SnackbarType { SUCCESS, ERROR, INFO, WARNING }

private class TypedSnackbarVisuals(
    override val message: String,
    val type: SnackbarType,
    override val actionLabel: String? = null,
    override val withDismissAction: Boolean = false,
    override val duration: SnackbarDuration = SnackbarDuration.Short,
) : SnackbarVisuals

class AppSnackbarState {
    private val _hostState = SnackbarHostState()
    val hostState: SnackbarHostState get() = _hostState

    suspend fun show(message: String, type: SnackbarType = SnackbarType.INFO) {
        _hostState.showSnackbar(TypedSnackbarVisuals(message = message, type = type))
    }
}

@Composable
fun rememberAppSnackbarState(): AppSnackbarState = remember { AppSnackbarState() }

val LocalSnackbarState = compositionLocalOf<AppSnackbarState> {
    error("AppSnackbarState not provided")
}

@Composable
fun AppSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier.padding(16.dp),
    ) { data ->
        val typed = data.visuals as? TypedSnackbarVisuals
        val type = typed?.type ?: SnackbarType.INFO
        val message = data.visuals.message
        val colorScheme = MaterialTheme.colorScheme

        val (bgColor, contentColor) = when (type) {
            SnackbarType.ERROR -> colorScheme.errorContainer to colorScheme.onErrorContainer
            SnackbarType.SUCCESS -> colorScheme.primaryContainer to colorScheme.onPrimaryContainer
            SnackbarType.WARNING -> colorScheme.tertiaryContainer to colorScheme.onTertiaryContainer
            SnackbarType.INFO -> colorScheme.inverseSurface to colorScheme.inverseOnSurface
        }

        Snackbar(
            modifier = Modifier.fillMaxWidth(),
            containerColor = bgColor,
            contentColor = contentColor,
            shape = RoundedCornerShape(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = MiuixIcons.Info,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = contentColor,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = message,
                    color = contentColor,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
fun AppSnackbarHost(snackbarState: AppSnackbarState, modifier: Modifier = Modifier) {
    AppSnackbarHost(hostState = snackbarState.hostState, modifier = modifier)
}

