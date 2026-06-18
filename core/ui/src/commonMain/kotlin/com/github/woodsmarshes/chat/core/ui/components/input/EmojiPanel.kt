package com.github.woodsmarshes.chat.core.ui.components.input

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.woodsmarshes.chat.core.ui.theme.LocalBubbleColors

private val commonEmojis = listOf(
    "😀", "😂", "🤣", "😊", "😍", "🥰", "😘", "😜",
    "🤔", "😅", "😢", "😭", "😤", "🥺", "😱", "🤯",
    "👍", "👎", "👏", "🙌", "💪", "🔥", "⭐", "❤️",
    "💔", "🎉", "🎂", "🍰", "☕", "🍕", "🎵", "📷",
    "🏠", "✈️", "🚗", "⏰", "💰", "💡", "📌", "✅",
    "❌", "⚠️", "💯", "🙏", "🤝", "👋", "🫡", "💀",
)

@Composable
fun EmojiPanel(
    onEmojiSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bubbleColors = LocalBubbleColors.current

    LazyVerticalGrid(
        columns = GridCells.Fixed(8),
        modifier = modifier
            .fillMaxWidth()
            .background(bubbleColors.panelBackground)
            .padding(4.dp),
        contentPadding = PaddingValues(4.dp),
    ) {
        items(commonEmojis) { emoji ->
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onEmojiSelected(emoji) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = emoji,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
