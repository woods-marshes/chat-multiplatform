package com.github.woodsmarshes.chat.feature.search.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.github.woodsmarshes.chat.core.ui.components.item.ContactItem
import com.github.woodsmarshes.chat.core.ui.components.item.ConversationItem
import com.github.woodsmarshes.chat.core.ui.components.search.AdaptiveSearchBar
import com.github.woodsmarshes.chat.core.ui.components.state.EmptyContent
import com.github.woodsmarshes.chat.core.ui.components.state.ErrorContent
import com.github.woodsmarshes.chat.core.ui.components.state.LoadingContent
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import com.github.woodsmarshes.chat.feature.search.model.SearchResultUiModel
import com.github.woodsmarshes.chat.feature.search.navigation.SearchType
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
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
            AdaptiveSearchBar(
                onQueryChange = viewModel::onQueryChanged,
                onSearchQuery = viewModel::onSearchQuery,
                placeholder = strings.searchPlaceholder,
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationIconClick = onBack,
                searchViewContent = {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.ime)
                    ) {
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
                },
            )
        },
    ) { padding ->
        // Collapsed state body. This route is only reached from Settings
        // (which surfaces "not supported" in the expanded view above).
        EmptyContent(
            message = strings.searchPrompt,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}
