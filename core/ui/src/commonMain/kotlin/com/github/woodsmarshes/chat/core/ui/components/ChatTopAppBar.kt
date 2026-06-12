package com.github.woodsmarshes.chat.core.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import androidx.compose.material.icons.filled.Menu

/**
 * 共享 TopAppBar（参考 NiA 的 NiaTopAppBar，所有 Screen 统一使用）。
 *
 * @param showBackButton 显示返回箭头
 * @param showMenuButton 显示汉堡菜单按钮（抽屉触发）。优先于 showBackButton
 * @param onMenuClick 汉堡按钮点击回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopAppBar(
    title: String,
    showBackButton: Boolean = false,
    onBackClick: () -> Unit = {},
    showMenuButton: Boolean = false,
    onMenuClick: (() -> Unit)? = null,
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
                            contentDescription = "菜单",
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
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(),
    )
}
