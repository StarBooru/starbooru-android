package eu.filipnone.starbooru.data

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsStoreTest {
    @Test
    fun videosStartWithSound() {
        assertFalse(SettingsStore.read(emptyPreferences()).videoMuted)
    }

    @Test
    fun muteIsKeptUnderTheWebsName() {
        val prefs = mutablePreferencesOf()
        SettingsStore.write(prefs, Settings(videoMuted = true))
        assertEquals(true, prefs[SettingsStore.VIDEO_MUTED])
        assertEquals("video_muted", SettingsStore.VIDEO_MUTED.name)
        assertTrue(SettingsStore.read(prefs).videoMuted)
    }
}
