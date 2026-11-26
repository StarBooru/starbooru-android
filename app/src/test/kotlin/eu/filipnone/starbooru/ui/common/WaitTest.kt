package eu.filipnone.starbooru.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

/** After the web's `a_guest_who_asks_too_fast_hears_to_slow_down_and_when`. */
class WaitTest {
    @Test
    fun aGuestHearsSecondsUnderAMinute() {
        assertEquals(Wait.Seconds(12), Wait.of(12, guest = true))
        assertEquals(Wait.Seconds(1), Wait.of(0, guest = true))
        assertEquals(Wait.Minutes(2), Wait.of(90, guest = true))
    }

    @Test
    fun anAccountHearsWholeMinutes() {
        assertEquals(Wait.Minutes(1), Wait.of(12, guest = false))
        assertEquals(Wait.Minutes(2), Wait.of(61, guest = false))
        assertEquals(Wait.Unknown, Wait.of(null, guest = false))
    }
}
