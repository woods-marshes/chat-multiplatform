package com.github.woodsmarshes.chat.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults as M3ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator as M3CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton as M3ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton as M3FloatingActionButton
import androidx.compose.material3.HorizontalDivider as M3HorizontalDivider
import androidx.compose.material3.Icon as M3Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Tab
import androidx.compose.material3.Text as M3Text
import androidx.compose.material3.TextButton as M3TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState as rememberM3PullToRefreshState
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.github.woodsmarshes.chat.core.ui.theme.isMiuixTheme
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.FloatingActionButton as MiuixFloatingActionButton
import top.yukonga.miuix.kmp.basic.HorizontalDivider as MiuixHorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator as MiuixInfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.PullToRefresh as MiuixPullToRefresh
import top.yukonga.miuix.kmp.basic.TabRow as MiuixTabRow
import top.yukonga.miuix.kmp.basic.TabRowWithContour as MiuixTabRowWithContour
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults as MiuixTextFieldDefaults
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState as rememberMiuixPullToRefreshState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * Dual-style progress indicator: renders Miuix's [MiuixInfiniteProgressIndicator]
 * in Miuix mode and Material 3's [M3CircularProgressIndicator] in M3 mode.
 */
@Composable
fun AppProgressIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    strokeWidth: Dp = 2.5.dp,
    color: Color = Color.Unspecified,
) {
    if (isMiuixTheme()) {
        val resolvedColor = if (color != Color.Unspecified) color else MiuixTheme.colorScheme.primary
        MiuixInfiniteProgressIndicator(
            modifier = modifier,
            size = size,
            strokeWidth = strokeWidth,
            color = resolvedColor,
        )
    } else {
        val resolvedColor = if (color != Color.Unspecified) color else MaterialTheme.colorScheme.primary
        M3CircularProgressIndicator(
            modifier = modifier.size(size),
            strokeWidth = strokeWidth,
            color = resolvedColor,
        )
    }
}

/**
 * Dual-style FloatingActionButton: renders Miuix's [MiuixFloatingActionButton]
 * in Miuix mode and Material 3's [M3FloatingActionButton] in M3 mode.
 */
@Composable
fun AppFloatingActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    if (isMiuixTheme()) {
        MiuixFloatingActionButton(
            onClick = onClick,
            modifier = modifier,
            containerColor = MiuixTheme.colorScheme.primary,
            minWidth = 56.dp,
            minHeight = 56.dp,
        ) {
            MiuixIcon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MiuixTheme.colorScheme.onPrimary,
            )
        }
    } else {
        M3FloatingActionButton(
            onClick = onClick,
            modifier = modifier,
        ) {
            M3Icon(
                imageVector = icon,
                contentDescription = contentDescription,
            )
        }
    }
}

/**
 * Dual-style ExtendedFloatingActionButton.
 */
@Composable
fun AppExtendedFloatingActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    if (isMiuixTheme()) {
        MiuixFloatingActionButton(
            onClick = onClick,
            modifier = modifier,
            containerColor = MiuixTheme.colorScheme.primary,
            minWidth = 96.dp,
            minHeight = 52.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiuixIcon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onPrimary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                MiuixText(
                    text = label,
                    color = MiuixTheme.colorScheme.onPrimary,
                    style = MiuixTheme.textStyles.button,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    } else {
        M3ExtendedFloatingActionButton(
            onClick = onClick,
            modifier = modifier,
            icon = { M3Icon(icon, contentDescription = null) },
            text = { M3Text(label) },
        )
    }
}

/**
 * Dual-style TabRow: renders Miuix's [MiuixTabRowWithContour] (or [MiuixTabRow])
 * in Miuix mode and Material 3's [PrimaryTabRow] / [SecondaryTabRow] in M3 mode.
 */
@Composable
fun AppTabRow(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    withContour: Boolean = true,
) {
    if (isMiuixTheme()) {
        if (withContour) {
            MiuixTabRowWithContour(
                tabs = tabs,
                selectedTabIndex = selectedTabIndex,
                onTabSelected = onTabSelected,
                modifier = modifier.fillMaxWidth(),
            )
        } else {
            MiuixTabRow(
                tabs = tabs,
                selectedTabIndex = selectedTabIndex,
                onTabSelected = onTabSelected,
                modifier = modifier.fillMaxWidth(),
            )
        }
    } else {
        if (withContour) {
            SecondaryTabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = modifier.fillMaxWidth(),
                containerColor = Color.Transparent,
                divider = {},
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { onTabSelected(index) },
                        text = { M3Text(title) },
                    )
                }
            }
        } else {
            PrimaryTabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = modifier.fillMaxWidth(),
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { onTabSelected(index) },
                        text = { M3Text(title) },
                    )
                }
            }
        }
    }
}

