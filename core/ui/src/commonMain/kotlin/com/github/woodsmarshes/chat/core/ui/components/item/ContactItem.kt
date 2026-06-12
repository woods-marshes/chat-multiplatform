package com.github.woodsmarshes.chat.core.ui.components.item
import com.github.woodsmarshes.chat.core.ui.components.avatar.UserAvatar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.woodsmarshes.chat.core.model.ui.ContactUiModel
import com.github.woodsmarshes.chat.core.ui.theme.LocalBubbleColors

@Composable
fun ContactItem(
    contact: ContactUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bubbleColors = LocalBubbleColors.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(
            name = contact.displayName ?: contact.username,
            avatarUrl = contact.avatarUrl,
            size = 48.dp,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.displayName ?: contact.username,
                color = bubbleColors.onSurfaceColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val bio = contact.bio
            if (bio != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = bio,
                    color = bubbleColors.timestampColor,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
