package com.github.woodsmarshes.chat.core.ui.components.motion

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.github.woodsmarshes.chat.core.ui.components.isInListDetailScene

object AppMotion {
    const val FeedbackMillis = 150
    const val PageMillis = 260
    const val SharedMillis = 320

    /**
     * Full-screen spinners only appear if loading lingers longer than this;
     * Room-backed paging resolves within the first frame, so an immediate
     * spinner would flash on every chat open.
     */
    const val LoadingRevealDelayMillis = 400L
}

val LocalAppSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/**
 * Shared-bounds helper keyed by a caller-built namespace key. Disabled when
 * the scope is absent (web/JS) or the composable renders inside a visible
 * multi-pane ListDetailScene — with both panes composed at once, identical
 * keys on both sides would collide instead of animating.
 */
@Composable
fun Modifier.sharedElementBounds(key: String?, element: String): Modifier {
    val scope = LocalAppSharedTransitionScope.current ?: return this
    if (key == null || isInListDetailScene()) return this
    val visibility = LocalNavAnimatedContentScope.current
    return with(scope) {
        sharedBounds(
            sharedContentState = rememberSharedContentState("$key/$element"),
            animatedVisibilityScope = visibility,
            boundsTransform = { _, _ -> tween(AppMotion.SharedMillis) },
            enter = fadeIn(tween(AppMotion.FeedbackMillis)),
            exit = fadeOut(tween(AppMotion.FeedbackMillis)),
        )
    }
}

/** Bounds also support different text styles and composite avatar placeholders. */
@Composable
fun Modifier.sharedConversationBounds(conversationId: String?, element: String): Modifier =
    sharedElementBounds(conversationId?.let { "conversation/$it" }, element)

/** Contact/profile avatar+title flight; pairs ContactItem with ProfileScreen. */
@Composable
fun Modifier.sharedUserBounds(userId: String?, element: String): Modifier =
    sharedElementBounds(userId?.let { "user/$it" }, element)

/** Settings personal card -> PROFILE detail header flight. */
@Composable
fun Modifier.sharedSettingsProfileBounds(element: String): Modifier =
    sharedElementBounds("settings-profile", element)
