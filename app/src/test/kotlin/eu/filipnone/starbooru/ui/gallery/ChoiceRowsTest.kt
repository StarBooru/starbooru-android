package eu.filipnone.starbooru.ui.gallery

import org.junit.Assert.assertEquals
import org.junit.Test

class ChoiceRowsTest {
    @Test
    fun choicesThatFitShareOneRow() {
        assertEquals(4, ChoiceRows.columns(count = 4, fit = 4))
        assertEquals(3, ChoiceRows.columns(count = 3, fit = 5))
    }

    @Test
    fun choicesThatDontFitMakeEvenRows() {
        // Four shapes where three fit: two and two, not three and one.
        assertEquals(2, ChoiceRows.columns(count = 4, fit = 3))
        assertEquals(2, ChoiceRows.columns(count = 4, fit = 2))
        assertEquals(2, ChoiceRows.columns(count = 3, fit = 2))
        assertEquals(3, ChoiceRows.columns(count = 5, fit = 4))
        assertEquals(1, ChoiceRows.columns(count = 4, fit = 1))
        assertEquals(1, ChoiceRows.columns(count = 4, fit = 0))
        assertEquals(1, ChoiceRows.columns(count = 0, fit = 3))
    }

    @Test
    fun theMenuWidensOverAWideGrid() {
        // A phone held upright, then on its side or a tablet.
        assertEquals(300, ChoiceRows.menuWidth(395.0))
        assertEquals(300, ChoiceRows.menuWidth(599.0))
        assertEquals(400, ChoiceRows.menuWidth(600.0))
        assertEquals(400, ChoiceRows.menuWidth(780.0))
        assertEquals(300, ChoiceRows.menuWidth(Double.NaN))
    }

    @Test
    fun aCellHoldsTheWidestLabelAndItsPadding() {
        // 300 wide: cells of 63 + 2 * 8 with a 1 px line between.
        assertEquals(3, ChoiceRows.fit(width = 300f, widest = 63f, padding = 8f, line = 1f))
        assertEquals(4, ChoiceRows.fit(width = 300f, widest = 55f, padding = 8f, line = 1f))
        // Exactly four.
        assertEquals(4, ChoiceRows.fit(width = 4 * 80f + 3, widest = 64f, padding = 8f, line = 1f))
        assertEquals(1, ChoiceRows.fit(width = 50f, widest = 63f, padding = 8f, line = 1f))
        assertEquals(1, ChoiceRows.fit(width = Float.NaN, widest = 63f, padding = 8f, line = 1f))
        assertEquals(1, ChoiceRows.fit(width = 300f, widest = 0f, padding = 0f, line = 1f))
    }
}
