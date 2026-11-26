package eu.filipnone.starbooru.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.savedstate.compose.serialization.serializers.MutableStateSerializer

/**
 * One back stack per navigation item, so each keeps its place while another
 * is open. The gallery's is the way out of the app: Back at the root of any
 * other item goes to it. All of it survives a rotation and process death.
 */
class NavigationState(tab: MutableState<Tab>, val stacks: Map<Tab, NavBackStack<NavKey>>) {
    var tab: Tab by tab

    val stacksInUse: List<Tab>
        get() = if (tab == Tab.Gallery) listOf(Tab.Gallery) else listOf(Tab.Gallery, tab)

    val current: NavKey?
        get() = stacks[tab]?.lastOrNull()

    /** Opens [tab]; pressing it again while open goes back to its root. */
    fun select(tab: Tab) {
        if (this.tab == tab) {
            stacks[tab]?.let { stack -> while (stack.size > 1) stack.removeAt(stack.lastIndex) }
        } else {
            this.tab = tab
        }
    }

    fun open(route: Route) {
        val tab = Tab.entries.firstOrNull { it.root == route }
        if (tab != null) {
            select(tab)
            return
        }
        val stack = stacks[this.tab] ?: return
        if (stack.lastOrNull() != route) stack.add(route)
    }

    /** Replaces the page on top, as a new search does to the gallery's. */
    fun replace(route: Route) {
        val stack = stacks[tab] ?: return
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
        stack.add(route)
    }

    /** False when there's nowhere to go back to and the app should close. */
    fun back(): Boolean {
        val stack = stacks[tab] ?: return false
        return when {
            stack.size > 1 -> {
                stack.removeAt(stack.lastIndex)
                true
            }

            tab != Tab.Gallery -> {
                tab = Tab.Gallery
                true
            }

            else -> false
        }
    }
}

@Composable
fun rememberNavigationState(): NavigationState {
    val tab =
        rememberSerializable(serializer = MutableStateSerializer(Tab.serializer())) {
            mutableStateOf(Tab.Gallery)
        }
    val stacks = Tab.entries.associateWith { rememberNavBackStack(it.root) }
    return remember { NavigationState(tab, stacks) }
}

/** The entries of the stacks in use, each with its own saved state and ViewModels. */
@Composable
fun NavigationState.entries(provider: (NavKey) -> NavEntry<NavKey>): List<NavEntry<NavKey>> {
    val decorated =
        stacks.mapValues { (_, stack) ->
            rememberDecoratedNavEntries(
                backStack = stack,
                entryDecorators =
                    listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                entryProvider = provider,
            )
        }
    return stacksInUse.flatMap { decorated[it].orEmpty() }
}
