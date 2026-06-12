package com.github.woodsmarshes.chat.feature.contacts.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.github.woodsmarshes.chat.core.ui.components.AlphabetIndexBar
import com.github.woodsmarshes.chat.core.ui.components.ChatTopAppBar
import com.github.woodsmarshes.chat.core.ui.components.item.ContactItem
import com.github.woodsmarshes.chat.core.ui.components.shimmer.ContactSkeleton
import com.github.woodsmarshes.chat.core.ui.components.state.EmptyContent
import com.github.woodsmarshes.chat.core.ui.components.state.ErrorContent
import com.github.woodsmarshes.chat.core.ui.components.shimmer.ListSkeleton
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

private val indexLetters = ('A'..'Z').map { it.toString() } + "#"

@Composable
fun ContactsScreen(
    onContactClick: (String) -> Unit,
    onMenuClick: (() -> Unit)? = null,
    viewModel: ContactsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            ChatTopAppBar(
                title = LocalStrings.current.contactsTitle,
                showMenuButton = onMenuClick != null,
                onMenuClick = onMenuClick,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { /* TODO: 添加联系人 */ }) {
                Icon(Icons.Default.Add, contentDescription = "添加联系人")
            }
        },
    ) { innerPadding ->
        when {
            uiState.isLoading && uiState.contacts.isEmpty() -> {
                ListSkeleton(
                    modifier = Modifier.padding(innerPadding),
                    count = 8,
                    skeleton = { ContactSkeleton() }
                )
            }
            uiState.error != null && uiState.contacts.isEmpty() -> {
                ErrorContent(
                    message = uiState.error ?: LocalStrings.current.loadFailed,
                    modifier = Modifier.padding(innerPadding),
                )
            }
            uiState.contacts.isEmpty() && !uiState.isLoading -> {
                EmptyContent(
                    message = LocalStrings.current.noContacts,
                    modifier = Modifier.padding(innerPadding),
                )
            }
            else -> {
                Box(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
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
}
