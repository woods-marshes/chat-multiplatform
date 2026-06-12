package com.github.woodsmarshes.chat.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.woodsmarshes.chat.core.ui.theme.LocalBubbleColors

@Composable
fun AlphabetIndexBar(
    letters: List<String>,
    onLetterSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bubbleColors = LocalBubbleColors.current

    Column(
        modifier = modifier
            .width(24.dp)
            .fillMaxHeight()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { letter ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onLetterSelected(letter) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = letter,
                    color = bubbleColors.inputSendIconTint,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
