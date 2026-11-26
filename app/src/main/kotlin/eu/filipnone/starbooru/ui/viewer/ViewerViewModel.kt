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
    val error: Throwable? = null
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
    private val saved: SavedStateHandle
) : ViewModel() {
    private val posts = LinkedHashMap<Long, Post>(16, 0.75f, true)
    private val sides = HashMap<Long, Neighbors>()
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
        val id =
            when (side) {
                Side.Prev -> s.neighbors?.prev
                Side.Next -> s.neighbors?.next
            } ?: return false
        saved[ID] = id
        _state.value = ViewerState(id, posts[id], neighbors = sides[id])
        load(id)
        return true
    }

    /** Takes in a post changed elsewhere (a vote, an edit). */
    fun replace(post: Post) {
        posts[post.id] = post
        _state.update { s ->
            s.copy(
                post = if (s.post?.id == post.id) post else s.post,
                prev = if (s.prev?.id == post.id) post else s.prev,
                next = if (s.next?.id == post.id) post else s.next
            )
        }
    }

    private fun load(id: Long) {
        loading?.cancel()
        loading =
            viewModelScope.launch {
                try {
                    val post = posts[id] ?: account.call { it.post(id) }.also { keep(it) }
                    _state.update { if (it.id == id) it.copy(post = post, error = null) else it }
                    val near = sides[id] ?: account.call { it.neighbors(id, query, seed) }.also { sides[id] = it }
                    _state.update { if (it.id == id) it.copy(neighbors = near) else it }
                    val prev = near.prev?.let { p -> posts[p] ?: account.call { it.post(p) }.also { keep(it) } }
                    val next = near.next?.let { n -> posts[n] ?: account.call { it.post(n) }.also { keep(it) } }
                    _state.update { if (it.id == id) it.copy(prev = prev, next = next) else it }
                } catch (e: CoreException) {
                    _state.update { if (it.id == id && it.post == null) it.copy(error = e) else it }
                }
            }
    }

    private fun keep(post: Post) {
        posts[post.id] = post
        while (posts.size > KEEP) posts.remove(posts.keys.first())
    }

    private companion object {
        const val ID = "id"
        const val KEEP = 24
    }
}
