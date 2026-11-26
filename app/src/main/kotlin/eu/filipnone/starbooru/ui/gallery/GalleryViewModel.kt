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
import kotlinx.coroutines.Job
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
    val loaded: Boolean = false
)

/**
 * The results of one search, a page at a time. What was loaded, and the
 * post at the top of the screen, survive the system closing the app: coming
 * back loads as many posts again, in bigger pages, and the screen scrolls
 * back to that post.
 */
class GalleryViewModel(private val account: Account, val query: String, private val saved: SavedStateHandle) : ViewModel() {
    private val _state = MutableStateFlow(GalleryState())
    val state: StateFlow<GalleryState> = _state.asStateFlow()

    /** Keeps `order:random` in the same order from page to page, and in the viewer. */
    val seed: Long? =
        if (isRandom(query)) {
            saved[SEED] ?: mixSeed(System.nanoTime(), 0u).also { saved[SEED] = it }
        } else {
            null
        }

    /** The post at the top of the screen, to scroll back to. */
    var anchor: Long?
        get() = saved[ANCHOR]
        set(value) {
            saved[ANCHOR] = value
        }

    private var page = 0u
    private var job: Job? = null

    init {
        val restore = saved[LOADED] ?: 0
        if (restore > pageSize().toInt()) restore(restore) else loadMore()
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
                            loaded = true
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
                var more = true
                var p = 0u
                try {
                    while (more && posts.size < wanted) {
                        p += 1u
                        val result = account.call { it.posts(query, seed, p, per) }
                        posts += result.posts
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
        const val RESTORE_PAGE = 180
        const val RESTORE_MAX = 3000
    }
}
