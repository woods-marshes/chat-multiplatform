package com.github.woodsmarshes.chat.app.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.github.woodsmarshes.chat.core.ui.components.motion.AppMotion
import kotlin.math.roundToInt

private enum class CompactSlot { Navigation, Content }

/** One persistent bar; its drawing animation never resizes destination constraints. */
@Composable
internal fun CompactNavigationLayout(
    showNavigation: Boolean,
    navigation: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (bottomBarHeight: Dp) -> Unit,
) {
    val visibility by animateFloatAsState(
        targetValue = if (showNavigation) 1f else 0f,
        animationSpec = tween(AppMotion.PageMillis),
        label = "CompactNavigationVisibility",
    )
    SubcomposeLayout(modifier.fillMaxSize()) { constraints ->
        val navigationPlaceable = subcompose(CompactSlot.Navigation) {
            Box(Modifier.then(if (showNavigation) Modifier else Modifier.semantics { hideFromAccessibility() })) {
                navigation()
            }
        }.single().measure(constraints.copy(minHeight = 0))
        val contentPlaceable = subcompose(CompactSlot.Content) {
            content(navigationPlaceable.height.toDp())
        }.single().measure(Constraints.fixed(constraints.maxWidth, constraints.maxHeight))
        layout(constraints.maxWidth, constraints.maxHeight) {
            contentPlaceable.placeRelative(0, 0)
            if (visibility > 0f) {
                navigationPlaceable.placeRelativeWithLayer(
                    x = 0,
                    y = compactBarTop(constraints.maxHeight, navigationPlaceable.height, visibility),
                ) { alpha = visibility }
            }
        }
    }
}

internal fun compactBarTop(containerHeight: Int, barHeight: Int, visibility: Float): Int =
    containerHeight - (barHeight * visibility.coerceIn(0f, 1f)).roundToInt()
