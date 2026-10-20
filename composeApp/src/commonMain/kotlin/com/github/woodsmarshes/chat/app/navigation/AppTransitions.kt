package com.github.woodsmarshes.chat.app.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import com.github.woodsmarshes.chat.core.ui.components.motion.AppMotion
import com.github.woodsmarshes.chat.feature.article.navigation.ArticleDetailNavKey
import com.github.woodsmarshes.chat.feature.article.navigation.ArticleListNavKey
import com.github.woodsmarshes.chat.feature.article_editor.navigation.ArticleEditorNavKey
import com.github.woodsmarshes.chat.feature.chat.navigation.ChatNavKey
import com.github.woodsmarshes.chat.feature.contacts.navigation.ContactsNavKey
import com.github.woodsmarshes.chat.feature.conversations.navigation.ChatGroupInfoNavKey
import com.github.woodsmarshes.chat.feature.conversations.navigation.ConversationsNavKey
import com.github.woodsmarshes.chat.feature.conversations.navigation.GroupInfoNavKey
import com.github.woodsmarshes.chat.feature.profile.navigation.ProfileNavKey
import com.github.woodsmarshes.chat.feature.search.navigation.SearchNavKey
import com.github.woodsmarshes.chat.feature.settings.navigation.EditProfileNavKey
import com.github.woodsmarshes.chat.feature.settings.navigation.OpenSourceLicensesNavKey
import com.github.woodsmarshes.chat.feature.settings.navigation.SettingsDetailNavKey
import com.github.woodsmarshes.chat.feature.settings.navigation.SettingsNavKey

/**
 * Directional screen transitions for the single-pane (compact) layout.
 *
 * Nav3 pops render the outgoing scene on top (lower target zIndex). A
 * cross-fade there would blend the two screens — the visible "chat bleeding
 * through the list" artefact — so the incoming screen must stay opaque and
 * the outgoing one slides/fades away instead.
 */
object AppTransitions {
    /** Tab-level destinations switch without hierarchy motion. */
    private fun isTopLevelSwitch(initial: Any?, target: Any?): Boolean {
        val topLevel = setOf(
            ArticleListNavKey::class,
            ConversationsNavKey::class,
            ContactsNavKey::class,
            SettingsNavKey::class,
        )
        return initial != null && target != null &&
            initial::class in topLevel && target::class in topLevel
    }

    private fun isChatKey(key: Any?): Boolean = key is ChatNavKey

    /** Push: incoming detail slides over with a subtle parallax from the right. */
    fun transitionSpec(initial: Any?, target: Any?): ContentTransform = when {
        isTopLevelSwitch(initial, target) ->
            fadeIn(tween(AppMotion.PageMillis)) togetherWith fadeOut(tween(AppMotion.PageMillis))
        else -> (slideInHorizontally(tween(AppMotion.PageMillis)) { it / 4 } +
            fadeIn(tween(AppMotion.PageMillis))) togetherWith
            fadeOut(tween(AppMotion.FeedbackMillis))
    }

    /**
     * Pop: incoming (lower) screen is fully opaque immediately — no fade-in —
     * while the outgoing one fades out on top. The incoming screen only ever
     * slides if the popped screen was a chat (list parallax).
     */
    fun popTransitionSpec(initial: Any?, target: Any?): ContentTransform = when {
        isTopLevelSwitch(initial, target) ->
            fadeIn(tween(AppMotion.PageMillis)) togetherWith fadeOut(tween(AppMotion.PageMillis))
        isChatKey(initial) -> (slideInHorizontally(tween(AppMotion.PageMillis)) { -it / 4 }) togetherWith
            fadeOut(tween(AppMotion.FeedbackMillis))
        else -> EnterTransition.None togetherWith fadeOut(tween(AppMotion.FeedbackMillis))
    }

    /**
     * Predictive back: the gesture already seeks the pop animation; a fast
     * spec would be visible only as a flash on commit. Keep the parallax
     * directions from [popTransitionSpec] but let the commit be near-instant.
     */
    fun predictivePopTransitionSpec(initial: Any?, target: Any?): ContentTransform =
        popTransitionSpec(initial, target)
}
