package com.github.woodsmarshes.chat.feature.search.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.woodsmarshes.chat.core.ui.components.ChatTopAppBar
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    viewModel: SearchViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

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
