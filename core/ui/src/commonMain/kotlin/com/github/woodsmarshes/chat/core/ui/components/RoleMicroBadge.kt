package com.github.woodsmarshes.chat.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.woodsmarshes.chat.core.model.ConversationRole
import com.github.woodsmarshes.chat.core.model.DarkThemeConfig
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import com.github.woodsmarshes.chat.core.ui.theme.LocalThemeConfig

/**
 * Refined micro-pill badge for group Owner ("群主") and Admin ("管理员").
 * Ordinary members (`MEMBER` / `PARTICIPANT`) are intentionally not badged.
 */
@Composable
fun RoleMicroBadge(
    role: ConversationRole?,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val label = when (role) {
        ConversationRole.OWNER -> strings.roleOwner
        ConversationRole.ADMIN -> strings.roleAdmin
        else -> return
    }

    val themeConfig = LocalThemeConfig.current
    val isDark = when (themeConfig.darkThemeConfig) {
        DarkThemeConfig.LIGHT -> false
        DarkThemeConfig.DARK -> true
        DarkThemeConfig.FOLLOW_SYSTEM -> isSystemInDarkTheme()
    }

    val (bgColor, textColor) = when (role) {
        ConversationRole.OWNER -> {
            val bg = Color(0xFFF59E0B).copy(alpha = if (isDark) 0.20f else 0.14f)
            val fg = if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706)
            bg to fg
        }
        ConversationRole.ADMIN -> {
            val primary = MaterialTheme.colorScheme.primary
            primary.copy(alpha = if (isDark) 0.18f else 0.12f) to primary
        }
        else -> return
    }

    Text(
        text = label,
        color = textColor,
        style = LocalTextStyle.current.copy(
            fontSize = 10.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Medium,
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
        ),
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .padding(horizontal = 5.dp, vertical = 1.5.dp),
    )
}
