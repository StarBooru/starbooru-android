package eu.filipnone.starbooru.ui.frame

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import eu.filipnone.starbooru.data.Hand

/** Material 3's window width classes, by the window and never the device. */
enum class WidthClass {
    /** Under 600 dp: a phone held upright, half a split screen. The bar at the bottom. */
    Compact,

    /** 600 to 839 dp: a small tablet, an open foldable, a phone on its side. A rail. */
    Medium,

    /** 840 dp and up: the details beside the picture, lists beside their page. */
    Expanded
    ;

    val hasRail: Boolean get() = this != Compact

    companion object {
        fun of(widthDp: Float): WidthClass =
            when {
                widthDp < 600f -> Compact
                widthDp < 840f -> Medium
                else -> Expanded
            }
    }
}

/** Where the screens are drawn within the frame, and what's around them. */
data class FrameInfo(
    val width: WidthClass = WidthClass.Compact,
    val hand: Hand = Hand.Either,
    /** The space the floating bars and the system's bars take from each side. */
    val padding: PaddingValues = PaddingValues(0.dp),
    /** A short window, like a phone on its side: the rail shows icons only. */
    val short: Boolean = false
) {
    /** The rail, the viewer's panel and its zoom tools go on the right. */
    val rightHanded: Boolean get() = hand == Hand.Right
}

val LocalFrame = compositionLocalOf { FrameInfo() }

/** Whether the frame's bars show; the viewer's fullscreen hides them. */
val LocalBars = staticCompositionLocalOf<MutableState<Boolean>> { mutableStateOf(true) }
