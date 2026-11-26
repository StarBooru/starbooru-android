package eu.filipnone.starbooru.ui.viewer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.filipnone.starbooru.core.CoreException
import eu.filipnone.starbooru.core.Neighbors
import eu.filipnone.starbooru.core.Post
import eu.filipnone.starbooru.data.Account
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One side of the current post. */
enum class Side { Prev, Next }

data class ViewerState(
    val id: Long,
    val post: Post? = null,
    val prev: Post? = null,
    val next: Post? = null,
    /** Whether there is a post on each side, once the server has said. */
    val neighbors: Neighbors? = null,
    val error: Throwable? = null,
    /** Posts like this one, for the row under it. */
    val related: List<Post> = emptyList(),
)

/**
 * The post on show and the ones before and after it in the search it was
 * opened from. Both sides are fetched ahead, so a swipe shows the next
 * picture at once. The post on show survives the system closing the app.
 */
class ViewerViewModel(
    private val account: Account,
    startId: Long,
    val query: String,
    val seed: Long?,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val posts = LinkedHashMap<Long, Post>(16, 0.75f, true)
    private val sides = HashMap<Long, Neighbors>()
    private val related = HashMap<Long, List<Post>>()
    private val _state = MutableStateFlow(ViewerState(saved[ID] ?: startId))
    val state: StateFlow<ViewerState> = _state.asStateFlow()
    private var loading: Job? = null

    init {
        load(_state.value.id)
    }

    fun retry() = load(_state.value.id)

    /** Moves to the post on [side], if there is one. */
    fun go(side: Side): Boolean {
        val s = _state.value
        val near = s.neighbors
        if (near == null) {
            // The neighbours never came: ask again rather than stay stuck.
            if (loading?.isActive != true) load(s.id)
            return false
        }
        val id =
            when (side) {
                Side.Prev -> near.prev
                Side.Next -> near.next
            } ?: return false
        saved[ID] = id
        val known = sides[id]
        // What's already here stands in until the server answers: the post
        // just left is the new one's other side.
        val prev = known?.prev?.let { posts[it] } ?: if (side == Side.Next && known == null) s.post else null
        val next = known?.next?.let { posts[it] } ?: if (side == Side.Prev && known == null) s.post else null
        _state.value = ViewerState(id, posts[id], prev = prev, next = next, neighbors = known, related = related[id].orEmpty())
        load(id)
        return true
    }

    /**
     * The post on show went to the recycle bin: on to the next one, or the
     * one before. False when there's neither, and the viewer closes.
     */
    fun gone(): Boolean {
        val s = _state.value
        s.post?.let { posts[it.id] = it.copy(deleted = true) }
        sides.clear()
        return go(Side.Next) || go(Side.Prev)
    }

    /** Takes in a post changed elsewhere (a vote, an edit). */
    fun replace(post: Post) {
        posts[post.id] = post
        _state.update { s ->
            s.copy(
                post = if (s.post?.id == post.id) post else s.post,
                prev = if (s.prev?.id == post.id) post else s.prev,
                next = if (s.next?.id == post.id) post else s.next,
            )
        }
    }

    private fun load(id: Long) {
        loading?.cancel()
        loading =
            viewModelScope.launch {
                val post =
                    try {
                        posts[id] ?: account.call { it.post(id) }.also { keep(it) }
                    } catch (e: CoreException) {
                        _state.update { if (it.id == id && it.post == null) it.copy(error = e) else it }
                        return@launch
                    }
                _state.update { if (it.id == id) it.copy(post = post, error = null) else it }
                launch {
                    val found =
                        related[id] ?: try {
                            account.call { it.related(id, RELATED.toUInt()) }
                        } catch (_: CoreException) {
                            // The row stays empty; nothing else depends on it.
                            null
                        }
                    if (found != null) {
                        related[id] = found
                        _state.update { if (it.id == id) it.copy(related = found) else it }
                    }
                }
                val near =
                    try {
                        sides[id] ?: account.call { it.neighbors(id, query, seed) }.also { sides[id] = it }
                    } catch (_: CoreException) {
                        // The arrows wait; the next swipe asks again.
                        return@launch
                    }
                _state.update { if (it.id == id) it.copy(neighbors = near) else it }
                // Each side on its own, so one that fails leaves the other.
                val prev = near.prev?.let { fetch(it) }
                _state.update { if (it.id == id) it.copy(prev = prev) else it }
                val next = near.next?.let { fetch(it) }
                _state.update { if (it.id == id) it.copy(next = next) else it }
            }
    }

    private suspend fun fetch(id: Long): Post? =
        posts[id] ?: try {
            account.call { it.post(id) }.also { keep(it) }
        } catch (_: CoreException) {
            null
        }

    private fun keep(post: Post) {
        posts[post.id] = post
        while (posts.size > KEEP) posts.remove(posts.keys.first())
    }

    private companion object {
        const val ID = "id"
        const val KEEP = 24
        const val RELATED = 12
    }
}
