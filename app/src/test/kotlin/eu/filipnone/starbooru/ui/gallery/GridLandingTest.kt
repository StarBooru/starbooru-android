package eu.filipnone.starbooru.ui.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GridLandingTest {
    @Test
    fun aFingerOnAStillPageReachesTheTileUnderIt() {
        assertEquals(48L, GridLanding.tile(moving = false) { 48L })
        assertNull(GridLanding.tile<Long>(moving = false) { null })
    }

    @Test
    fun aFingerOnAMovingPageOnlyStopsIt() {
        // No tap, hold or swipe: the tile under it isn't the one aimed at.
        var asked = false
        val tile =
            GridLanding.tile(moving = true) {
                asked = true
                48L
            }
        assertNull(tile)
        assertEquals(false, asked)
    }
}
