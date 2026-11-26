package eu.filipnone.starbooru.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** After the web's tests for `logic/spots.rs`. */
class SpotsTest {
    private fun spot(key: String) = Spot(key, post = 7, count = 60)

    @Test
    fun theLatestThirtyStayBesidesTheSaved() {
        val spots = (0 until 40).map { spot("tag$it") } + spot("old saved")
        val kept = Spots.keep(spots, saved = setOf("old saved"))
        assertEquals(Spots.MAX + 1, kept.size)
        assertTrue(kept.any { it.key == "old saved" })
        assertEquals("tag0", kept.first().key)
    }

    @Test
    fun aSearchStaysOnceAsItCameLast() {
        val kept = Spots.keep(listOf(spot("a").copy(count = 120), spot("b"), spot("a")), emptySet())
        assertEquals(listOf("a", "b"), kept.map { it.key })
        assertEquals(120, kept.first().count)
    }
}
