package eu.filipnone.starbooru.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import eu.filipnone.starbooru.core.GalleryView
import eu.filipnone.starbooru.ui.gestures.SwipeAction
import eu.filipnone.starbooru.ui.theme.Seed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** [Settings] in DataStore. Unknown or missing values read as the default. */
class SettingsStore(private val store: DataStore<Preferences>) {
    val settings: Flow<Settings> =
        store.data
            .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
            .map(::read)

    suspend fun update(change: (Settings) -> Settings) {
        store.edit { prefs -> write(prefs, change(read(prefs))) }
    }

    internal companion object {
        val THEME = stringPreferencesKey("theme")
        val OLED = booleanPreferencesKey("oled")
        val WALLPAPER = booleanPreferencesKey("wallpaper_colours")
        val HUE = doublePreferencesKey("seed_hue")
        val CHROMA = doublePreferencesKey("seed_chroma")
        val LIGHTNESS = doublePreferencesKey("seed_lightness")
        val HAND = stringPreferencesKey("hand")
        val VIEW = stringPreferencesKey("gallery_view")
        val COLUMNS = intPreferencesKey("gallery_columns")
        val FLUSH = booleanPreferencesKey("gallery_flush")
        val CACHE_MB = intPreferencesKey("cache_mb")
        val SWIPE_LEFT = stringPreferencesKey("swipe_left")
        val SWIPE_RIGHT = stringPreferencesKey("swipe_right")
        val EFFECTS = stringPreferencesKey("effects")
        val SAVER = stringPreferencesKey("battery_saver")
        val ORIGINALS = booleanPreferencesKey("originals")

        inline fun <reified E : Enum<E>> enumOf(name: String?, default: E): E = enumValues<E>().firstOrNull { it.name == name } ?: default

        fun read(prefs: Preferences): Settings {
            val d = Settings()
            return Settings(
                theme = enumOf(prefs[THEME], d.theme),
                oled = prefs[OLED] ?: d.oled,
                wallpaperColours = prefs[WALLPAPER] ?: d.wallpaperColours,
                seed =
                    Seed(
                        hue = prefs[HUE] ?: d.seed.hue,
                        chroma = prefs[CHROMA] ?: d.seed.chroma,
                        lightness = prefs[LIGHTNESS] ?: d.seed.lightness,
                    ),
                hand = enumOf(prefs[HAND], d.hand),
                galleryView = enumOf(prefs[VIEW], d.galleryView),
                columns = prefs[COLUMNS]?.takeIf { it in 1..24 },
                flush = prefs[FLUSH] ?: d.flush,
                cacheMb = prefs[CACHE_MB]?.takeIf { it in 0..65_536 } ?: d.cacheMb,
                swipeLeft = SwipeAction.of(prefs[SWIPE_LEFT]),
                swipeRight = SwipeAction.of(prefs[SWIPE_RIGHT]),
                effects = enumOf(prefs[EFFECTS], d.effects),
                saver = enumOf(prefs[SAVER], d.saver),
                originals = prefs[ORIGINALS] ?: d.originals,
            )
        }

        fun write(
            prefs: MutablePreferences,
            s: Settings,
        ) {
            prefs[THEME] = s.theme.name
            prefs[OLED] = s.oled
            prefs[WALLPAPER] = s.wallpaperColours
            prefs[HUE] = s.seed.hue
            prefs[CHROMA] = s.seed.chroma
            prefs[LIGHTNESS] = s.seed.lightness
            prefs[HAND] = s.hand.name
            prefs[VIEW] = s.galleryView.name
            val columns = s.columns
            if (columns == null) prefs.remove(COLUMNS) else prefs[COLUMNS] = columns
            prefs[FLUSH] = s.flush
            prefs[CACHE_MB] = s.cacheMb
            prefs[SWIPE_LEFT] = s.swipeLeft.key
            prefs[SWIPE_RIGHT] = s.swipeRight.key
            prefs[EFFECTS] = s.effects.name
            prefs[SAVER] = s.saver.name
            prefs[ORIGINALS] = s.originals
        }
    }
}
