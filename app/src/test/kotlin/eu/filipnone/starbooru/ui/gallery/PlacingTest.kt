package eu.filipnone.starbooru.ui.gallery

import eu.filipnone.starbooru.core.TileRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The web's entrance rule (`settled`, `in_sight`) and the anchor kept across layouts. */
class PlacingTest {
    // ---------- entrance ----------

    @Test
    fun aTileOfANewPageInSightMakesAnEntrance() {
        // 60 posts were there; post 61's tile is on the screen, 0 to 800 dp.
        assertTrue(Entrance.plays(index = 60, settled = 60, top = 700.0, bottom = 830.0, from = 0.0, to = 800.0))
        // The first page, on an empty gallery.
        assertTrue(Entrance.plays(index = 0, settled = 0, top = 0.0, bottom = 130.0, from = 0.0, to = 800.0))
    }

    @Test
    fun aTileThatWasThereBeforeShowsAtOnce() {
        // Scrolled back into view, or there when coming back from the viewer.
        assertFalse(Entrance.plays(index = 10, settled = 60, top = 700.0, bottom = 830.0, from = 0.0, to = 800.0))
        assertFalse(Entrance.plays(index = 59, settled = 60, top = 0.0, bottom = 130.0, from = 0.0, to = 800.0))
    }

    @Test
    fun aNewTileOutOfSightShowsAtOnce() {
        // Below the screen, above it, and only touching its edges.
        assertFalse(Entrance.plays(index = 70, settled = 60, top = 900.0, bottom = 1030.0, from = 0.0, to = 800.0))
        assertFalse(Entrance.plays(index = 70, settled = 60, top = -300.0, bottom = -170.0, from = 0.0, to = 800.0))
        assertFalse(Entrance.plays(index = 70, settled = 60, top = 800.0, bottom = 930.0, from = 0.0, to = 800.0))
        assertFalse(Entrance.plays(index = 70, settled = 60, top = -130.0, bottom = 0.0, from = 0.0, to = 800.0))
        // Half in sight is in sight.
        assertTrue(Entrance.plays(index = 70, settled = 60, top = -60.0, bottom = 70.0, from = 0.0, to = 800.0))
    }

    // ---------- anchor ----------

    /** Three columns of 100 dp tiles, 8 dp apart, [count] of them. */
    private fun grid(
        count: Int,
        columns: Int = 3,
        size: Double = 100.0,
        gap: Double = 8.0,
    ) = (0 until count).map { i ->
        TileRect(x = (i % columns) * (size + gap), y = (i / columns) * (size + gap), width = size, height = size)
    }

    @Test
    fun atTheTopThereIsNoAnchor() {
        assertEquals(-1, Anchor.at(grid(30), 0.0, current = 5))
        assertEquals(-1, Anchor.at(grid(30), -40.0, current = -1))
        assertEquals(-1, Anchor.at(grid(30), Double.NaN, current = -1))
    }

    @Test
    fun theAnchorIsTheFirstTileReachingBelowTheLine() {
        // Row 3 starts at 324 dp; a line in it, or in the gap above it.
        assertEquals(9, Anchor.at(grid(30), 350.0, current = -1))
        assertEquals(9, Anchor.at(grid(30), 320.0, current = -1))
        // Past every tile.
        assertEquals(-1, Anchor.at(grid(30), 5000.0, current = -1))
    }

    @Test
    fun theAnchorStaysWhileItsTileCrossesTheLine() {
        // Post 30 (index 29) was the anchor in another layout, and was put
        // back at the top of this one: it stays, not the first of its row.
        val tiles = grid(60, columns = 5)
        val top = tiles[29].y
        assertEquals(25, Anchor.at(tiles, top, current = -1))
        assertEquals(29, Anchor.at(tiles, top, current = 29))
        // Rounded to a pixel above the tile.
        assertEquals(29, Anchor.at(tiles, top - 0.4, current = 29))
        // Scrolled on past it: the next row's first.
        assertEquals(30, Anchor.at(tiles, tiles[29].y + 104.0, current = 29))
    }

    @Test
    fun aTurnAndBackKeepsTheSamePost() {
        // Portrait, three columns: post 30 at the top.
        val portrait = grid(60, columns = 3)
        val landscape = grid(60, columns = 5)
        var anchor = 29
        repeat(4) {
            anchor = Anchor.at(landscape, landscape[anchor].y, anchor)
            anchor = Anchor.at(portrait, portrait[anchor].y, anchor)
        }
        assertEquals(29, anchor)
    }

    @Test
    fun flushTilesKeepTheirAnchorToo() {
        // No gap: the row above ends exactly where the anchor's starts.
        val tiles = grid(30, gap = 0.0)
        assertEquals(12, Anchor.at(tiles, tiles[12].y - 0.4, current = 12))
        assertEquals(12, Anchor.at(tiles, tiles[12].y, current = -1))
    }
}
