package eu.filipnone.starbooru.ui.settings

import eu.filipnone.starbooru.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** After the web's tests for `logic/filter.rs` and `logic/settings.rs`. */
class SettingsIndexTest {
    private val text =
        mapOf(
            R.string.settings_group_account to "Account and security",
            R.string.settings_account to "Account",
            R.string.settings_appearance to "Appearance",
            R.string.settings_group_appearance_note to "Theme, colour and language",
            R.string.settings_theme to "Theme",
            R.string.settings_oled to "Black background",
            R.string.settings_gestures to "Gestures",
            R.string.settings_swipe_left to "Swipe left",
            R.string.settings_hand to "Hand",
            R.string.hand_left to "Left",
        )

    private fun read(id: Int) = text[id].orEmpty()

    @Test
    fun wordsAreFoldedAndSplitOnSpaces() {
        assertEquals(listOf("perche", "cosi", "ae"), SettingsIndex.words("  Perché Così ÀÉ "))
        assertTrue(SettingsIndex.holdsAll(SettingsIndex.words("perche"), listOf("Perché no")))
        assertTrue(SettingsIndex.holdsAll(emptyList(), listOf("x")))
        assertFalse(SettingsIndex.holdsAll(SettingsIndex.words("sunset cat"), listOf("sunset.jpg")))
    }

    @Test
    fun aGroupsNameFindsItWhole() {
        val found = SettingsIndex.find(SettingsIndex.words("colour"), Group.entries, guest = false, ::read)
        assertEquals(listOf(Found(Group.Appearance, null)), found)
    }

    @Test
    fun aCardsWordsFindOnlyIt() {
        val found = SettingsIndex.find(SettingsIndex.words("black"), Group.entries, guest = false, ::read)
        assertEquals(listOf(Found(Group.Appearance, setOf("theme"))), found)
    }

    @Test
    fun aGuestDoesntFindWhatNeedsAnAccount() {
        assertTrue(SettingsIndex.find(SettingsIndex.words("swipe"), Group.shown(true), guest = true, ::read).isEmpty())
        assertEquals(1, SettingsIndex.find(SettingsIndex.words("swipe"), Group.shown(false), guest = false, ::read).size)
    }

    @Test
    fun aGuestSeesFourGroups() {
        assertEquals(listOf(Group.Account, Group.Appearance, Group.Viewing, Group.About), Group.shown(true))
        assertEquals(7, Group.shown(false).size)
    }
}
