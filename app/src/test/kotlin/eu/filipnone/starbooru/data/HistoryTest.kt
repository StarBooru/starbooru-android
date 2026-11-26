package eu.filipnone.starbooru.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** After the web's tests for `logic/history.rs`. */
class HistoryTest {
    @Test
    fun anOpenedPostGoesFirstOnce() {
        assertEquals(listOf(3L, 1L, 2L), History.opened(listOf(1L, 2L, 3L), 3))
        assertEquals(listOf(4L, 1L), History.opened(listOf(1L), 4))
    }

    @Test
    fun theListKeepsNoMoreThanItsCap() {
        val many = (1L..80L).toList()
        assertEquals(History.MAX, History.opened(many, 99).size)
        assertEquals(99L, History.opened(many, 99).first())
    }

    @Test
    fun storedTextReadsBackTidy() {
        assertEquals(listOf(5L, 2L), History.parse("5,2,5,-1,x,0"))
        assertEquals(emptyList<Long>(), History.parse(null))
        assertEquals(emptyList<Long>(), History.parse(""))
    }
}
