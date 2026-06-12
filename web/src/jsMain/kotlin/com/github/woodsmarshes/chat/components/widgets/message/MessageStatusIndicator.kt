package com.github.woodsmarshes.chat.components.widgets.message

import androidx.compose.runtime.Composable
import com.github.woodsmarshes.chat.components.widgets.SpinnerIcon
import com.github.woodsmarshes.chat.model.MessageState
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Color
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.modifiers.border
import com.varabyte.kobweb.compose.ui.modifiers.color
import com.varabyte.kobweb.compose.ui.modifiers.fillMaxSize
import com.varabyte.kobweb.compose.ui.modifiers.size
import com.varabyte.kobweb.silk.components.icons.fa.FaCheck
import com.varabyte.kobweb.silk.components.icons.fa.FaCircleExclamation
import org.jetbrains.compose.web.css.px

@Composable
fun MessageStatusIndicator(
    state: MessageState,
    modifier: Modifier = Modifier,
    color: Color? = null
) {
    val defaultIndicatorColor = when (state) {
        is MessageState.SendFailed -> Colors.Red
        MessageState.Completed -> Colors.Green.copyf(alpha = 0.8f)
        MessageState.Sending -> Colors.DodgerBlue
    }
    val indicatorColor = color ?: defaultIndicatorColor

    Box(
        modifier = modifier
            .size(16.px)
            .color(indicatorColor)
    ) {
        when (state) {
            MessageState.Sending -> {
                SpinnerIcon(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(color = indicatorColor, width = 1.5.px)
                )
            }
            is MessageState.SendFailed -> {
                FaCircleExclamation(
                    modifier = Modifier.fillMaxSize()
                )
            }
            MessageState.Completed -> {
                FaCheck(
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}