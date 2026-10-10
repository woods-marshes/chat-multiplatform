package com.github.woodsmarshes.chat.core.ui.components.item

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.woodsmarshes.chat.core.ui.theme.isMiuixTheme
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    if (isMiuixTheme()) {
        SmallTitle(
            text = title,
            modifier = modifier.fillMaxWidth(),
            insideMargin = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        )
    } else {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    danger: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
) {
    if (isMiuixTheme()) {
        val titleColors = if (danger) {
            BasicComponentColors(
                color = MiuixTheme.colorScheme.error,
                disabledColor = MiuixTheme.colorScheme.disabledOnSurface,
            )
        } else {
            BasicComponentDefaults.titleColor()
        }
        BasicComponent(
            title = title,
            summary = subtitle,
            titleColor = titleColors,
            modifier = modifier.fillMaxWidth(),
            onClick = onClick,
            startAction = {
                MiuixIcon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 14.dp).size(22.dp),
                    tint = if (danger) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary,
                )
            },
            endActions = if (trailing != null || onClick != null) {
                {
                    if (trailing != null) {
                        trailing()
                    } else if (onClick != null) {
                        MiuixIcon(
                            imageVector = MiuixIcons.Basic.ArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        )
                    }
                }
            } else {
                null
            },
        )
    } else {
        val titleColor = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface

        Row(
            modifier = modifier
                .fillMaxWidth()
                .clickable(enabled = onClick != null) { onClick?.invoke() }
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = titleColor,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (trailing != null) {
                trailing()
            } else if (onClick != null) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun SettingsItemWithSwitch(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    if (isMiuixTheme()) {
        BasicComponent(
            title = title,
            summary = subtitle,
            modifier = modifier.fillMaxWidth(),
            onClick = { onCheckedChange(!checked) },
            role = Role.Switch,
            startAction = {
                MiuixIcon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 14.dp).size(22.dp),
                    tint = MiuixTheme.colorScheme.primary,
                )
            },
            endActions = {
                MiuixSwitch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                )
            },
        )
    } else {
        SettingsItem(
            icon = icon,
            title = title,
            subtitle = subtitle,
            modifier = modifier.toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
            trailing = {
                Switch(
                    checked = checked,
                    onCheckedChange = null,
                )
            },
        )
    }
}

