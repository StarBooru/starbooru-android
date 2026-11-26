package eu.filipnone.starbooru.ui.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.filipnone.starbooru.core.CoreException
import eu.filipnone.starbooru.core.Post
import eu.filipnone.starbooru.core.pageSize
import eu.filipnone.starbooru.core.trashQuery
import eu.filipnone.starbooru.data.Account
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrashState(
    val posts: List<Post> = emptyList(),
    val loading: Boolean = false,
    val hasMore: Boolean = true,
    val loaded: Boolean = false,
    val error: Throwable? = null,
    /** Posts with a request under way, whose buttons wait. */
    val busy: Set<Long> = emptySet(),
)

/** What happened, for the screen to say. */
sealed interface TrashNews {
    data class Restored(val count: Int) : TrashNews

    data class Purged(val count: Int) : TrashNews

    data class Failed(val id: Long?, val restoring: Boolean, val error: Throwable) : TrashNews
}

/** The recycle bin: the posts moved there, a page at a time, as a search for them. */
class TrashViewModel(private val account: Account) : ViewModel() {
    private val _state = MutableStateFlow(TrashState())
    val state: StateFlow<TrashState> = _state.asStateFlow()

    private val _news = MutableStateFlow<TrashNews?>(null)
    val news: StateFlow<TrashNews?> = _news.asStateFlow()

    private var page = 0u
    private var job: Job? = null

    init {
        loadMore()
    }

    fun told() {
        _news.value = null
    }

    fun loadMore() {
        if (job?.isActive == true || !_state.value.hasMore) return
        job =
            viewModelScope.launch {
                _state.update { it.copy(loading = true, error = null) }
                try {
                    val next = page + 1u
                    val result = account.call { it.posts(trashQuery(), null, next, pageSize()) }
                    page = next
                    _state.update { st ->
                        val known = st.posts.mapTo(HashSet()) { it.id }
                        st.copy(
                            posts = st.posts + result.posts.filter { it.id !in known },
                            loading = false,
                            hasMore = result.hasMore,
                            loaded = true,
                        )
                    }
                } catch (e: CoreException) {
                    _state.update { it.copy(loading = false, error = e, loaded = true) }
                }
            }
    }

    fun reload() {
        job?.cancel()
        page = 0u
        _state.value = TrashState()
        loadMore()
    }

    fun restore(post: Post) =
        act(post.id, restoring = true) {
            account.call { it.restorePost(post.id) }
            _news.value = TrashNews.Restored(1)
        }

    fun purge(post: Post) =
        act(post.id, restoring = false) {
            account.call { it.purgePost(post.id) }
            _news.value = TrashNews.Purged(1)
        }

    /** Brings back every post loaded, one after another; the bin then reloads. */
    fun restoreAll() {
        val posts = _state.value.posts
        viewModelScope.launch {
            var done = 0
            for (post in posts) {
                try {
                    account.call { it.restorePost(post.id) }
                    done++
                } catch (e: CoreException) {
                    _news.value = TrashNews.Failed(post.id, restoring = true, error = e)
                    break
                }
            }
            if (done > 0) _news.value = TrashNews.Restored(done)
            reload()
        }
    }

    /** Deletes everything in the bin for good (administrators). */
    fun empty() {
        viewModelScope.launch {
            try {
                val result = account.call { it.emptyTrash() }
                _news.value = TrashNews.Purged(result.purged.toInt())
            } catch (e: CoreException) {
                _news.value = TrashNews.Failed(null, restoring = false, error = e)
            }
            reload()
        }
    }

    private fun act(
        id: Long,
        restoring: Boolean,
        block: suspend () -> Unit,
    ) {
        if (id in _state.value.busy) return
        _state.update { it.copy(busy = it.busy + id) }
        viewModelScope.launch {
            try {
                block()
                _state.update { st -> st.copy(posts = st.posts.filter { it.id != id }) }
            } catch (e: CoreException) {
                _news.value = TrashNews.Failed(id, restoring, e)
            } finally {
                _state.update { it.copy(busy = it.busy - id) }
            }
        }
    }
}
