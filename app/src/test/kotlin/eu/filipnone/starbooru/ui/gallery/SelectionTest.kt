package eu.filipnone.starbooru.ui.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** After the web's tests for `logic/bulk.rs` and `logic/drag_select.rs`. */
class SelectionTest {
    private val loaded = (1L..10L).toList()

    @Test
    fun aTapPicksOrLetsGoOfOnePost() {
        val s = Selection().toggle(3).toggle(5)
        assertEquals(setOf(3L, 5L), s.ids)
        assertEquals(setOf(5L), s.toggle(3).ids)
    }

    @Test
    fun aRangeGoesEitherWayRound() {
        assertEquals((3L..6L).toSet(), Selection().range(loaded, 2, 5, true).ids)
        assertEquals((3L..6L).toSet(), Selection().range(loaded, 5, 2, true).ids)
        val all = Selection().range(loaded, 0, 9, true)
        assertEquals(setOf(1L, 2L, 9L, 10L), all.range(loaded, 2, 7, false).ids)
    }

    @Test
    fun allThenNone() {
        val all = Selection().toggleAll(loaded)
        assertTrue(all.isFull(loaded))
        assertEquals(0, all.toggleAll(loaded).size)
        assertFalse(Selection().isFull(emptyList()))
    }

    @Test
    fun neverMoreThanOneEditTakes() {
        val many = (1L..600L).toList()
        assertEquals(Selection.MAX, Selection().toggleAll(many).size)
        assertTrue(Selection().toggleAll(many).isFull(many))
    }

    @Test
    fun thePickedComeInTheGallerysOrder() {
        val s = Selection(setOf(9L, 2L, 42L))
        assertEquals(listOf(2L, 9L, 42L), s.inOrder(loaded))
    }

    @Test
    fun aDragPicksFromAnUnpickedPostAndLetsGoFromAPickedOne() {
        val before = Selection(setOf(4L, 5L, 6L))
        assertEquals(setOf(1L, 2L, 3L, 4L, 5L, 6L), DragPick(before, from = 1, to = 3).selection(loaded)?.ids)
        assertEquals(setOf(6L), DragPick(before, from = 4, to = 5).selection(loaded)?.ids)
        // Back to where it started, the rest is as it was.
        assertEquals(before.ids + 2L, DragPick(before, from = 2, to = 2).selection(loaded)?.ids)
        assertNull(DragPick(before, from = 99).selection(loaded))
    }

    @Test
    fun thePageScrollsFasterNearerTheEdge() {
        assertEquals(0f, DragPick.edgeSpeed(500f, 0f, 1000f), 0f)
        assertTrue(DragPick.edgeSpeed(10f, 0f, 1000f) < DragPick.edgeSpeed(60f, 0f, 1000f))
        assertTrue(DragPick.edgeSpeed(10f, 0f, 1000f) < 0f)
        assertTrue(DragPick.edgeSpeed(990f, 0f, 1000f) > 0f)
        assertEquals(1600f, DragPick.edgeSpeed(1200f, 0f, 1000f), 0f)
        assertEquals(0f, DragPick.edgeSpeed(Float.NaN, 0f, 1000f), 0f)
    }
}
