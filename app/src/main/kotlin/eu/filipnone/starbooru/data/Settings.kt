package eu.filipnone.starbooru.data

import eu.filipnone.starbooru.core.GalleryView
import eu.filipnone.starbooru.ui.gestures.SwipeAction
import eu.filipnone.starbooru.ui.theme.Seed

enum class ThemeMode { System, Light, Dark }

/** Which side the rail, the viewer's panel and its zoom tools sit on. */
enum class Hand { Either, Left, Right }

/** The app's own settings, kept on the device. */
data class Settings(
    val theme: ThemeMode = ThemeMode.System,
    val oled: Boolean = false,
    /** The wallpaper's colours on Android 12 and later; [seed] otherwise. */
    val wallpaperColours: Boolean = true,
    val seed: Seed = Seed(),
    val hand: Hand = Hand.Either,
    val galleryView: GalleryView = GalleryView.STAGGERED_PORTRAIT,
    /** A column count picked by the user; `null` follows the width. */
    val columns: Int? = null,
    /** Tiles without gaps or rounded corners. */
    val flush: Boolean = false,
    /** How much of the disk pictures may take, in megabytes; 0 keeps none. */
    val cacheMb: Int = DEFAULT_CACHE_MB,
    /** What swiping a tile sideways does. Off until chosen, as on the web: a sideways swipe is easy to make by mistake. */
    val swipeLeft: SwipeAction = SwipeAction.Off,
    val swipeRight: SwipeAction = SwipeAction.Off,
) {
    /** Whether the rail and the viewer's panel sit on the right. */
    val rightHanded: Boolean get() = hand == Hand.Right

    companion object {
        const val DEFAULT_CACHE_MB = 512

        /** The limits Settings offers, as the reference did. */
        val CACHE_CHOICES_MB = listOf(0, 128, 256, 512, 1024, 2048, 4096)
    }
}
