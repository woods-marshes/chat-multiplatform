package com.github.woodsmarshes.chat.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Settings
import com.github.woodsmarshes.chat.core.ui.components.avatar.UserAvatar
import com.github.woodsmarshes.chat.core.ui.theme.LocalBubbleColors

@Composable
fun MeDrawerSheet(
    displayName: String,
    username: String,
    avatarUrl: String?,
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bubbleColors = LocalBubbleColors.current

    ModalDrawerSheet(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))

            UserAvatar(
                name = displayName.ifEmpty { username },
                avatarUrl = avatarUrl,
                size = 72.dp,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = displayName.ifEmpty { username },
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = bubbleColors.onSurfaceColor,
            )

            if (username.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "@$username",
                    fontSize = 14.sp,
                    color = bubbleColors.timestampColor,
                )
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            NavigationDrawerItem(
                icon = { Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(24.dp)) },
                label = { Text("个人资料") },
                selected = false,
                onClick = onProfileClick,
                colors = NavigationDrawerItemDefaults.colors(
                    unselectedIconColor = bubbleColors.iconTint,
                    unselectedTextColor = bubbleColors.onSurfaceColor,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(24.dp)) },
                label = { Text("设置") },
                selected = false,
                onClick = onSettingsClick,
                colors = NavigationDrawerItemDefaults.colors(
                    unselectedIconColor = bubbleColors.iconTint,
                    unselectedTextColor = bubbleColors.onSurfaceColor,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            NavigationDrawerItem(
                icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(24.dp)) },
                label = { Text("退出登录", color = bubbleColors.errorColor) },
                selected = false,
                onClick = onLogoutClick,
                colors = NavigationDrawerItemDefaults.colors(
                    unselectedIconColor = bubbleColors.errorColor,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}
