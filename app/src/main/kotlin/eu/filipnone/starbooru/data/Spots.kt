package eu.filipnone.starbooru.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Where the gallery was in one search: the post at the top and how many were loaded. */
@Serializable
data class Spot(
    /** The search, by [Spots.key]. */
    val key: String,
    val post: Long?,
    val count: Int,
    /** The shuffle of an `order:random` search, so it comes back the same. */
    val seed: Long? = null,
)

/**
 * The places the gallery remembers, so a search opened again (a saved one
 * above all) comes back where it was left: a saved search's for as long as
 * it stays saved, and the latest [MAX] of the rest, as on the web.
 */
class Spots(
    private val store: DataStore<Preferences>,
    /** Two ways of writing one search give one key. */
    private val keyOf: (String) -> String,
) {
    fun key(query: String): String = keyOf(query)

    suspend fun find(
        owner: String,
        query: String,
    ): Spot? {
        val k = key(query)
        return read(owner).firstOrNull { it.key == k }
    }

    /** Remembers [spot] as the latest; [saved] are the saved searches' keys, which stay. */
    suspend fun put(
        owner: String,
        spot: Spot,
        saved: Set<String>,
    ) {
        try {
            store.edit { prefs ->
                val list = listOf(spot) + decode(prefs[slot(owner)]).filter { it.key != spot.key }
                prefs[slot(owner)] = Json.encodeToString(keep(list, saved))
            }
        } catch (_: IOException) {
        }
    }

    private suspend fun read(owner: String): List<Spot> = decode(store.data.catch { emit(emptyPreferences()) }.first()[slot(owner)])

    private fun slot(owner: String) = stringPreferencesKey("spots:$owner")

    private fun decode(text: String?): List<Spot> =
        try {
            text?.let { Json.decodeFromString<List<Spot>>(it) }.orEmpty()
        } catch (_: SerializationException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }

    companion object {
        /** Searches remembered besides the saved ones. */
        const val MAX = 30

        /** Saved searches remembered, at most: as many as the server saves. */
        const val MAX_SAVED = 100

        /** [spots], the latest first, trimmed: a saved search's stays, the rest keep the latest [MAX]. */
        fun keep(
            spots: List<Spot>,
            saved: Set<String>,
        ): List<Spot> {
            var pinned = 0
            var others = 0
            val seen = HashSet<String>()
            return spots.filter { spot ->
                if (!seen.add(spot.key)) return@filter false
                if (spot.key in saved) ++pinned <= MAX_SAVED else ++others <= MAX
            }
        }
    }
}
