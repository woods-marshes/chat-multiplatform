package com.github.woodsmarshes.chat.core.ui.components.search

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
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
/**
 * A search bar state that deliberately skips saveable restoration.
 *
 * Nav entry decorators keep saveable state alive across top-level tab
 * switches, so rememberSearchBarState() resurrects a search view the user
 * left expanded — arriving on a tab with the search already open (and its
 * old query firing behind the collapsed-looking bar) reads as a bug.
 * Every arrival starts collapsed and empty; within one visit the state
 * behaves normally.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberFreshSearchBarState(): SearchBarState =
    remember {
        SearchBarState(
            initialValue = SearchBarValue.Collapsed,
            // Mirrors material3's standard-motion defaults (MotionScheme
            // SlowSpatial / DefaultSpatial) — the public constructor
            // requires the specs explicitly.
            animationSpecForExpand = spring(dampingRatio = 0.9f, stiffness = 200f),
            animationSpecForCollapse = spring(dampingRatio = 0.9f, stiffness = 700f),
        )
    }

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
    state: SearchBarState = rememberFreshSearchBarState(),
    placeholder: String? = null,
    navigationIcon: ImageVector? = null,
    onNavigationIconClick: (() -> Unit)? = null,
    trailingAffordance: (@Composable () -> Unit)? = null,
    // 1, not 2: group and contact names can legitimately be a single
    // character (a group named "3"), and one CJK character carries as much
    // information as a whole Latin word. The debounce already protects the
    // server from per-keystroke load.
    minQueryLength: Int = 1,
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

    // Clear any saveable-restored query when arriving in a collapsed state so
    // an old query does not fire through the debounced stream behind the pill.
    LaunchedEffect(Unit) {
        if (state.targetValue == SearchBarValue.Collapsed && textFieldState.text.isNotEmpty()) {
            textFieldState.edit { replace(0, length, "") }
        }
    }

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
            // Only focusable once expanded: on Compose Desktop LocalInputModeManager
            // defaults to InputMode.Touch, and M3's InputField expands unconditionally
            // on onFocusChanged when in touch mode — so any focus reassignment on tab
            // switch otherwise pops the search view open by itself. Pointer clicks on
            // the collapsed pill still expand via DetectClickFromInteractionSource,
            // and the expanded view's FocusRequester focuses the input field as soon
            // as expansion starts.
            modifier = Modifier
                .fillMaxWidth()
                .focusProperties {
                    canFocus = state.targetValue == SearchBarValue.Expanded
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
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        SearchBar(
            state = state,
            inputField = inputField,
            modifier = Modifier
                .fillMaxWidth()
                .onPreviewKeyEvent { event ->
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
    }

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
