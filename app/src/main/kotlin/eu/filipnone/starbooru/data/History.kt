package eu.filipnone.starbooru.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * The posts opened in the viewer, which Explore lists as Recently viewed:
 * the latest first, each once, the oldest dropped past [MAX]. Kept on the
 * device, apart for each account on each server, as on the web.
 */
class History(private val store: DataStore<Preferences>) {
    fun ids(owner: String): Flow<List<Long>> =
        store.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> parse(prefs[key(owner)]) }

    suspend fun open(
        owner: String,
        id: Long,
    ) = change(owner) { opened(it, id) }

    suspend fun clear(owner: String) = change(owner) { emptyList() }

    /** Puts back a list taken away, after an Undo. */
    suspend fun set(
        owner: String,
        ids: List<Long>,
    ) = change(owner) { tidy(ids) }

    suspend fun drop(
        owner: String,
        gone: Set<Long>,
    ) = change(owner) { list -> list.filter { it !in gone } }

    private suspend fun change(
        owner: String,
        how: (List<Long>) -> List<Long>,
    ) {
        try {
            store.edit { prefs ->
                val next = how(parse(prefs[key(owner)]))
                prefs[key(owner)] = next.joinToString(",")
            }
        } catch (_: IOException) {
            // A full disk loses this visit, nothing more.
        }
    }

    private fun key(owner: String) = stringPreferencesKey("viewed:$owner")

    companion object {
        const val MAX = 60

        /** How many Explore shows at first, and again with each Load more: each is a request. */
        const val SHOWN = 12

        /** [id] opened: it goes first, out of where it was. */
        fun opened(
            ids: List<Long>,
            id: Long,
        ): List<Long> = tidy(listOf(id) + ids)

        /** Real ids, each once where it first comes, no more than [MAX]. */
        fun tidy(ids: List<Long>): List<Long> = ids.filter { it > 0 }.distinct().take(MAX)

        fun parse(text: String?): List<Long> = tidy(text.orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() })
    }
}
