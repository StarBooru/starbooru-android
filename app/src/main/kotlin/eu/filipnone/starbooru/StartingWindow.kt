package eu.filipnone.starbooru

import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** The room the system bars and the cutout take on each side, in px. */
data class Bars(val left: Int, val top: Int, val right: Int, val bottom: Int)

/**
 * Holds the starting window until Compose lays the app out with the
 * window's insets. They reach Compose a frame or more after its first one;
 * without the hold, the bars draw under the status bar until then and jump
 * into place.
 */
class StartingWindow {
    /** The insets Compose has, once it has composed with any. */
    private var seen: Bars? = null

    /** Keeps [view]'s window from drawing until it's [ready]. */
    fun hold(view: View) {
        val since = SystemClock.uptimeMillis()
        view.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    val window =
                        ViewCompat.getRootWindowInsets(view)?.getInsets(
                            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
                        )
                    val bars = window?.let { Bars(it.left, it.top, it.right, it.bottom) }
                    val go = ready(bars, seen, SystemClock.uptimeMillis() - since)
                    if (go) view.viewTreeObserver.removeOnPreDrawListener(this)
                    return go
                }
            },
        )
    }

    /** Tells the hold what Compose has; somewhere in the app's content. */
    @Composable
    fun Watch() {
        val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
        val density = LocalDensity.current
        val dir = LocalLayoutDirection.current
        val now = Bars(insets.getLeft(density, dir), insets.getTop(density), insets.getRight(density, dir), insets.getBottom(density))
        SideEffect { seen = now }
    }

    companion object {
        /** The longest the window waits, whatever happens, in ms. */
        const val MOST_MS = 1_000L

        /**
         * Whether the first frame may draw: once Compose has the [window]'s
         * insets as [compose], which a window without bars has from the
         * start, or after [MOST_MS] [waited].
         */
        fun ready(
            window: Bars?,
            compose: Bars?,
            waited: Long,
        ): Boolean = waited >= MOST_MS || (window != null && window == compose)
    }
}
