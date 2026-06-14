package com.github.woodsmarshes.chat.feature.search.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.woodsmarshes.chat.core.ui.components.ChatTopAppBar
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import com.github.woodsmarshes.chat.feature.search.navigation.SearchType
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    type: SearchType,
    viewModel: SearchViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    val searchBarState = rememberSearchBarState(initialValue = SearchBarValue.Expanded)
    val textFieldState = rememberTextFieldState()

    ExpandedFullScreenSearchBar(
        state = searchBarState,
        inputField = {
            SearchBarDefaults.InputField(
                textFieldState = textFieldState,
                searchBarState = searchBarState,
                onSearch = { query ->  },
                placeholder = { Text("搜索${type.name.lowercase()}") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    IconButton(onClick = { textFieldState.edit { replace(0, length, "") } }) {
                        Icon(Icons.Default.Close, null)
                    }
                }
            )
        },
        colors = SearchBarDefaults.colors(),
    ) {

    }

    Scaffold(
        topBar = {
            ChatTopAppBar(
                title = LocalStrings.current.searchTitle,
                showBackButton = true,
                onBackClick = onBack,
            )

        }
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
                placeholder = { Text(LocalStrings.current.searchPlaceholder) },
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(LocalStrings.current.searchPrompt, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
