package com.github.woodsmarshes.chat.feature.contacts.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.github.woodsmarshes.chat.core.ui.components.AlphabetIndexBar
import com.github.woodsmarshes.chat.core.ui.components.LocalAccountAffordance
import com.github.woodsmarshes.chat.core.ui.components.item.ContactItem
import com.github.woodsmarshes.chat.core.ui.components.search.AdaptiveSearchBar
import com.github.woodsmarshes.chat.core.ui.components.shimmer.ContactSkeleton
import com.github.woodsmarshes.chat.core.ui.components.shimmer.ListSkeleton
import com.github.woodsmarshes.chat.core.ui.components.state.EmptyContent
import com.github.woodsmarshes.chat.core.ui.components.state.ErrorContent
import com.github.woodsmarshes.chat.core.ui.components.state.ListScreenScaffold
import com.github.woodsmarshes.chat.core.ui.components.state.LoadingContent
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

private val indexLetters = ('A'..'Z').map { it.toString() } + "#"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    onContactClick: (String) -> Unit,
    onMenuClick: (() -> Unit)? = null,
    viewModel: ContactsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val searchBarState = rememberSearchBarState()
    val strings = LocalStrings.current
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            AdaptiveSearchBar(
                onQueryChange = { searchQuery = it },
                onSearchQuery = viewModel::onSearchQuery,
                state = searchBarState,
                placeholder = strings.searchContactsPlaceholder,
                navigationIcon = if (onMenuClick != null) Icons.Default.Menu else null,
                onNavigationIconClick = onMenuClick,
                trailingAffordance = LocalAccountAffordance.current,
                searchViewContent = {
                    when {
                        uiState.isSearching && uiState.searchResults.isEmpty() -> LoadingContent(
                            message = strings.loading,
                            modifier = Modifier.fillMaxSize(),
                        )
                        uiState.searchError != null && uiState.searchResults.isEmpty() -> ErrorContent(
                            message = uiState.searchError ?: strings.searchFailed,
                            onRetry = viewModel::retrySearch,
                            modifier = Modifier.fillMaxSize(),
                        )
                        uiState.searchResults.isEmpty() -> EmptyContent(
                            message = if (searchQuery.isNotBlank()) strings.searchNoResults else strings.searchPrompt,
                            modifier = Modifier.fillMaxSize(),
                        )
                        else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(
                                items = uiState.searchResults,
                                key = { it.id },
                            ) { contact ->
                                ContactItem(
                                    contact = contact,
                                    onClick = {
                                        scope.launch { searchBarState.animateToCollapsed() }
                                        onContactClick(contact.id.toString())
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { /* TODO: 添加联系人 */ }) {
                Icon(Icons.Default.Add, contentDescription = strings.addContactCd)
            }
        },
    ) { innerPadding ->
        ListScreenScaffold(
            isLoading = uiState.isLoading,
            error = uiState.error,
            isEmpty = uiState.contacts.isEmpty(),
            emptyMessage = strings.noContacts,
            loadingContent = {
                ListSkeleton(count = 8, skeleton = { ContactSkeleton() })
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                ) {
                    items(
                        items = uiState.contacts,
                        key = { it.id },
                    ) { contact ->
                        ContactItem(
                            contact = contact,
                            onClick = { onContactClick(contact.id.toString()) },
                        )
                    }
                }
                AlphabetIndexBar(
                    letters = indexLetters,
                    onLetterSelected = { letter ->
                        val idx = uiState.contacts.indexOfFirst {
                            (it.displayName?.firstOrNull() ?: it.username.firstOrNull())?.uppercaseChar() == letter.firstOrNull()
                        }
                        if (idx >= 0) {
                            scope.launch { listState.animateScrollToItem(idx) }
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                )
            }
        }
    }
}
