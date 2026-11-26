package eu.filipnone.starbooru.ui.viewer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Test

/** The web's `logic/zoom.rs` tests. */
class ZoomTest {
    private val bounds = Size(800f, 600f)

    private fun assertAt(
        x: Float,
        y: Float,
        zoom: Zoom
    ) {
        assertEquals("x of $zoom", x, zoom.offset.x, 1e-4f)
        assertEquals("y of $zoom", y, zoom.offset.y, 1e-4f)
    }

    @Test
    fun zoomsAroundTheFinger() {
        val z = Zoom().zoomAt(2f, Offset(100f, 50f), bounds)
        assertEquals(2f, z.scale, 0f)
        // The point under the finger stays put: at = t + (at - t0) * k.
        assertAt(-100f, -50f, z)
        assertAt(0f, 0f, Zoom().zoomAt(2f, Offset.Zero, bounds))
    }

    @Test
    fun clampsScaleAndOffset() {
        assertEquals(Zoom.MAX_SCALE, Zoom().zoomAt(100f, Offset.Zero, bounds).scale, 0f)
        assertEquals(Zoom(), Zoom().zoomAt(0.1f, Offset(10f, 10f), bounds))
        val z = Zoom().zoomAt(2f, Offset.Zero, bounds).pan(Offset(10_000f, -10_000f), bounds)
        assertAt(400f, -300f, z)
        // Not zoomed: panning does nothing.
        assertEquals(Zoom(), Zoom().pan(Offset(50f, 50f), bounds))
    }

    @Test
    fun toggles() {
        val z = Zoom().toggleAt(Offset.Zero, bounds)
        assertEquals(Zoom.TAP_SCALE, z.scale, 0f)
        assertEquals(Zoom(), z.toggleAt(Offset(5f, 5f), bounds))
    }

    @Test
    fun ignoresHostileNumbers() {
        val z = Zoom().zoomAt(2f, Offset.Zero, bounds)
        assertEquals(z, z.zoomAt(Float.NaN, Offset.Zero, bounds))
        assertEquals(z, z.zoomAt(-1f, Offset.Zero, bounds))
        assertEquals(z, z.zoomAt(2f, Offset(Float.POSITIVE_INFINITY, 0f), bounds))
        assertEquals(z, z.pan(Offset(Float.NaN, 1f), bounds))
        assertAt(0f, 0f, z.pan(Offset(5f, 5f), Size(Float.NaN, Float.NaN)))
        assertAt(0f, 0f, z.pan(Offset(5f, 5f), Size(-10f, -10f)))
        assertEquals(200, z.percent)
        assertEquals(100, Zoom().percent)
    }

    private val notch = (1.0 / 1.1).toFloat()

    @Test
    fun aWheelNotchIsTheSameEverywhere() {
        // One notch, in the units and sizes the wheels send.
        for (delta in listOf(
            WheelDelta.Notches(1f),
            WheelDelta.Pixels(120f),
            WheelDelta.Pixels(100f),
            WheelDelta.Pixels(150f),
            WheelDelta.Pixels(80f),
            WheelDelta.Lines(3f),
            WheelDelta.Lines(6f),
            WheelDelta.Pages(1f)
        )) {
            assertEquals("$delta", notch, wheelFactor(delta), 1e-6f)
        }
        for (delta in listOf(WheelDelta.Notches(-1f), WheelDelta.Pixels(-120f), WheelDelta.Pixels(-80f), WheelDelta.Lines(-6f))) {
            assertEquals("$delta", 1.1f, wheelFactor(delta), 1e-6f)
        }
    }

    @Test
    fun smallWheelDeltasAddUpToANotch() {
        // A touchpad or smooth wheel: ten events of 10 px make one notch.
        assertEquals(notch, wheelFactor(WheelDelta.Pixels(10f)).pow(10), 1e-5f)
        assertEquals(notch, wheelFactor(WheelDelta.Lines(0.75f)).pow(4), 1e-5f)
        assertEquals(notch, wheelFactor(WheelDelta.Notches(0.25f)).pow(4), 1e-5f)
    }

    @Test
    fun aWheelEventNeverJumps() {
        assertEquals(1.1f, wheelFactor(WheelDelta.Pixels(-10_000f)), 1e-6f)
        assertEquals(1.1f, wheelFactor(WheelDelta.Lines(-500f)), 1e-6f)
        assertEquals(1.1f, wheelFactor(WheelDelta.Pages(-9f)), 1e-6f)
        assertEquals(1.1f, wheelFactor(WheelDelta.Notches(-4f)), 1e-6f)
    }

    @Test
    fun aWheelEventWithoutVerticalTravelDoesNothing() {
        assertEquals(1f, wheelFactor(WheelDelta.Pixels(0f)), 0f)
        assertEquals(1f, wheelFactor(WheelDelta.Lines(0f)), 0f)
        assertEquals(1f, wheelFactor(WheelDelta.Notches(0f)), 0f)
    }

    @Test
    fun hostileWheelDeltasLeaveTheZoomAlone() {
        val z = Zoom().zoomAt(2f, Offset.Zero, bounds)
        for (delta in listOf(
            WheelDelta.Pixels(Float.NaN),
            WheelDelta.Lines(Float.NaN),
            WheelDelta.Pages(Float.NaN),
            WheelDelta.Notches(Float.NaN)
        )) {
            assertEquals("$delta", z, z.zoomAt(wheelFactor(delta), Offset.Zero, bounds))
        }
        assertEquals(notch, wheelFactor(WheelDelta.Pixels(Float.POSITIVE_INFINITY)), 1e-6f)
    }
}
