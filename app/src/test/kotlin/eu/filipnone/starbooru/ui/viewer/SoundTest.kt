package eu.filipnone.starbooru.ui.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundTest {
    @Test
    fun muteCarriesOverAndIsKept() {
        val kept = mutableListOf<Boolean>()
        val sound = Sound(muted = false) { kept += it }
        sound.press(true)
        assertTrue(sound.muted)
        sound.press(false)
        assertFalse(sound.muted)
        assertEquals(listOf(true, false), kept)
    }

    @Test
    fun pressingWhatItAlreadyIsKeepsNothing() {
        val kept = mutableListOf<Boolean>()
        val sound = Sound(muted = true) { kept += it }
        sound.press(true)
        assertTrue(kept.isEmpty())
    }

    @Test
    fun whatSettingsHoldsIsHeardButNotWrittenBack() {
        val kept = mutableListOf<Boolean>()
        val sound = Sound(muted = false) { kept += it }
        sound.heard(true)
        assertTrue(sound.muted)
        assertTrue(kept.isEmpty())
    }
}
