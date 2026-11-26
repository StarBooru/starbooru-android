package eu.filipnone.starbooru.ui.explore

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.filipnone.starbooru.core.CoreException
import eu.filipnone.starbooru.core.Explore
import eu.filipnone.starbooru.core.Post
import eu.filipnone.starbooru.core.mixSeed
import eu.filipnone.starbooru.core.sorted
import eu.filipnone.starbooru.data.Account
import eu.filipnone.starbooru.data.AccountState
import eu.filipnone.starbooru.data.History
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExploreState(
    val query: String = "",
    /** The window Trending counts over, in days; a public server's choice. */
    val days: Int = WEEK,
    val explore: Explore? = null,
    val loading: Boolean = false,
    val error: Throwable? = null,
    /** The posts opened lately, as many as loaded so far. */
    val recent: List<Post> = emptyList(),
    /** How many posts the history holds in all. */
    val recentCount: Int = 0,
    val random: List<Post> = emptyList(),
    val shuffling: Boolean = false,
) {
    companion object {
        const val WEEK = 7
    }
}

/**
 * Explore: what was viewed lately, on this day in earlier years, random
 * picks and people; on a public server what's trending too. The top bar's
 * search narrows all of it.
 */
class ExploreViewModel(private val account: Account, private val history: History, private val saved: SavedStateHandle) : ViewModel() {
    private val _state = MutableStateFlow(ExploreState(query = saved[QUERY] ?: "", days = saved[DAYS] ?: ExploreState.WEEK))
    val state: StateFlow<ExploreState> = _state.asStateFlow()

    private var loading: Job? = null
    private var shuffles = 0uL

    /** Whose history this is: one list per account on each server. */
    val owner: String
        get() =
            when (val s = account.state.value) {
                is AccountState.SignedIn -> "${s.server}#${s.user.id}"
                is AccountState.Guest -> "${s.server}#guest"
                else -> ""
            }

    init {
        load()
    }

    fun setQuery(query: String) {
        val q = query.trim()
        if (q == _state.value.query) return
        saved[QUERY] = q
        _state.update { it.copy(query = q) }
        load()
    }

    fun setDays(days: Int) {
        saved[DAYS] = days
        _state.update { it.copy(days = days) }
        load()
    }

    fun load() {
        loading?.cancel()
        loading =
            viewModelScope.launch {
                _state.update { it.copy(loading = true, error = null) }
                val public = account.info.value?.mode == eu.filipnone.starbooru.core.Mode.PUBLIC
                // A private server leaves Trending empty: the shortest window costs it least.
                val days = if (public) _state.value.days else 1
                try {
                    val explore = account.call { it.explore(days.toUInt(), _state.value.query) }
                    _state.update { it.copy(explore = explore, random = explore.random, loading = false) }
                } catch (e: CoreException) {
                    _state.update { it.copy(loading = false, error = e) }
                }
                loadRecent(fresh = true)
            }
    }

    /** The next [History.SHOWN] posts of the history; each is a request. */
    fun loadRecent(fresh: Boolean = false) {
        viewModelScope.launch {
            val ids = history.ids(owner).first()
            val have = if (fresh) emptyList() else _state.value.recent
            val next = ids.filter { id -> have.none { it.id == id } }.take(History.SHOWN)
            val posts =
                next
                    .map { id ->
                        async {
                            try {
                                account.call { it.post(id) }
                            } catch (_: CoreException) {
                                null
                            }
                        }
                    }.awaitAll()
                    .filterNotNull()
                    .filter { !it.deleted }
            _state.update { it.copy(recent = have + posts, recentCount = ids.size) }
        }
    }

    /** Takes the history away; [onCleared] gets a way to put it back. */
    fun clearRecent(onCleared: (undo: () -> Unit) -> Unit) {
        viewModelScope.launch {
            val who = owner
            val ids = history.ids(who).first()
            history.clear(who)
            _state.update { it.copy(recent = emptyList(), recentCount = 0) }
            onCleared {
                viewModelScope.launch {
                    history.set(who, ids)
                    loadRecent(fresh = true)
                }
            }
        }
    }

    /** New random picks within the search. */
    fun shuffle() {
        if (_state.value.shuffling) return
        _state.update { it.copy(shuffling = true) }
        viewModelScope.launch {
            shuffles++
            val seed = mixSeed(System.nanoTime(), shuffles)
            try {
                val page = account.call { it.posts(sorted(_state.value.query, "random"), seed, 1u, RANDOM_COUNT) }
                _state.update { it.copy(random = page.posts, shuffling = false) }
            } catch (e: CoreException) {
                _state.update { it.copy(shuffling = false, error = e) }
            }
        }
    }

    private companion object {
        const val QUERY = "query"
        const val DAYS = "days"
        const val RANDOM_COUNT = 12u
    }
}
