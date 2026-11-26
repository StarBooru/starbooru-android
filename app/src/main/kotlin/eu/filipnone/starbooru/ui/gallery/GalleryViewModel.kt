package eu.filipnone.starbooru.ui.gallery

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.filipnone.starbooru.core.CoreException
import eu.filipnone.starbooru.core.Post
import eu.filipnone.starbooru.core.TagCorrection
import eu.filipnone.starbooru.core.isRandom
import eu.filipnone.starbooru.core.mixSeed
import eu.filipnone.starbooru.core.pageSize
import eu.filipnone.starbooru.data.Account
import eu.filipnone.starbooru.data.Spot
import eu.filipnone.starbooru.data.Spots
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GalleryState(
    val posts: List<Post> = emptyList(),
    val loading: Boolean = false,
    val hasMore: Boolean = true,
    /** The last request's failure; the posts loaded before it stay. */
    val error: Throwable? = null,
    val corrected: List<TagCorrection> = emptyList(),
    /** The first page has answered at least once. */
    val loaded: Boolean = false,
)

/**
 * The results of one search, a page at a time. What was loaded, and the
 * post at the top of the screen, survive the system closing the app: coming
 * back loads as many posts again, in bigger pages, and the screen scrolls
 * back to that post.
 */
class GalleryViewModel(
    private val account: Account,
    val query: String,
    private val saved: SavedStateHandle,
    /** Where searches were left, to come back to; none in tests. */
    private val spots: Spots? = null,
    private val owner: String = "",
    /** The saved searches' keys, whose spots stay. */
    private val savedKeys: () -> Set<String> = { emptySet() },
) : ViewModel() {
    private val _state = MutableStateFlow(GalleryState())
    val state: StateFlow<GalleryState> = _state.asStateFlow()

    /** Keeps `order:random` in the same order from page to page, and in the viewer. */
    var seed: Long? =
        if (isRandom(query)) {
            saved[SEED] ?: mixSeed(System.nanoTime(), 0u).also { saved[SEED] = it }
        } else {
            null
        }
        private set

    /** The post at the top of the screen, to scroll back to. */
    var anchor: Long?
        get() = saved[ANCHOR]
        set(value) {
            if (saved.get<Long>(ANCHOR) == value) return
            saved[ANCHOR] = value
            remember()
        }

    private var remembering: Job? = null

    /** Keeps where this search is, a moment after the scrolling stops. */
    private fun remember() {
        val spots = spots ?: return
        remembering?.cancel()
        remembering =
            viewModelScope.launch {
                delay(REMEMBER_AFTER_MS)
                val spot = Spot(spots.key(query), anchor, _state.value.posts.size, seed)
                spots.put(owner, spot, savedKeys())
            }
    }

    private var page = 0u

    private val _selecting = MutableStateFlow(saved[SELECTING] ?: false)

    /** Whether taps pick posts rather than open them. */
    val selecting: StateFlow<Boolean> = _selecting.asStateFlow()

    private val _selection = MutableStateFlow(Selection(saved.get<LongArray>(SELECTED)?.toSet().orEmpty()))
    val selection: StateFlow<Selection> = _selection.asStateFlow()

    fun select(on: Boolean) {
        _selecting.value = on
        saved[SELECTING] = on
        if (!on) setSelection(Selection())
    }

    fun setSelection(selection: Selection) {
        _selection.value = selection
        saved[SELECTED] = selection.ids.toLongArray()
    }

    /** The posts loaded, by id, in the gallery's order. */
    fun ids(): List<Long> = _state.value.posts.map { it.id }

    /** Takes in a post changed elsewhere: a vote, an edit. */
    fun replace(post: Post) {
        _state.update { st -> st.copy(posts = st.posts.map { if (it.id == post.id) post else it }) }
    }

    /** Takes posts out (to the recycle bin), and answers where each was, to put it back. */
    fun remove(ids: Set<Long>): List<Pair<Int, Post>> {
        val gone = _state.value.posts.withIndex().filter { it.value.id in ids }.map { it.index to it.value }
        _state.update { st -> st.copy(posts = st.posts.filter { it.id !in ids }) }
        setSelection(ids.fold(_selection.value) { s, id -> s.without(id) })
        return gone
    }

    /** Puts posts back where they were, after an Undo. */
    fun putBack(posts: List<Pair<Int, Post>>) {
        _state.update { st ->
            val list = st.posts.toMutableList()
            for ((index, post) in posts.sortedBy { it.first }) {
                if (list.none { it.id == post.id }) list.add(index.coerceIn(0, list.size), post)
            }
            st.copy(posts = list)
        }
    }
    private var job: Job? = null

    init {
        val kept: Int? = saved[LOADED]
        when {
            // Back after the system closed the app: as it was.
            kept != null -> if (kept > pageSize().toInt()) restore(kept) else loadMore()

            // A search opened again: where it was left.
            spots != null ->
                viewModelScope.launch {
                    val spot = spots.find(owner, query)
                    if (spot != null) {
                        saved[ANCHOR] = spot.post
                        if (seed != null && spot.seed != null) seed = spot.seed.also { saved[SEED] = it }
                    }
                    val count = spot?.count ?: 0
                    if (count > pageSize().toInt()) restore(count) else loadMore()
                }

            else -> loadMore()
        }
    }

    fun loadMore() {
        val s = _state.value
        if (job?.isActive == true || !s.hasMore) return
        job =
            viewModelScope.launch {
                _state.update { it.copy(loading = true, error = null) }
                try {
                    val next = page + 1u
                    val result = account.call { it.posts(query, seed, next, pageSize()) }
                    page = next
                    _state.update { st ->
                        val known = st.posts.mapTo(HashSet()) { it.id }
                        val posts = st.posts + result.posts.filter { it.id !in known }
                        saved[LOADED] = posts.size
                        st.copy(
                            posts = posts,
                            loading = false,
                            hasMore = result.hasMore,
                            corrected = if (next == 1u) result.corrected else st.corrected,
                            loaded = true,
                        )
                    }
                } catch (e: CoreException) {
                    _state.update { it.copy(loading = false, error = e, loaded = true) }
                }
            }
    }

    /** Asks for the first page again, after a failure or when pulled. */
    fun reload() {
        job?.cancel()
        page = 0u
        _state.value = GalleryState()
        loadMore()
    }

    /** Loads [count] posts again in pages of up to [RESTORE_PAGE], as many as were loaded. */
    private fun restore(count: Int) {
        val wanted = count.coerceAtMost(RESTORE_MAX)
        val per = RESTORE_PAGE.toUInt()
        job =
            viewModelScope.launch {
                _state.update { it.copy(loading = true) }
                val posts = ArrayList<Post>(wanted)
                val seen = HashSet<Long>()
                var more = true
                var p = 0u
                try {
                    while (more && posts.size < wanted) {
                        p += 1u
                        val result = account.call { it.posts(query, seed, p, per) }
                        // A post added meanwhile shifts the pages: one can come twice.
                        posts += result.posts.filter { seen.add(it.id) }
                        more = result.hasMore
                        if (p == 1u) _state.update { it.copy(corrected = result.corrected) }
                    }
                    // Paging goes on in pages of the usual size after the
                    // ones already loaded.
                    page = (posts.size / pageSize().toInt()).toUInt()
                    val kept = posts.take(page.toInt() * pageSize().toInt()).ifEmpty { posts }
                    _state.update { it.copy(posts = kept, loading = false, hasMore = more || kept.size < posts.size, loaded = true) }
                    saved[LOADED] = kept.size
                } catch (e: CoreException) {
                    _state.update { it.copy(loading = false, error = e, loaded = true) }
                }
            }
    }

    private companion object {
        const val SEED = "seed"
        const val ANCHOR = "anchor"
        const val LOADED = "loaded"
        const val SELECTING = "selecting"
        const val SELECTED = "selected"
        const val REMEMBER_AFTER_MS = 600L
        const val RESTORE_PAGE = 180
        const val RESTORE_MAX = 3000
    }
}