/**
 * Dual-style PullToRefresh container: renders Miuix's [MiuixPullToRefresh]
 * in Miuix mode and Material 3's [PullToRefreshBox] in M3 mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (isMiuixTheme()) {
        val state = rememberMiuixPullToRefreshState()
        MiuixPullToRefresh(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = modifier.fillMaxSize(),
            pullToRefreshState = state,
            color = MiuixTheme.colorScheme.primary,
            content = content,
        )
    } else {
        val state = rememberM3PullToRefreshState()
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = modifier.fillMaxSize(),
            state = state,
            indicator = {
                Indicator(
                    modifier = Modifier.align(Alignment.TopCenter),
                    isRefreshing = isRefreshing,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    state = state,
                )
            },
        ) {
            content()
        }
    }
}

/**
 * Dual-style BottomSheet: renders Miuix's [WindowBottomSheet] in Miuix mode
 * and Material 3's [ModalBottomSheet] in M3 mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppModalBottomSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (isMiuixTheme()) {
        WindowBottomSheet(
            show = show,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            title = title,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                content = content,
            )
        }
    } else {
        val sheetState = rememberModalBottomSheetState()
        val scope = rememberCoroutineScope()
        var isMounted by remember { mutableStateOf(show) }
        val currentShow by rememberUpdatedState(show)

        LaunchedEffect(show) {
            if (show) {
                isMounted = true
            } else if (isMounted) {
                scope.launch {
                    sheetState.hide()
                }.invokeOnCompletion {
                    if (!sheetState.isVisible && !currentShow) {
                        isMounted = false
                    }
                }
            }
        }

        if (isMounted) {
            ModalBottomSheet(
                onDismissRequest = {
                    isMounted = false
                    onDismissRequest()
                },
                modifier = modifier,
                sheetState = sheetState,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                ) {
                    if (title != null) {
                        M3Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    content()
                }
            }
        }
    }
}

/**
 * Dual-style Dialog: renders Miuix's [WindowDialog] with Miuix [MiuixTextButton]
 * actions in Miuix mode, and Material 3's [AlertDialog] in M3 mode.
 */
@Composable
fun AppAlertDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    confirmLabel: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    confirmDanger: Boolean = false,
    dismissLabel: String? = null,
    onDismiss: (() -> Unit)? = null,
    dismissEnabled: Boolean = true,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    if (isMiuixTheme()) {
        WindowDialog(
            show = show,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            title = title,
            summary = summary,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (content != null) {
                    content()
                    Spacer(modifier = Modifier.height(16.dp))
                }
                if (dismissLabel != null || confirmLabel != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (dismissLabel != null && onDismiss != null) {
                            MiuixTextButton(
                                text = dismissLabel,
                                onClick = onDismiss,
                                enabled = dismissEnabled,
                                modifier = Modifier.weight(1f),
                                colors = MiuixButtonDefaults.textButtonColors(),
                            )
                        }
                        if (confirmLabel != null && onConfirm != null) {
                            val confirmColors = if (confirmDanger) {
                                MiuixButtonDefaults.textButtonColors(
                                    color = MiuixTheme.colorScheme.error,
                                    textColor = MiuixTheme.colorScheme.onError,
                                )
                            } else {
                                MiuixButtonDefaults.textButtonColorsPrimary()
                            }
                            MiuixTextButton(
                                text = confirmLabel,
                                onClick = onConfirm,
                                enabled = confirmEnabled,
                                modifier = Modifier.weight(1f),
                                colors = confirmColors,
                            )
                        }
                    }
                }
            }
        }
    } else if (show) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            title = { M3Text(title) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (summary != null) {
                        M3Text(summary)
                        if (content != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                    content?.invoke(this)
                }
            },
            confirmButton = {
                if (confirmLabel != null && onConfirm != null) {
                    M3TextButton(
                        onClick = onConfirm,
                        enabled = confirmEnabled,
                        colors = if (confirmDanger) {
                            M3ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            )
                        } else {
                            M3ButtonDefaults.textButtonColors()
                        },
                    ) {
                        M3Text(confirmLabel)
                    }
                }
            },
            dismissButton = if (dismissLabel != null && onDismiss != null) {
                {
                    M3TextButton(
                        onClick = onDismiss,
                        enabled = dismissEnabled,
                    ) {
                        M3Text(dismissLabel)
                    }
                }
            } else {
                null
            },
        )
    }
}

/**
 * Dual-style TextField: renders Miuix's [MiuixTextField] in Miuix mode and
 * Material 3's [OutlinedTextField] in M3 mode.
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    errorText: String? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    if (isMiuixTheme()) {
        Column(modifier = modifier) {
            val borderColor = if (isError) {
                MiuixTheme.colorScheme.error
            } else {
                MiuixTheme.colorScheme.primary
            }
            MiuixTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                label = label,
                useLabelAsPlaceholder = placeholder == null,
                insideMargin = DpSize(16.dp, 14.dp),
                colors = MiuixTextFieldDefaults.textFieldColors(
                    borderColor = borderColor,
                ),
                enabled = enabled,
                singleLine = singleLine,
                minLines = minLines,
                maxLines = maxLines,
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon,
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
            )
            if (isError && !errorText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                MiuixText(
                    text = errorText,
                    color = MiuixTheme.colorScheme.error,
                    style = MiuixTheme.textStyles.footnote1,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    } else {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { M3Text(label) },
            placeholder = placeholder?.let { hint -> { M3Text(hint) } },
            modifier = modifier,
            enabled = enabled,
            isError = isError,
            supportingText = if (isError && !errorText.isNullOrBlank()) {
                { M3Text(errorText) }
            } else {
                null
            },
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            visualTransformation = visualTransformation,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
        )
    }
}

/**
 * Dual-style horizontal divider.
 */
@Composable
fun AppHorizontalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = 0.5.dp,
) {
    if (isMiuixTheme()) {
        MiuixHorizontalDivider(
            modifier = modifier,
            thickness = thickness,
            color = MiuixTheme.colorScheme.dividerLine,
        )
    } else {
        M3HorizontalDivider(
            modifier = modifier,
            thickness = thickness,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
    }
}
