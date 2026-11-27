package eu.filipnone.starbooru

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartingWindowTest {
    private val phone = Bars(left = 0, top = 63, right = 0, bottom = 63)

    @Test
    fun theWindowWaitsUntilComposeHasTheInsets() {
        // Compose's first frames read no insets yet.
        assertFalse(StartingWindow.ready(phone, null, waited = 0))
        assertFalse(StartingWindow.ready(phone, Bars(0, 0, 0, 0), waited = 150))
        assertTrue(StartingWindow.ready(phone, phone, waited = 160))
    }

    @Test
    fun theWindowWaitsForItsOwnInsets() {
        assertFalse(StartingWindow.ready(null, Bars(0, 0, 0, 0), waited = 10))
    }

    @Test
    fun aWindowWithoutBarsDrawsAtOnce() {
        val none = Bars(0, 0, 0, 0)
        assertTrue(StartingWindow.ready(none, none, waited = 0))
    }

    @Test
    fun theWaitHasAnEnd() {
        assertTrue(StartingWindow.ready(phone, Bars(0, 0, 0, 0), waited = StartingWindow.MOST_MS))
        assertTrue(StartingWindow.ready(null, null, waited = 5_000))
    }
}
