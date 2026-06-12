package com.github.woodsmarshes.chat.core.ui.components.feedback

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Info

enum class SnackbarType { SUCCESS, ERROR, INFO, WARNING }

data class AppSnackbarMessage(
    val message: String,
    val type: SnackbarType = SnackbarType.INFO,
    val duration: kotlin.time.Duration = kotlin.time.Duration.parse("3s"),
)

class AppSnackbarState {
    private val _hostState = SnackbarHostState()
    val hostState: SnackbarHostState get() = _hostState

    suspend fun show(message: String, type: SnackbarType = SnackbarType.INFO) {
        _hostState.showSnackbar("$type:$message")
    }
}

@Composable
fun rememberAppSnackbarState(): AppSnackbarState = remember { AppSnackbarState() }

val LocalSnackbarState = compositionLocalOf<AppSnackbarState> {
    error("AppSnackbarState not provided")
}

@Composable
fun AppSnackbarHost(snackbarState: AppSnackbarState, modifier: Modifier = Modifier) {
    SnackbarHost(
        hostState = snackbarState.hostState,
        modifier = modifier.padding(16.dp),
    ) { data ->
        val parts = data.visuals.message.split(":", limit = 2)
        val type = try { SnackbarType.valueOf(parts[0]) } catch (_: Exception) { SnackbarType.INFO }
        val message = parts.getOrElse(1) { parts[0] }

        val bgColor = when (type) {
            SnackbarType.SUCCESS -> Color(0xFF2E7D32)
            SnackbarType.ERROR -> Color(0xFFC62828)
            SnackbarType.WARNING -> Color(0xFFEF6C00)
            SnackbarType.INFO -> Color(0xFF37474F)
        }

        Snackbar(
            modifier = Modifier.fillMaxWidth(),
            containerColor = bgColor,
            contentColor = Color.White,
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
                    tint = Color.White,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = message,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
