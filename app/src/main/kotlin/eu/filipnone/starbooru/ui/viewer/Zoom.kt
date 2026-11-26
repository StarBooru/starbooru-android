package eu.filipnone.starbooru.ui.viewer

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign

/**
 * The viewer's zoom: the picture's [scale] and its [offset] from the stage's
 * centre, in pixels. Ported from the web's `logic/zoom.rs`; nothing a finger
 * or the wheel sends can make it NaN or push the picture off its place.
 */
@Immutable
data class Zoom(val scale: Float = MIN_SCALE, val offset: Offset = Offset.Zero) {
    val isZoomed: Boolean get() = scale > MIN_SCALE + 0.001f

    /** The scale as a whole percentage, 100 at fit. */
    val percent: Int get() = Math.round(scale * 100f)

    /**
     * Scales by [factor], keeping the point [at] (from the stage's centre,
     * in pixels) where it is. [bounds] is the picture's size at fit.
     */
    fun zoomAt(
        factor: Float,
        at: Offset,
        bounds: Size,
    ): Zoom {
        if (!factor.isFinite() || factor <= 0f || !at.x.isFinite() || !at.y.isFinite()) return this
        val next = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        val k = next / scale
        return Zoom(next, Offset(at.x - (at.x - offset.x) * k, at.y - (at.y - offset.y) * k)).clamped(bounds)
    }

    /** A double tap: back to fit when zoomed in, else in to [TAP_SCALE] on [at]. */
    fun toggleAt(
        at: Offset,
        bounds: Size,
    ): Zoom = if (isZoomed) Zoom() else zoomAt(TAP_SCALE / scale, at, bounds)

    /** Moves the picture by [delta] pixels, as far as its own place allows. */
    fun pan(
        delta: Offset,
        bounds: Size,
    ): Zoom {
        if (!delta.x.isFinite() || !delta.y.isFinite()) return this
        return copy(offset = offset + delta).clamped(bounds)
    }

    /** Keeps the picture over its own place: the offset is at most half of what the zoom added to its size. */
    private fun clamped(bounds: Size): Zoom {
        if (!isZoomed) return Zoom()
        val over = scale - MIN_SCALE
        val mx = atLeastZero(bounds.width) * over / 2f
        val my = atLeastZero(bounds.height) * over / 2f
        return copy(offset = Offset(offset.x.coerceIn(-mx, mx), offset.y.coerceIn(-my, my)))
    }

    companion object {
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 8f

        /** The scale a double tap zooms to. */
        const val TAP_SCALE = 2.5f

        /** One step of the zoom buttons and keys. */
        const val STEP = 1.5f
    }
}

/** A size that is NaN or negative counts as none. */
private fun atLeastZero(x: Float): Float = if (x > 0f) x else 0f

/** One notch of a mouse wheel zooms by this much. */
private const val WHEEL_NOTCH = 1.1

/** A pixel delta this big is a whole notch, whatever its size. */
private const val NOTCH_MIN_PIXELS = 40f

/** Smaller pixel deltas (touchpads, smooth wheels) add up to a notch at this. */
private const val PIXELS_PER_NOTCH = 100f

/** Firefox's lines: 3 to a notch. */
private const val LINES_PER_NOTCH = 3f

/** A wheel event's vertical travel, positive downwards, in its own unit. */
sealed interface WheelDelta {
    val y: Float

    /** Compose's `PointerInputChange.scrollDelta.y` on Android: wheel notches, fractions from smooth wheels. */
    data class Notches(override val y: Float) : WheelDelta

    data class Pixels(override val y: Float) : WheelDelta

    data class Lines(override val y: Float) : WheelDelta

    data class Pages(override val y: Float) : WheelDelta
}

/**
 * The zoom factor of one wheel event. An event counts as one notch at most,
 * so a big delta can't make the zoom jump; rolling down zooms out.
 */
fun wheelFactor(delta: WheelDelta): Float {
    val y = delta.y
    val notches =
        when (delta) {
            is WheelDelta.Pixels -> if (abs(y) >= NOTCH_MIN_PIXELS) y.sign else y / PIXELS_PER_NOTCH
            is WheelDelta.Lines -> (y / LINES_PER_NOTCH).coerceIn(-1f, 1f)
            is WheelDelta.Pages, is WheelDelta.Notches -> y.coerceIn(-1f, 1f)
        }
    return WHEEL_NOTCH.pow(-notches.toDouble()).toFloat()
}
