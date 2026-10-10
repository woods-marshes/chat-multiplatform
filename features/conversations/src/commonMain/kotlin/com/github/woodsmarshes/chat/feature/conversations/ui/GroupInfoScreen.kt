package com.github.woodsmarshes.chat.feature.conversations.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.woodsmarshes.chat.core.model.ui.SenderUser
import com.github.woodsmarshes.chat.core.ui.components.ButtonSize
import com.github.woodsmarshes.chat.core.ui.components.ButtonStyle
import com.github.woodsmarshes.chat.core.ui.components.ChatAppButton
import com.github.woodsmarshes.chat.core.ui.components.ChatAppCard
import com.github.woodsmarshes.chat.core.ui.components.ChatTopAppBar
import com.github.woodsmarshes.chat.core.ui.components.isInListDetailScene
import com.github.woodsmarshes.chat.core.ui.components.avatar.UserAvatar
import com.github.woodsmarshes.chat.core.ui.components.bubble.RoleMicroBadge
import com.github.woodsmarshes.chat.core.ui.components.state.EmptyContent
import com.github.woodsmarshes.chat.core.ui.components.state.ErrorContent
import com.github.woodsmarshes.chat.core.ui.components.state.LoadingContent
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupInfoScreen(
    conversationId: String,
    onBack: () -> Unit,
    onOpenChat: (conversationId: String) -> Unit,
    modifier: Modifier = Modifier,
    isExtraPane: Boolean = false,
    viewModel: GroupInfoViewModel = koinViewModel(parameters = { parametersOf(conversationId) }),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    val inMultiPane = isInListDetailScene()

    Scaffold(
        modifier = modifier,
        topBar = {
            ChatTopAppBar(
                title = strings.groupInfoTitle,
                showBackButton = !inMultiPane,
                showCloseButton = inMultiPane && isExtraPane,
                onBackClick = onBack,
            )
        },
    ) { padding ->
        when {
            uiState.notFound -> EmptyContent(
                message = strings.groupNotFound,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
            uiState.isLoading -> LoadingContent(
                message = strings.loading,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
            uiState.error != null && uiState.conversationId == null -> ErrorContent(
                message = uiState.error ?: strings.groupLoadFailed,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onRetry = viewModel::refresh,
            )
            else -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.TopCenter,
            ) {
                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    contentPadding = PaddingValues(top = 32.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    item(key = "header") {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            UserAvatar(
                                name = uiState.name,
                                avatarUrl = uiState.avatarUrl,
                                size = 120.dp,
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = uiState.name,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                            )
                            val handle = uiState.handle
                            if (!handle.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "@$handle",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                            val resolvedConvId = uiState.conversationId
                            if (uiState.isMember && resolvedConvId != null) {
                                if (!(inMultiPane && isExtraPane)) {
                                    ChatAppButton(
                                        onClick = { onOpenChat(resolvedConvId) },
                                        label = strings.openChat,
                                        style = ButtonStyle.PRIMARY,
                                        size = ButtonSize.MD,
                                        fullWidth = true,
                                    )
                                }
                            } else {
                                ChatAppButton(
                                    onClick = viewModel::joinGroup,
                                    label = strings.joinGroupTitle,
                                    style = ButtonStyle.PRIMARY,
                                    size = ButtonSize.MD,
                                    enabled = !uiState.isJoining,
                                    isLoading = uiState.isJoining,
                                    fullWidth = true,
                                )
                            }
                            val actionError = uiState.actionError
                            if (actionError != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = actionError,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }

                            val description = uiState.description
                            if (!description.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(24.dp))
                                ChatAppCard(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                    )
                                }
                            }

                            if (uiState.members.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = "${strings.membersLabel} (${uiState.members.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 4.dp),
                                )
                            }
                        }
                    }

                    items(
                        items = uiState.members,
                        key = { it.id },
                    ) { member ->
                        MemberRow(member = member)
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberRow(member: SenderUser) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(
            name = member.displayName?.ifEmpty { null } ?: member.username,
            avatarUrl = member.avatarUrl,
            size = 40.dp,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = member.displayName?.ifEmpty { null } ?: member.username,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (member.role != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    RoleMicroBadge(role = member.role)
                }
            }
            if (member.username.isNotEmpty()) {
                Text(
                    text = "@${member.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
