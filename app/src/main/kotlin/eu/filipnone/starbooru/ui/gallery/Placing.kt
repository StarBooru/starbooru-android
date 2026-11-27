package eu.filipnone.starbooru.ui.gallery

import eu.filipnone.starbooru.core.TileRect

/**
 * Which tiles make an entrance, as on the web (`pages/gallery.rs`, `settled`
 * and `in_sight`): only those that come with a newly loaded page and are in
 * sight as it lands, and only once. A tile scrolled back into view, or there
 * when the gallery comes back from the viewer, is simply there.
 */
object Entrance {
    /**
     * Whether the tile at [index], from [top] to [bottom] down the grid,
     * makes an entrance as it first shows: it came after the [settled]
     * posts that were there before the last page, and it meets the screen,
     * [from] to [to]. All in dp.
     */
    fun plays(
        index: Int,
        settled: Int,
        top: Double,
        bottom: Double,
        from: Double,
        to: Double,
    ): Boolean = index >= settled && top < to && bottom > from
}

/** The post at the top of the gallery, under the bar, to come back to after a resize. */
object Anchor {
    /** How far, in dp, a tile put back at the top can sit under the line, from rounding to pixels. */
    private const val SLACK = 1.0

    /**
     * The tile at the screen's top [line], in dp down the grid: the
     * [current] one while its tile still crosses the line, so a page put
     * back around it keeps it, otherwise the first that reaches below the
     * line. -1 while the page is at its top, and where the line is past
     * every tile.
     */
    fun at(
        tiles: List<TileRect>,
        line: Double,
        current: Int,
    ): Int {
        if (!(line > 0.0)) return -1
        val kept = tiles.getOrNull(current)
        if (kept != null && kept.y - SLACK <= line && kept.y + kept.height > line) return current
        return tiles.indexOfFirst { it.y + it.height > line }
    }
}
