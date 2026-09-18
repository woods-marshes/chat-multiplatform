package com.github.woodsmarshes.chat.core.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings

/**
 * 共享 TopAppBar（参考 NiA 的 NiaTopAppBar，所有 Screen 统一使用）。
 *
 * @param showBackButton 显示返回箭头
 * @param showMenuButton 显示汉堡菜单按钮（抽屉触发）。优先于 showBackButton
 * @param onMenuClick 汉堡按钮点击回调
 * @param onSearchClick 非空时在操作区渲染胶囊搜索入口（替代独立搜索图标）
 * @param showAccountAffordance 顶栏末尾渲染当前用户头像（仅顶层页面使用；
 *   头像内容来自 [LocalAccountAffordance]，紧凑布局由 shell 提供，宽屏为 null）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopAppBar(
    title: String,
    showBackButton: Boolean = false,
    onBackClick: () -> Unit = {},
    showMenuButton: Boolean = false,
    onMenuClick: (() -> Unit)? = null,
    onSearchClick: (() -> Unit)? = null,
    showAccountAffordance: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
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
                Surface(
                    onClick = onSearchClick,
                    shape = RoundedCornerShape(percent = 50),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.defaultMinSize(minWidth = 120.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = LocalStrings.current.searchTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
            }
            actions()
            if (showAccountAffordance) {
                Spacer(Modifier.width(4.dp))
                LocalAccountAffordance.current?.invoke()
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(),
    )
}
