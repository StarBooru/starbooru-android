package eu.filipnone.starbooru.ui.viewer

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether videos play muted. Muting one carries over to the next, and is
 * kept in Settings, as the web keeps `video_muted`.
 */
class Sound(
    muted: Boolean = false,
    /** Keeps a change, for the next time the app opens. */
    private val keep: (Boolean) -> Unit = {},
) {
    /** What the next video starts with. */
    var muted: Boolean = muted
        private set

    /** Mute or unmute pressed in a player. */
    fun press(muted: Boolean) {
        if (muted == this.muted) return
        this.muted = muted
        keep(muted)
    }

    /** What Settings holds, once it's been read. */
    fun heard(muted: Boolean) {
        this.muted = muted
    }
}

/** The app's [Sound], for every player in it. */
val LocalSound = staticCompositionLocalOf { Sound() }
