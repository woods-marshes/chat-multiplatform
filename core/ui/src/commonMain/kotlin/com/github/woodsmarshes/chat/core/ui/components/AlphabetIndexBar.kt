package com.github.woodsmarshes.chat.core.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.woodsmarshes.chat.core.ui.theme.LocalBubbleColors

@Composable
fun AlphabetIndexBar(
    letters: List<String>,
    onLetterSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (letters.isEmpty()) return

    val bubbleColors = LocalBubbleColors.current
    val density = LocalDensity.current
    var containerHeightPx by remember { mutableIntStateOf(0) }
    val currentOnLetterSelected by rememberUpdatedState(onLetterSelected)

    val minSlotPx = with(density) { 16.dp.toPx() }
    val maxVisibleCount = if (containerHeightPx > 0 && minSlotPx > 0f) {
        (containerHeightPx / minSlotPx).toInt().coerceAtLeast(1)
    } else {
        letters.size
    }
    val step = if (maxVisibleCount >= letters.size) {
        1
    } else {
        ((letters.size + maxVisibleCount - 1) / maxVisibleCount).coerceAtLeast(1)
    }

    val visibleLetters = remember(letters, step) {
        letters.filterIndexed { index, _ -> index % step == 0 || index == letters.lastIndex }
    }

    fun selectAtY(y: Float) {
        if (containerHeightPx <= 0 || visibleLetters.isEmpty()) return
        val fraction = (y / containerHeightPx.toFloat()).coerceIn(0f, 0.9999f)
        val visibleIndex = (fraction * visibleLetters.size).toInt().coerceIn(0, visibleLetters.lastIndex)
        currentOnLetterSelected(visibleLetters[visibleIndex])
    }

    Column(
        modifier = modifier
            .widthIn(min = 32.dp)
            .fillMaxHeight()
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .onSizeChanged { containerHeightPx = it.height }
            .pointerInput(visibleLetters) {
                detectTapGestures { offset -> selectAtY(offset.y) }
            }
            .pointerInput(visibleLetters) {
                detectVerticalDragGestures(
                    onDragStart = { offset -> selectAtY(offset.y) },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        selectAtY(change.position.y)
                    },
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        visibleLetters.forEach { letter ->
            Box(
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = letter,
                    color = bubbleColors.inputSendIconTint,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

