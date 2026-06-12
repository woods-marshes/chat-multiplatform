package com.github.woodsmarshes.chat.feature.profile.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.woodsmarshes.chat.core.ui.components.ChatAppCard
import com.github.woodsmarshes.chat.core.ui.components.ChatTopAppBar
import com.github.woodsmarshes.chat.core.ui.components.state.EmptyContent
import com.github.woodsmarshes.chat.core.ui.components.state.ErrorContent
import com.github.woodsmarshes.chat.core.ui.components.state.LoadingContent
import com.github.woodsmarshes.chat.core.ui.components.avatar.UserAvatar
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import com.github.woodsmarshes.chat.core.ui.theme.LocalBubbleColors
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ProfileScreen(
    userId: String,
    onBack: () -> Unit,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val bubbleColors = LocalBubbleColors.current

    Scaffold(
        topBar = {
            ChatTopAppBar(
                title = LocalStrings.current.profileTitle,
                showBackButton = true,
                onBackClick = onBack,
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> LoadingContent(message = LocalStrings.current.loading)
            uiState.error != null -> ErrorContent(
                message = uiState.error ?: LocalStrings.current.loadFailed,
                modifier = Modifier.padding(padding),
            )
            uiState.displayName.isEmpty() && !uiState.isLoading -> EmptyContent(
                message = LocalStrings.current.userNotFound,
                modifier = Modifier.padding(padding),
            )
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .consumeWindowInsets(padding)
                        .padding(padding)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ChatAppCard(modifier = Modifier.padding(24.dp)) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            UserAvatar(
                                name = uiState.displayName.ifEmpty { uiState.username },
                                avatarUrl = uiState.avatarUrl,
                                size = 96.dp,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = uiState.displayName.ifEmpty { uiState.username },
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = bubbleColors.onSurfaceColor,
                            )
                            if (uiState.username.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "@${uiState.username}",
                                    fontSize = 14.sp,
                                    color = bubbleColors.timestampColor,
                                )
                            }
                            val bio = uiState.bio
                            if (bio != null) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = bio,
                                    fontSize = 15.sp,
                                    color = bubbleColors.onSurfaceColor,
                                )
                            }
                            val email = uiState.email
                            if (email != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = email,
                                    fontSize = 13.sp,
                                    color = bubbleColors.timestampColor,
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = LocalStrings.current.userIdLabel(userId),
                                fontSize = 13.sp,
                                color = bubbleColors.timestampColor,
                            )
                        }
                    }
                }
            }
        }
    }
}
