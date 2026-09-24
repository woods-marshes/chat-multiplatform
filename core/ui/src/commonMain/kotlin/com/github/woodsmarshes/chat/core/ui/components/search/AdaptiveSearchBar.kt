package com.github.woodsmarshes.chat.core.ui.components.search

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExpandedDockedSearchBarWithGap
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.window.core.layout.WindowSizeClass
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Material 3 adaptive search bar following the official composition: a
 * collapsed [SearchBar] plus an expanded search view sharing one
 * [SearchBarState] and one [SearchBarDefaults.InputField].
 *
 * The expanded form follows the M3 guidance: a full-screen search view on
 * compact widths and a docked popup with a gap on medium+ widths, so the
 * detail pane of a list-detail layout stays visible.
 *
 * The component owns the query text state and applies a debounce plus a
 * minimum-length policy before notifying [onSearchQuery]; an empty string is
 * delivered when the query falls below [minQueryLength] or is cleared, so
 * callers can drop stale results immediately. The input field always uses
 * IME action Search (enforced by the M3 input field), and the expanded view
 * handles predictive back and scrim dismissal internally.
 *
 * @param onQueryChange called on every text edit with the raw query.
 * @param onSearchQuery called (debounced) with the trimmed query once it
 *   reaches [minQueryLength]; called with an empty string when it does not.
 * @param modifier applied to the collapsed search bar.
 * @param state hoisted search bar state; pass a remembered
 *   [rememberSearchBarState] value to collapse programmatically, e.g. after
 *   a result is picked.
 * @param placeholder hint shown in the input field.
 * @param navigationIcon optional icon shown at the leading edge while
 *   collapsed (e.g. a drawer menu); while expanded the bar always shows a
 *   back arrow that collapses the search view.
 * @param onNavigationIconClick click handler for [navigationIcon].
 * @param trailingAffordance optional slot rendered at the trailing edge of
 *   the collapsed pill while the query is empty (e.g. the account avatar
 *   affordance); the clear button takes over the slot as soon as text is
 *   entered.
 * @param minQueryLength minimum trimmed length before [onSearchQuery] fires.
 * @param debounceMillis delay between the last keystroke and [onSearchQuery].
 * @param onExpandedChange notified when the search view expands or collapses.
 * @param searchViewContent results rendered inside the expanded search view.
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    FlowPreview::class,
)
@Composable
fun AdaptiveSearchBar(
    onQueryChange: (String) -> Unit,
    onSearchQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
    state: SearchBarState = rememberSearchBarState(),
    placeholder: String? = null,
    navigationIcon: ImageVector? = null,
    onNavigationIconClick: (() -> Unit)? = null,
    trailingAffordance: (@Composable () -> Unit)? = null,
    minQueryLength: Int = 2,
    debounceMillis: Long = 300L,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    searchViewContent: @Composable ColumnScope.() -> Unit,
) {
    val strings = LocalStrings.current
    val textFieldState = rememberTextFieldState()
    val scope = rememberCoroutineScope()

    val currentOnQueryChange by rememberUpdatedState(onQueryChange)
    val currentOnSearchQuery by rememberUpdatedState(onSearchQuery)
    val currentOnExpandedChange by rememberUpdatedState(onExpandedChange)

    // Raw query stream for callers that mirror the text in their own state.
    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }
            .collect { currentOnQueryChange(it) }
    }

    // Debounced search stream; below-minimum queries are reported as empty.
    LaunchedEffect(textFieldState, minQueryLength, debounceMillis) {
        snapshotFlow { textFieldState.text.toString().trim() }
            .debounce(debounceMillis)
            .distinctUntilChanged()
            .collect { query ->
                currentOnSearchQuery(if (query.length >= minQueryLength) query else "")
            }
    }

    LaunchedEffect(state) {
        snapshotFlow { state.targetValue == SearchBarValue.Expanded }
            .collect { currentOnExpandedChange?.invoke(it) }
    }

    val inputField: @Composable () -> Unit = {
        SearchBarDefaults.InputField(
            textFieldState = textFieldState,
            searchBarState = state,
            onSearch = { query ->
                val trimmed = query.trim()
                currentOnSearchQuery(if (trimmed.length >= minQueryLength) trimmed else "")
            },
            placeholder = placeholder?.let { hint -> { Text(hint) } },
            leadingIcon = {
                if (state.targetValue == SearchBarValue.Expanded) {
                    IconButton(onClick = { scope.launch { state.animateToCollapsed() } }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.backCd,
                        )
                    }
                } else if (navigationIcon != null && onNavigationIconClick != null) {
                    IconButton(onClick = onNavigationIconClick) {
                        Icon(imageVector = navigationIcon, contentDescription = strings.menuCd)
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                    )
                }
            },
            trailingIcon = {
                when {
                    textFieldState.text.isNotEmpty() -> {
                        IconButton(onClick = { textFieldState.edit { replace(0, length, "") } }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = strings.searchClearCd,
                            )
                        }
                    }
                    state.targetValue == SearchBarValue.Collapsed && trailingAffordance != null ->
                        trailingAffordance()
                }
            },
        )
    }

    // Collapsed pill. Ctrl+K expands it from the keyboard on desktop.
    SearchBar(
        state = state,
        inputField = inputField,
        modifier = modifier.onPreviewKeyEvent { event ->
            if (
                event.type == KeyEventType.KeyDown &&
                event.key == Key.K &&
                event.isCtrlPressed &&
                state.targetValue == SearchBarValue.Collapsed
            ) {
                scope.launch { state.animateToExpanded() }
                true
            } else {
                false
            }
        },
    )

    // Expanded search view: docked popup on medium+ widths (keeps the detail
    // pane of a list-detail layout visible), full screen otherwise.
    val useDockedSearch = currentWindowAdaptiveInfoV2().windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    if (useDockedSearch) {
        ExpandedDockedSearchBarWithGap(
            state = state,
            inputField = inputField,
            content = searchViewContent,
        )
    } else {
        ExpandedFullScreenSearchBar(
            state = state,
            inputField = inputField,
            content = searchViewContent,
        )
    }
}
