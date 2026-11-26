package eu.filipnone.starbooru.ui.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import eu.filipnone.starbooru.R
import java.text.Normalizer

/** Settings' seven groups, as on the web. */
enum class Group(
    @param:StringRes val title: Int,
    @param:StringRes val note: Int,
    @param:DrawableRes val icon: Int,
    /** Whether a guest has it. */
    val forGuests: Boolean,
) {
    Account(R.string.settings_group_account, R.string.settings_account, R.drawable.ic_user, true),
    Appearance(R.string.settings_appearance, R.string.settings_group_appearance_note, R.drawable.ic_palette, true),
    Viewing(R.string.settings_group_viewing, R.string.settings_group_viewing_note, R.drawable.ic_gallery, true),
    Battery(R.string.settings_group_battery, R.string.settings_group_battery_note, R.drawable.ic_battery, false),
    Library(R.string.settings_library, R.string.settings_group_library_note, R.drawable.ic_folder, false),
    Sharing(R.string.settings_group_sharing, R.string.settings_group_sharing_note, R.drawable.ic_share, false),
    About(R.string.settings_group_about, R.string.settings_group_about_note, R.drawable.ic_info, true),
    ;

    companion object {
        fun shown(guest: Boolean): List<Group> = entries.filter { !guest || it.forGuests }
    }
}

/** A card a search finds: its group, its id and the texts it shows. */
data class Card(
    val group: Group,
    val id: String,
    /** Only for an account, not for a guest. */
    val account: Boolean,
    val texts: List<Int>,
)

/** A group with something the search found: all its cards, or these. */
data class Found(
    val group: Group,
    /** Null for all of them. */
    val cards: Set<String>?,
)

/** The cards of Settings, each with the words that find it. */
object SettingsIndex {
    val CARDS =
        listOf(
            Card(
                Group.Account,
                "account",
                true,
                listOf(R.string.settings_account, R.string.settings_server_version, R.string.settings_sign_out),
            ),
            Card(Group.Account, "guest", false, listOf(R.string.guest_sign_in, R.string.guest_sign_up)),
            Card(
                Group.Appearance,
                "theme",
                false,
                listOf(R.string.settings_theme, R.string.theme_system, R.string.theme_light, R.string.theme_dark, R.string.settings_oled),
            ),
            Card(
                Group.Appearance,
                "accent",
                false,
                listOf(
                    R.string.settings_accent,
                    R.string.accent_system,
                    R.string.accent_violet,
                    R.string.accent_pink,
                    R.string.accent_coral,
                    R.string.accent_amber,
                    R.string.accent_green,
                    R.string.accent_teal,
                    R.string.accent_blue,
                    R.string.accent_red,
                ),
            ),
            Card(Group.Appearance, "language", false, listOf(R.string.settings_language, R.string.lang_english, R.string.lang_italian)),
            Card(
                Group.Viewing,
                "thumbnails",
                false,
                listOf(R.string.settings_tiles, R.string.settings_tiles_spaced, R.string.settings_tiles_flush),
            ),
            Card(Group.Viewing, "viewer", false, listOf(R.string.settings_viewer, R.string.settings_originals)),
            Card(
                Group.Viewing,
                "gestures",
                true,
                listOf(R.string.settings_gestures, R.string.settings_swipe_left, R.string.settings_swipe_right),
            ),
            Card(
                Group.Viewing,
                "hand",
                false,
                listOf(R.string.settings_hand, R.string.hand_either, R.string.hand_left, R.string.hand_right),
            ),
            Card(
                Group.Battery,
                "battery-saver",
                true,
                listOf(R.string.settings_saver, R.string.saver_does_videos, R.string.saver_does_preload, R.string.saver_does_motion),
            ),
            Card(
                Group.Battery,
                "effects",
                true,
                listOf(R.string.settings_effects, R.string.effects_does_glass, R.string.effects_does_thumbs, R.string.effects_does_glow),
            ),
            Card(Group.Battery, "cache", true, listOf(R.string.cache_title, R.string.cache_limit, R.string.cache_clear)),
            Card(Group.Library, "library", true, listOf(R.string.trash_title)),
            Card(
                Group.About,
                "help",
                false,
                listOf(R.string.settings_help_legal, R.string.help_title, R.string.privacy_title, R.string.terms_title),
            ),
            Card(
                Group.About,
                "about",
                false,
                listOf(R.string.about_title, R.string.about_version, R.string.about_github, R.string.about_credits),
            ),
        )

    /** The words of [q], folded, in the order typed. */
    fun words(q: String): List<String> = q.split(Regex("\\s+")).filter { it.isNotEmpty() }.map(::fold)

    /** [s] in lower case without accents, so `perche` finds `Perché`. */
    fun fold(s: String): String =
        Normalizer
            .normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")

    /** Whether the [places], together, hold every one of [words]. No words: everything does. */
    fun holdsAll(
        words: List<String>,
        places: List<String>,
    ): Boolean {
        if (words.isEmpty()) return true
        val all = places.joinToString("\n") { fold(it) }
        return words.all { it in all }
    }

    /**
     * What a search for [words] finds among [groups], in their order: a group
     * whose name or note holds every word is found whole, otherwise the cards
     * whose own words, their group's and their id hold them all. [text] reads
     * a string resource.
     */
    fun find(
        words: List<String>,
        groups: List<Group>,
        guest: Boolean,
        text: (Int) -> String,
    ): List<Found> {
        if (words.isEmpty()) return emptyList()
        return groups.mapNotNull { group ->
            val named = listOf(text(group.title), text(group.note))
            if (holdsAll(words, named)) return@mapNotNull Found(group, null)
            val cards =
                CARDS
                    .filter { it.group == group && (!guest || !it.account) && (guest || it.id != "guest") }
                    .filter { card -> holdsAll(words, card.texts.map(text) + card.id.replace('-', ' ') + named) }
                    .map { it.id }
            if (cards.isEmpty()) null else Found(group, cards.toSet())
        }
    }
}
