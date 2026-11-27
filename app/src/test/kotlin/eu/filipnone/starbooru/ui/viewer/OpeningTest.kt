package eu.filipnone.starbooru.ui.viewer

import eu.filipnone.starbooru.post
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpeningTest {
    @Test
    fun theViewerTakesThePostItOpens() {
        val opening = Opening()
        opening.put(post(5))
        assertEquals(post(5), opening.take(5))
        // Once.
        assertNull(opening.take(5))
    }

    @Test
    fun aPostLeftForAnotherIsDropped() {
        val opening = Opening()
        opening.put(post(5))
        assertNull(opening.take(6))
        // Not kept for later either: a link or Back opens 5 with nothing.
        assertNull(opening.take(5))
    }

    @Test
    fun theLastPostLeftWins() {
        val opening = Opening()
        opening.put(post(5))
        opening.put(post(7))
        assertEquals(post(7), opening.take(7))
    }

    @Test
    fun nothingLeftGivesNothing() {
        assertNull(Opening().take(1))
    }
}
