package com.github.woodsmarshes.chat.core.ui.components.bubble

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.woodsmarshes.chat.core.model.FileContent
import com.github.woodsmarshes.chat.core.ui.theme.LocalBubbleColors
import com.github.woodsmarshes.chat.core.ui.theme.LocalBubbleShapes
import com.github.woodsmarshes.chat.core.ui.utils.formatFileSize

@Composable
fun FileBubble(
    content: FileContent?,
    isOwnMessage: Boolean,
    onFileClick: ((FileContent) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (content == null) return

    val bubbleColors = LocalBubbleColors.current
    val bubbleShapes = LocalBubbleShapes.current

    val bgColor = if (isOwnMessage) bubbleColors.ownBackground.copy(alpha = 0.85f)
    else bubbleColors.otherBackground

    val contentColor = if (isOwnMessage) bubbleColors.ownContent else bubbleColors.otherContent
    val shape = if (isOwnMessage) bubbleShapes.ownBubble else bubbleShapes.otherBubble
    val sizeStr = formatFileSize(content.size)

    Row(
        modifier = modifier
            .widthIn(min = 160.dp, max = 260.dp)
            .clip(shape)
            .background(bgColor)
            .then(
                if (onFileClick != null) Modifier.clickable { onFileClick(content) }
                else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(contentColor.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp),
            )
        }

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = content.fileName,
                color = contentColor,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = sizeStr,
                    color = bubbleColors.timestampColor,
                    fontSize = 11.sp,
                )
                val mimeStr = content.mimeType
                if (mimeStr != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = mimeStr
                            .replace("application/", "")
                            .replace("audio/", "")
                            .replace("video/", "")
                            .replace("image/", ""),
                        color = bubbleColors.timestampColor,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}
