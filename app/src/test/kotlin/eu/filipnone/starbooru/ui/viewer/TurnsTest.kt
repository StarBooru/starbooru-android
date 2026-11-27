package eu.filipnone.starbooru.ui.viewer

import eu.filipnone.starbooru.core.Neighbors
import eu.filipnone.starbooru.post
import eu.filipnone.starbooru.ui.gestures.Turn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The web's `logic/viewer.rs` queue tests, and how a turn hands the slide on. */
class TurnsTest {
    @Test
    fun swipesWaitInLineAndTheOtherWayCancelsOneOut() {
        assertEquals(1, queued(0, Side.Next))
        assertEquals(3, queued(2, Side.Next))
        assertEquals(-1, queued(0, Side.Prev))
        assertEquals(1, queued(2, Side.Prev))
        // A burst doesn't leave it walking on for long.
        assertEquals(MOST_QUEUED, queued(MOST_QUEUED, Side.Next))
        assertEquals(-MOST_QUEUED, queued(-MOST_QUEUED, Side.Prev))
    }

    @Test
    fun aTurnGoesToItsSide() {
        assertEquals(Side.Next, Turn.Next.side)
        assertEquals(Side.Prev, Turn.Previous.side)
    }

    @Test
    fun theEndIsOnlyWhereTheServerSaysSo() {
        // Nothing known yet: a swipe may still go.
        assertFalse(ViewerState(5).endAt(Side.Next))
        assertFalse(ViewerState(5).endAt(Side.Prev))

        val last = ViewerState(5, post(5), prev = post(4), neighbors = Neighbors(prev = 4, next = null))
        assertTrue(last.endAt(Side.Next))
        assertFalse(last.endAt(Side.Prev))

        // Known but its post not here yet: not the end.
        val coming = ViewerState(5, post(5), neighbors = Neighbors(prev = 4, next = 6))
        assertFalse(coming.endAt(Side.Next))

        // The post just left stands in before the server answers.
        val left = ViewerState(6, post(6), prev = post(5))
        assertFalse(left.endAt(Side.Prev))
        assertNull(left.neighbors)
    }

    @Test
    fun theNewPostStaysWhereTheFingerLeftIt() {
        val span = 1_000f
        // Swiped 300 px left: the next picture stood at 700, and stays there.
        assertEquals(700f, slideAfter(-300f, Turn.Next, span), 0f)
        assertEquals(-700f, slideAfter(300f, Turn.Previous, span), 0f)
        // Pressed with no swipe: it comes in from the whole way out.
        assertEquals(1_000f, slideAfter(0f, Turn.Next, span), 0f)
        // Turned the other way midway: it goes on from there.
        assertEquals(-600f, slideAfter(400f, Turn.Previous, span), 0f)
        // Turned again before the last one came in: no further than one span out.
        assertEquals(1_000f, slideAfter(400f, Turn.Next, span), 0f)
        assertEquals(-1_000f, slideAfter(-900f, Turn.Previous, span), 0f)
    }
}
