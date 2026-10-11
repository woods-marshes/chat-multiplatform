package com.github.woodsmarshes.chat.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.navigation3.LocalListDetailSceneScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import com.github.woodsmarshes.chat.core.ui.theme.isMiuixTheme
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Whether the current composable is rendered inside a multi-pane
 * ListDetailScene with more than one pane visible side-by-side.
 *
 * When `true`, detail panes omit their leading back arrow (the list pane is
 * already visible alongside them), list panes highlight the active item, and
 * extra panes show a close (`X`) button instead of a back arrow. When `false`
 * (single-pane compact/medium layouts), detail and extra panes show a standard
 * back arrow.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun isInListDetailScene(): Boolean {
    val scope = LocalListDetailSceneScope.current ?: return false
    val value = scope.scaffoldTransitionScope.scaffoldStateTransition.targetState
    var visiblePanes = 0
    if (value.primary != PaneAdaptedValue.Hidden) visiblePanes++
    if (value.secondary != PaneAdaptedValue.Hidden) visiblePanes++
    if (value.tertiary != PaneAdaptedValue.Hidden) visiblePanes++
    return visiblePanes > 1
}

/**
 * 共享 TopAppBar（支持 Material 3 与 Miuix 双主题自动切换）。
 *
 * 固定为 64.dp 内容高度（与 [ChatConversationTopBar] 完全一致），确保在多栏并排
 * 布局（如左栏聊天页 + 右栏群聊信息/个人资料页）下底部分割线严格对齐。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = false,
    showCloseButton: Boolean = false,
    onBackClick: () -> Unit = {},
    showMenuButton: Boolean = false,
    onMenuClick: (() -> Unit)? = null,
    onSearchClick: (() -> Unit)? = null,
    showAccountAffordance: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val isMiuix = isMiuixTheme()
    val containerColor = if (isMiuix) MiuixTheme.colorScheme.surface else MaterialTheme.colorScheme.surface
    val titleColor = if (isMiuix) MiuixTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface

    Surface(
        color = containerColor,
        tonalElevation = 0.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .height(64.dp)
                    .padding(horizontal = 4.dp),
            ) {
                Row(
                    modifier = Modifier.align(Alignment.CenterStart),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when {
                        showMenuButton && onMenuClick != null -> {
                            if (isMiuix) {
                                MiuixIconButton(onClick = onMenuClick) {
                                    MiuixIcon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = LocalStrings.current.menuCd,
                                        tint = titleColor,
                                    )
                                }
                            } else {
                                IconButton(onClick = onMenuClick) {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = LocalStrings.current.menuCd,
                                    )
                                }
                            }
                        }
                        showCloseButton -> {
                            if (isMiuix) {
                                MiuixIconButton(onClick = onBackClick) {
                                    MiuixIcon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = LocalStrings.current.backCd,
                                        tint = titleColor,
                                    )
                                }
                            } else {
                                IconButton(onClick = onBackClick) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = LocalStrings.current.backCd,
                                    )
                                }
                            }
                        }
                        showBackButton -> {
                            if (isMiuix) {
                                MiuixIconButton(onClick = onBackClick) {
                                    MiuixIcon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = LocalStrings.current.backCd,
                                        tint = titleColor,
                                    )
                                }
                            } else {
                                IconButton(onClick = onBackClick) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = LocalStrings.current.backCd,
                                    )
                                }
                            }
                        }
                    }
                }

                if (isMiuix) {
                    MiuixText(
                        text = title,
                        style = MiuixTheme.textStyles.headline2,
                        fontWeight = FontWeight.SemiBold,
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 56.dp),
                    )
                } else {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 56.dp),
                    )
                }

                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onSearchClick != null) {
                        if (isMiuix) {
                            MiuixIconButton(onClick = onSearchClick) {
                                MiuixIcon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = LocalStrings.current.searchTitle,
                                    tint = titleColor,
                                )
                            }
                        } else {
                            IconButton(onClick = onSearchClick) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = LocalStrings.current.searchTitle,
                                )
                            }
                        }
                    }
                    actions()
                    if (showAccountAffordance) {
                        Spacer(Modifier.width(4.dp))
                        LocalAccountAffordance.current?.invoke()
                    }
                }
            }
            AppHorizontalDivider(thickness = 0.5.dp)
        }
    }
}

