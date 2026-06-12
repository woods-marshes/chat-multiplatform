package com.github.woodsmarshes.chat.components.widgets.message

import androidx.compose.runtime.Composable
import com.github.woodsmarshes.chat.components.widgets.UserAvatar
import com.github.woodsmarshes.chat.model.Message
import com.varabyte.kobweb.compose.foundation.layout.Arrangement
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.foundation.layout.Row
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.fillMaxWidth
import com.varabyte.kobweb.compose.ui.modifiers.flexShrink
import com.varabyte.kobweb.compose.ui.modifiers.minWidth
import com.varabyte.kobweb.compose.ui.modifiers.padding
import org.jetbrains.compose.web.css.px

@Composable
fun OtherMessageContainer(
    modifier: Modifier = Modifier,
    message: Message,
    onClickAvatar: (Message) -> Unit,
    onAvatarContextMenu: (Message) -> Unit,
    messageContent: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(topBottom = 4.px, leftRight = 16.px),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Top,
            modifier = Modifier
        ) {
            UserAvatar(
                modifier = Modifier.flexShrink(0),
                avatarUrl = message.detail.sender?.avatar,
                onClick = { onClickAvatar(message) },
                onContextMenu = { onAvatarContextMenu(message) },
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(right = 8.px)
                    .minWidth(0.px),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(space = 4.px)
            ) {
                SenderInfoDisplay(sender = message.detail.sender)

                messageContent()
            }
        }
    }
}