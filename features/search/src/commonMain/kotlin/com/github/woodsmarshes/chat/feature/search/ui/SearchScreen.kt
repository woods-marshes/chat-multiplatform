package com.github.woodsmarshes.chat.feature.search.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.woodsmarshes.chat.core.ui.components.ChatTopAppBar
import com.github.woodsmarshes.chat.core.ui.components.item.ContactItem
import com.github.woodsmarshes.chat.core.ui.components.item.ConversationItem
import com.github.woodsmarshes.chat.core.ui.components.state.EmptyContent
import com.github.woodsmarshes.chat.core.ui.components.state.ErrorContent
import com.github.woodsmarshes.chat.core.ui.components.state.LoadingContent
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import com.github.woodsmarshes.chat.feature.search.model.SearchResultUiModel
import com.github.woodsmarshes.chat.feature.search.navigation.SearchType
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    type: SearchType,
    viewModel: SearchViewModel = koinViewModel(parameters = { parametersOf(type) }),
) {
    val uiState by viewModel.uiState.collectAsState()
    val strings = LocalStrings.current

    Scaffold(
        topBar = {
            ChatTopAppBar(
                title = strings.searchTitle,
                showBackButton = true,
                onBackClick = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .windowInsetsPadding(WindowInsets.ime)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(strings.searchPlaceholder) },
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(16.dp))

            val hasQuery = uiState.query.trim().length >= 2
            when {
                uiState.notSupported -> EmptyContent(
                    message = strings.searchNotSupported,
                    modifier = Modifier.fillMaxSize(),
                )
                uiState.isLoading && uiState.results.isEmpty() -> LoadingContent(
                    message = strings.loading,
                    modifier = Modifier.fillMaxSize(),
                )
                uiState.error != null && uiState.results.isEmpty() -> ErrorContent(
                    message = uiState.error ?: strings.searchFailed,
                    onRetry = viewModel::retry,
                    modifier = Modifier.fillMaxSize(),
                )
                uiState.results.isEmpty() -> EmptyContent(
                    message = if (hasQuery) strings.searchNoResults else strings.searchPrompt,
                    modifier = Modifier.fillMaxSize(),
                )
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(
                        items = uiState.results,
                        contentType = { it::class },
                    ) { result ->
                        when (result) {
                            is SearchResultUiModel.Contact -> ContactItem(
                                contact = result.contact,
                                onClick = { /* TODO: open contact profile */ },
                            )
                            is SearchResultUiModel.Conversation -> ConversationItem(
                                conversation = result.conversation,
                                onClick = { /* TODO: open conversation */ },
                            )
                        }
                    }
                }
            }
        }
    }
}
