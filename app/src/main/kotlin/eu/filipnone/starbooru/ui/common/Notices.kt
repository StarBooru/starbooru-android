package eu.filipnone.starbooru.ui.common

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The app's notices, at the foot of the screen above the bar: what an action
 * did, with a way back (Undo) or onward (Open) when there is one.
 */
class Notices(val host: SnackbarHostState, private val scope: CoroutineScope) {
    fun say(text: String) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            host.showSnackbar(text, withDismissAction = true, duration = SnackbarDuration.Short)
        }
    }

    /** A notice with a button: [act] runs when it's pressed before the notice goes. */
    fun offer(
        text: String,
        action: String,
        act: () -> Unit,
    ) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            val result = host.showSnackbar(text, actionLabel = action, withDismissAction = true, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) act()
        }
    }
}

val LocalNotices = staticCompositionLocalOf<Notices?> { null }
