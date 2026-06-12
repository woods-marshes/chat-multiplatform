package com.github.woodsmarshes.chat.core.ui.components.state
import com.github.woodsmarshes.chat.core.ui.components.ChatAppButton
import com.github.woodsmarshes.chat.core.ui.components.ButtonSize
import com.github.woodsmarshes.chat.core.ui.components.ButtonStyle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.woodsmarshes.chat.core.ui.theme.LocalBubbleColors
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Info

@Composable
fun ErrorContent(
    message: String,
    onRetry: (() -> Unit)? = null,
    retryLabel: String = "重试",
    modifier: Modifier = Modifier,
) {
    val bubbleColors = LocalBubbleColors.current

    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = MiuixIcons.Info,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = bubbleColors.errorColor.copy(alpha = 0.6f),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            color = bubbleColors.onSurfaceColor,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            Spacer(modifier = Modifier.height(20.dp))
            ChatAppButton(
                onClick = onRetry,
                label = retryLabel,
                style = ButtonStyle.PRIMARY,
                size = ButtonSize.SM,
            )
        }
    }
}
