package com.github.woodsmarshes.chat.core.ui.components.input

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.woodsmarshes.chat.core.ui.theme.LocalBubbleColors

private data class MediaAction(
    val label: String,
    val icon: ImageVector,
    val selector: InputSelector,
)

@Composable
fun MediaActionPanel(
    onActionClick: (InputSelector) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bubbleColors = LocalBubbleColors.current

    val actions = listOf(
        MediaAction("图片", Icons.Default.Image, InputSelector.IMAGE),
        MediaAction("文件", Icons.AutoMirrored.Filled.InsertDriveFile, InputSelector.FILE),
        MediaAction("语音", Icons.Default.Mic, InputSelector.AUDIO),
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bubbleColors.panelBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        actions.forEach { action ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onActionClick(action.selector) }
                    .padding(8.dp),
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = action.label,
                    modifier = Modifier.size(32.dp),
                    tint = bubbleColors.iconTint,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = action.label,
                    fontSize = 12.sp,
                    color = bubbleColors.inputFieldContent,
                )
            }
        }
    }
}
