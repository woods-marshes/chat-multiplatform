package com.github.woodsmarshes.chat.app.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ContentTransform
import com.github.woodsmarshes.chat.feature.article.navigation.ArticleListNavKey
import com.github.woodsmarshes.chat.feature.chat.navigation.ChatNavKey
import com.github.woodsmarshes.chat.feature.contacts.navigation.ContactsNavKey
import com.github.woodsmarshes.chat.feature.conversations.navigation.ConversationsNavKey
import com.github.woodsmarshes.chat.feature.conversations.navigation.GroupInfoNavKey
import com.github.woodsmarshes.chat.feature.profile.navigation.ProfileNavKey
import com.github.woodsmarshes.chat.feature.settings.navigation.SettingsDetailNavKey
import com.github.woodsmarshes.chat.feature.settings.navigation.SettingsNavKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Transition routing rules: the incoming screen of a pop must not fade in
 * (Nav3 renders the popped screen on top, so a fade blends the two — the
 * "chat bleeding through the list" artefact), and tab switches must not get
 * hierarchy motion.
 */
class AppTransitionsTest {

    // Combined EnterTransition prints its slots as "Fade - <state>, Slide -
    // <state>". A slot set on its own prints the full name ("Fade - Fade(...)"),
    // while a combined transition prints the set slot's content and "null" for
    // the other. Slide presence is unambiguous either way; fade presence is
    // detected as "not None and no slide" only where no other effect is set.
    private fun hasFade(transform: ContentTransform) =
        transform.targetContentEnter.toString().contains("Fade - Fade") ||
            (!transform.targetContentEnter.toString().contains("Slide - Slide") &&
                transform.targetContentEnter != EnterTransition.None)

    private fun hasSlide(transform: ContentTransform) =
        transform.targetContentEnter.toString().contains("Slide - Slide")

    private fun isNone(transform: ContentTransform) =
        transform.targetContentEnter == EnterTransition.None

    @Test
    fun topLevelSwitchIsPureFade() {
        val push = AppTransitions.transitionSpec(ConversationsNavKey, ContactsNavKey)
        val pop = AppTransitions.popTransitionSpec(ContactsNavKey, ConversationsNavKey)
        assertTrue(hasFade(push), "tab switch push fades in")
        assertFalse(hasSlide(push), "tab switch push has no hierarchy slide")
        assertTrue(hasFade(pop), "tab switch pop fades in (no detail screen involved)")
        assertFalse(hasSlide(pop), "tab switch pop has no hierarchy slide")
    }

    @Test
    fun popFromChatIncomingIsOpaqueWithParallax() {
        val transform = AppTransitions.popTransitionSpec(ChatNavKey("c1", false), ConversationsNavKey)
        assertFalse(hasFade(transform), "pop from chat: incoming list must not fade")
        assertTrue(hasSlide(transform), "pop from chat: incoming list parallaxes")
    }

    @Test
    fun popFromGroupInfoIncomingIsOpaque() {
        val transform = AppTransitions.popTransitionSpec(GroupInfoNavKey("g1"), ConversationsNavKey)
        assertFalse(hasFade(transform), "pop from group info: incoming must not fade")
    }

    @Test
    fun popFromProfileIncomingIsOpaque() {
        val transform = AppTransitions.popTransitionSpec(ProfileNavKey("u1"), ContactsNavKey)
        assertFalse(hasFade(transform), "pop from profile: incoming must not fade")
    }

    @Test
    fun popFromSettingsDetailIncomingIsOpaque() {
        val transform = AppTransitions.popTransitionSpec(
            SettingsDetailNavKey(com.github.woodsmarshes.chat.feature.settings.model.SettingsCategory.PROFILE),
            SettingsNavKey,
        )
        assertFalse(hasFade(transform), "pop from settings detail: incoming must not fade")
    }

    @Test
    fun pushToChatSlidesInWithFade() {
        val transform = AppTransitions.transitionSpec(ConversationsNavKey, ChatNavKey("c1", false))
        assertTrue(hasSlide(transform), "push to chat slides in")
        assertTrue(hasFade(transform), "push to chat fades in")
    }

    @Test
    fun predictivePopMatchesPopRouting() {
        val pop = AppTransitions.popTransitionSpec(ChatNavKey("c1", false), ConversationsNavKey)
        val predictive = AppTransitions.predictivePopTransitionSpec(ChatNavKey("c1", false), ConversationsNavKey)
        assertTrue(hasSlide(pop), "pop from chat parallaxes")
        assertTrue(hasSlide(predictive), "predictive pop from chat parallaxes too")
        assertFalse(hasFade(predictive), "predictive pop incoming stays opaque")
        val pushEnter = AppTransitions.transitionSpec(ConversationsNavKey, ChatNavKey("c1", false)).targetContentEnter
        assertNotEquals(pushEnter.toString(), pop.targetContentEnter.toString(), "push and pop enter differ")
    }

    @Test
    fun articleTabIsTopLevel() {
        val push = AppTransitions.transitionSpec(ArticleListNavKey, ContactsNavKey)
        assertFalse(hasSlide(push), "article->contacts is a tab switch: no slide")
        assertTrue(hasFade(push), "article->contacts fades")
    }
}
