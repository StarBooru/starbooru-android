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
    /** The post as the page that opened it had it: shown at once, then asked for again. */
    first: Post? = null,
    /** Told of each post shown, for Explore's Recently viewed. */
    private val onSeen: (Long) -> Unit = {},
) : ViewModel() {
    private val posts = LinkedHashMap<Long, Post>(16, 0.75f, true)
    private val sides = HashMap<Long, Neighbors>()
    private val related = HashMap<Long, List<Post>>()
    private val _state: MutableStateFlow<ViewerState>
    val state: StateFlow<ViewerState>
    private var loading: Job? = null

    /** Swipes made before the server said what's beside the post, next counting up. */
    private var steps = 0

    init {
        val id = saved[ID] ?: startId
        val handed = first?.takeIf { it.id == id }
        handed?.let(::keep)
        _state = MutableStateFlow(ViewerState(id, post = handed))
        state = _state.asStateFlow()
        load(id, fresh = handed != null)
    }

    fun retry() = load(_state.value.id)

    /**
     * Moves to the post on [side], if there is one. Before the server has
     * said what's there, the swipe waits for its answer and moves then.
     */
    fun go(side: Side): Boolean {
        val s = _state.value
        val near = s.neighbors
        // Before the server says, the post standing in on that side (the one
        // just left) can still be gone back to.
        val standIn =
            when (side) {
                Side.Prev -> s.prev
                Side.Next -> s.next
            }
        if (near == null && standIn == null) {
            // Over a post that failed to load, a swipe only asks again.
            if (s.error == null) steps = queued(steps, side)
            // The neighbours never came: ask again rather than stay stuck.
            if (loading?.isActive != true) load(s.id)
            return false
        }
        val id =
            when (side) {
                Side.Prev -> near?.prev
                Side.Next -> near?.next
            } ?: standIn?.id ?: run {
                steps = 0
                return false
            }
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

    /** Loads post [id] and what's beside it. [fresh] asks the server even for a post already here. */
    private fun load(
        id: Long,
        fresh: Boolean = false,
    ) {
        loading?.cancel()
        loading =
            viewModelScope.launch {
                val known = posts[id]
                val post =
                    if (known != null && !fresh) {
                        known
                    } else {
                        try {
                            account.call { it.post(id) }.also { keep(it) }
                        } catch (e: CoreException) {
                            // The copy the page had stays, if there is one.
                            known ?: run {
                                _state.update { if (it.id == id && it.post == null) it.copy(error = e) else it }
                                return@launch
                            }
                        }
                    }
                _state.update { if (it.id == id) it.copy(post = post, error = null) else it }
                if (!post.deleted) onSeen(id)
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
                        // The arrows wait; the next swipe asks again. The
                        // swipes waiting for this answer are dropped.
                        steps = 0
                        return@launch
                    }
                _state.update { if (it.id == id) it.copy(neighbors = near) else it }
                if (drain(id, near)) return@launch
                // Both sides at once, each on its own: a slow or failing one
                // doesn't hold the other back.
                launch {
                    val prev = near.prev?.let { fetch(it) }
                    _state.update { if (it.id == id) it.copy(prev = prev) else it }
                }
                launch {
                    val next = near.next?.let { fetch(it) }
                    _state.update { if (it.id == id) it.copy(next = next) else it }
                }
            }
    }

    /**
     * Takes one waiting swipe from post [id], now that its neighbours [near]
     * are known: the post on that side is fetched first, so it shows at
     * once. True once it has moved on, which loads the next post.
     */
    private suspend fun drain(
        id: Long,
        near: Neighbors,
    ): Boolean {
        if (steps == 0) return false
        val side = if (steps > 0) Side.Next else Side.Prev
        val to = if (side == Side.Next) near.next else near.prev
        if (to == null) {
            steps = 0
            return false
        }
        val post = fetch(to)
        _state.update {
            when {
                it.id != id -> it
                side == Side.Next -> it.copy(next = post)
                else -> it.copy(prev = post)
            }
        }
        // A swipe the other way while it came, or a move, has the last word.
        val still = if (side == Side.Next) steps > 0 else steps < 0
        if (_state.value.id != id || !still) return false
        steps -= if (side == Side.Next) 1 else -1
        return go(side)
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
