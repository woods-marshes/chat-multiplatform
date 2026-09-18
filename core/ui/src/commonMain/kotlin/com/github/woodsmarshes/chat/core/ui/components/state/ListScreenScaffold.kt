package com.github.woodsmarshes.chat.core.ui.components.state

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings

/** Which branch of a list screen should currently render. */
internal enum class ListScreenState {
    LOADING,
    ERROR,
    EMPTY,
    CONTENT,
}

/**
 * Shared state machine for list screens: loading beats error beats empty,
 * but only while there is no data to show — transient errors and refreshes
 * keep existing content visible.
 */
internal fun resolveListScreenState(
    isLoading: Boolean,
    hasError: Boolean,
    isEmpty: Boolean,
): ListScreenState = when {
    isLoading && isEmpty -> ListScreenState.LOADING
    hasError && isEmpty -> ListScreenState.ERROR
    isEmpty -> ListScreenState.EMPTY
    else -> ListScreenState.CONTENT
}

/**
 * Shared Loading / Error / Empty / Content scaffold for list screens.
 *
 * - [isLoading] with no data renders [loadingContent] (a spinner by default);
 * - [error] with no data renders [ErrorContent] with optional [onRetry];
 * - an otherwise empty data set renders [EmptyContent] with [emptyMessage];
 * - anything else renders [content].
 *
 * [modifier] is applied to every branch root, so screens can pass their
 * layout padding once instead of repeating it per branch.
 */
@Composable
fun ListScreenScaffold(
    isLoading: Boolean,
    error: String?,
    isEmpty: Boolean,
    emptyMessage: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    loadingContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    when (resolveListScreenState(
        isLoading = isLoading,
        hasError = error != null,
        isEmpty = isEmpty,
    )) {
        ListScreenState.LOADING -> {
            if (loadingContent != null) {
                Box(modifier = modifier) { loadingContent() }
            } else {
                LoadingContent(
                    modifier = modifier,
                    message = LocalStrings.current.loading,
                )
            }
        }
        ListScreenState.ERROR -> ErrorContent(
            message = error ?: "",
            onRetry = onRetry,
            retryLabel = LocalStrings.current.retry,
            modifier = modifier,
        )
        ListScreenState.EMPTY -> EmptyContent(
            message = emptyMessage,
            modifier = modifier,
        )
        ListScreenState.CONTENT -> Box(modifier = modifier) { content() }
    }
}
