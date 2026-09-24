package com.github.woodsmarshes.chat.core.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals

@Serializable
private data class DetailKey(val id: Int) : NavKey

private data object OtherDetailKey : NavKey

private data object TabKey : NavKey

private data object OtherTabKey : NavKey

class NavigatorTest {

    private fun navigator(startKey: NavKey = TabKey): Navigator {
        val topLevelKeys = setOf(startKey, OtherTabKey)
        val state = NavigationState(
            startKey = startKey,
            topLevelStack = NavBackStack(startKey),
            subStacks = topLevelKeys.associateWith { key -> NavBackStack(key) },
        )
        return Navigator(state)
    }

    @Test
    fun sameKindDetailReplacesTopInsteadOfStacking() {
        val navigator = navigator()
        navigator.navigate(DetailKey(1))
        navigator.navigate(DetailKey(2))

        val stack = navigator.state.currentSubStack
        assertEquals(2, stack.size, "list base + one in-place detail")
        assertEquals(DetailKey(2), stack.last())
    }

    @Test
    fun backFromReplacedDetailReturnsToListInOneStep() {
        val navigator = navigator()
        navigator.navigate(DetailKey(1))
        navigator.navigate(DetailKey(2))
        navigator.goBack()

        assertEquals(listOf<NavKey>(TabKey), navigator.state.currentSubStack.toList())
    }

    @Test
    fun differentKindDetailsStillStack() {
        val navigator = navigator()
        navigator.navigate(DetailKey(1))
        navigator.navigate(OtherDetailKey)

        assertEquals(
            listOf<NavKey>(TabKey, DetailKey(1), OtherDetailKey),
            navigator.state.currentSubStack.toList(),
        )
    }

    @Test
    fun navigatingSameKeyDoesNotDuplicate() {
        val navigator = navigator()
        navigator.navigate(DetailKey(1))
        navigator.navigate(DetailKey(1))

        assertEquals(listOf<NavKey>(TabKey, DetailKey(1)), navigator.state.currentSubStack.toList())
    }

    @Test
    fun topLevelSwitchPreservesPerTabSubStack() {
        val navigator = navigator()
        navigator.navigate(DetailKey(1))
        navigator.navigate(OtherTabKey)
        navigator.navigate(TabKey)

        // Multi-back-stack: each tab keeps its own detail entries. Returning
        // to the start key rewrites the top-level stack to just that key.
        assertEquals(
            listOf<NavKey>(TabKey, DetailKey(1)),
            navigator.state.currentSubStack.toList(),
        )
        assertEquals(listOf<NavKey>(TabKey), navigator.state.topLevelStack.toList())
    }

    @Test
    fun reselectingCurrentTopLevelClearsItsSubStack() {
        val navigator = navigator()
        navigator.navigate(DetailKey(1))
        navigator.navigate(TabKey)

        assertEquals(listOf<NavKey>(TabKey), navigator.state.currentSubStack.toList())
    }

    @Test
    fun goBackAtStartKeyIsSafeNoOp() {
        val navigator = navigator()

        navigator.goBack()
        assertEquals(listOf<NavKey>(TabKey), navigator.state.currentSubStack.toList())
    }
}
