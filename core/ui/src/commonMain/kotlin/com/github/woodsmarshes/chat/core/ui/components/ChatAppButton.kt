package com.github.woodsmarshes.chat.core.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.woodsmarshes.chat.core.ui.theme.LocalBubbleColors

enum class ButtonStyle { PRIMARY, SECONDARY, TEXT, DANGER }

enum class ButtonSize(val height: Dp, val fontSize: Int, val padding: PaddingValues) {
    SM(36.dp, 13, PaddingValues(horizontal = 16.dp, vertical = 6.dp)),
    MD(44.dp, 15, PaddingValues(horizontal = 20.dp, vertical = 10.dp)),
    LG(52.dp, 17, PaddingValues(horizontal = 24.dp, vertical = 14.dp)),
}

@Composable
fun ChatAppButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.PRIMARY,
    size: ButtonSize = ButtonSize.MD,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    fullWidth: Boolean = false,
) {
    val bubbleColors = LocalBubbleColors.current
    val shape = RoundedCornerShape(12.dp)
    val effectiveModifier = if (fullWidth) modifier.fillMaxWidth().height(size.height) else modifier.height(size.height)

    when (style) {
        ButtonStyle.PRIMARY -> Button(
            onClick = onClick,
            modifier = effectiveModifier,
            enabled = enabled && !isLoading,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = bubbleColors.ownBackground,
                contentColor = bubbleColors.ownContent,
                disabledContainerColor = bubbleColors.ownBackground.copy(alpha = 0.4f),
            ),
            contentPadding = size.padding,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = bubbleColors.ownContent,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(label, fontSize = size.fontSize.sp, fontWeight = FontWeight.Medium)
            }
        }
        ButtonStyle.SECONDARY -> OutlinedButton(
            onClick = onClick,
            modifier = effectiveModifier,
            enabled = enabled && !isLoading,
            shape = shape,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = bubbleColors.ownBackground),
            contentPadding = size.padding,
        ) {
            Text(label, fontSize = size.fontSize.sp, fontWeight = FontWeight.Medium)
        }
        ButtonStyle.TEXT -> TextButton(
            onClick = onClick,
            modifier = effectiveModifier,
            enabled = enabled && !isLoading,
            contentPadding = size.padding,
        ) {
            Text(label, fontSize = size.fontSize.sp, fontWeight = FontWeight.Medium)
        }
        ButtonStyle.DANGER -> Button(
            onClick = onClick,
            modifier = effectiveModifier,
            enabled = enabled && !isLoading,
            shape = shape,
            colors = ButtonDefaults.buttonColors(containerColor = bubbleColors.errorColor, contentColor = Color.White),
            contentPadding = size.padding,
        ) {
            Text(label, fontSize = size.fontSize.sp, fontWeight = FontWeight.Medium)
        }
    }
}
