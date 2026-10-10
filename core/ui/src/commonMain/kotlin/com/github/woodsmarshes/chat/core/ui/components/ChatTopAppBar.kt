package com.github.woodsmarshes.chat.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.navigation3.LocalListDetailSceneScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import com.github.woodsmarshes.chat.core.ui.theme.isMiuixTheme
import top.yukonga.miuix.kmp.basic.HorizontalDivider as MiuixHorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
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
 * @param showBackButton 显示返回箭头
 * @param showCloseButton 显示关闭按钮（用于大屏最右侧的 extraPane 面板）
 * @param showMenuButton 显示汉堡菜单按钮（抽屉触发）。优先于 showBackButton
 * @param onMenuClick 汉堡按钮点击回调
 * @param onSearchClick 非空时在操作区渲染搜索入口
 * @param showAccountAffordance 顶栏末尾渲染当前用户头像（仅顶层页面使用；
 *   头像内容来自 [LocalAccountAffordance]，紧凑布局由 shell 提供，宽屏为 null）
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
    if (isMiuixTheme()) {
        Column(modifier = modifier.fillMaxWidth()) {
            SmallTopAppBar(
                title = title,
                color = MiuixTheme.colorScheme.surface,
                titleColor = MiuixTheme.colorScheme.onSurface,
                navigationIcon = {
                    when {
                        showMenuButton && onMenuClick != null -> {
                            MiuixIconButton(onClick = onMenuClick) {
                                MiuixIcon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = LocalStrings.current.menuCd,
                                    tint = MiuixTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        showCloseButton -> {
                            MiuixIconButton(onClick = onBackClick) {
                                MiuixIcon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = LocalStrings.current.backCd,
                                    tint = MiuixTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        showBackButton -> {
                            MiuixIconButton(onClick = onBackClick) {
                                MiuixIcon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = LocalStrings.current.backCd,
                                    tint = MiuixTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (onSearchClick != null) {
                        MiuixIconButton(onClick = onSearchClick) {
                            MiuixIcon(
                                imageVector = Icons.Default.Search,
                                contentDescription = LocalStrings.current.searchTitle,
                                tint = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    actions()
                    if (showAccountAffordance) {
                        Spacer(Modifier.width(4.dp))
                        LocalAccountAffordance.current?.invoke()
                    }
                },
            )
            MiuixHorizontalDivider(
                thickness = 0.5.dp,
                color = MiuixTheme.colorScheme.dividerLine,
            )
        }
    } else {
        Column(modifier = modifier.fillMaxWidth()) {
            CenterAlignedTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    when {
                        showMenuButton && onMenuClick != null -> {
                            IconButton(onClick = onMenuClick) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = LocalStrings.current.menuCd,
                                )
                            }
                        }
                        showCloseButton -> {
                            IconButton(onClick = onBackClick) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = LocalStrings.current.backCd,
                                )
                            }
                        }
                        showBackButton -> {
                            IconButton(onClick = onBackClick) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = LocalStrings.current.backCd,
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (onSearchClick != null) {
                        IconButton(onClick = onSearchClick) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = LocalStrings.current.searchTitle,
                            )
                        }
                    }
                    actions()
                    if (showAccountAffordance) {
                        Spacer(Modifier.width(4.dp))
                        LocalAccountAffordance.current?.invoke()
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
        }
    }
}
