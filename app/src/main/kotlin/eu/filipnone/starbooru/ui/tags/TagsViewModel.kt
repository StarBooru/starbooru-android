package eu.filipnone.starbooru.ui.tags

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.filipnone.starbooru.core.CoreException
import eu.filipnone.starbooru.core.Tag
import eu.filipnone.starbooru.core.TagCategory
import eu.filipnone.starbooru.core.TagDetail
import eu.filipnone.starbooru.core.TagOrder
import eu.filipnone.starbooru.core.TagUpdate
import eu.filipnone.starbooru.data.Account
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TagsState(
    val tags: List<Tag> = emptyList(),
    val order: TagOrder = TagOrder.COUNT,
    val filter: String = "",
    /** Only tags of this category; null for all. */
    val category: TagCategory? = null,
    val loading: Boolean = false,
    val hasMore: Boolean = true,
    val loaded: Boolean = false,
    val error: Throwable? = null,
) {
    val shown: List<Tag> get() = if (category == null) tags else tags.filter { it.category == category }
}

/** The Tags page: every tag in use, most used or A–Z, narrowed by name or category. */
class TagsViewModel(private val account: Account, private val saved: SavedStateHandle) : ViewModel() {
    private val _state =
        MutableStateFlow(
            TagsState(
                order = if (saved.get<String>(ORDER) == TagOrder.NAME.name) TagOrder.NAME else TagOrder.COUNT,
                filter = saved[FILTER] ?: "",
                category = TagCategory.entries.firstOrNull { it.name == saved.get<String>(CATEGORY) },
            ),
        )
    val state: StateFlow<TagsState> = _state.asStateFlow()

    private var page = 0u
    private var job: Job? = null

    init {
        loadMore()
    }

    fun setOrder(order: TagOrder) {
        saved[ORDER] = order.name
        _state.update { it.copy(order = order) }
        restart(0)
    }

    fun setFilter(text: String) {
        saved[FILTER] = text
        _state.update { it.copy(filter = text) }
        restart(TYPING_PAUSE_MS)
    }

    fun setCategory(category: TagCategory?) {
        saved[CATEGORY] = category?.name
        _state.update { it.copy(category = category) }
    }

    private fun restart(pause: Long) {
        job?.cancel()
        page = 0u
        _state.update { it.copy(tags = emptyList(), hasMore = true, loaded = false, error = null) }
        job =
            viewModelScope.launch {
                delay(pause)
                fetch()
            }
    }

    fun loadMore() {
        if (job?.isActive == true || !_state.value.hasMore) return
        job = viewModelScope.launch { fetch() }
    }

    private suspend fun fetch() {
        val s = _state.value
        _state.update { it.copy(loading = true, error = null) }
        try {
            val next = page + 1u
            val result = account.call { it.tagList(s.filter.trim().lowercase(), s.order, next, PAGE) }
            page = next
            _state.update { st ->
                val known = st.tags.mapTo(HashSet()) { it.id }
                st.copy(tags = st.tags + result.tags.filter { it.id !in known }, loading = false, hasMore = result.hasMore, loaded = true)
            }
        } catch (e: CoreException) {
            _state.update { it.copy(loading = false, error = e, loaded = true) }
        }
    }

    private companion object {
        const val ORDER = "order"
        const val FILTER = "filter"
        const val CATEGORY = "category"
        const val TYPING_PAUSE_MS = 250L
        const val PAGE = 100u
    }
}

data class TagState(val detail: TagDetail? = null, val error: Throwable? = null, val busy: Boolean = false)

/** What a change to a tag did, for the screen to say. */
sealed interface TagNews {
    data class Recoloured(val name: String, val category: TagCategory) : TagNews

    data class Failed(val error: Throwable) : TagNews
}

/** A tag's page: its category, its place in the tree, its aliases and implications. */
class TagViewModel(private val account: Account, val name: String) : ViewModel() {
    private val _state = MutableStateFlow(TagState())
    val state: StateFlow<TagState> = _state.asStateFlow()

    private val _news = MutableStateFlow<TagNews?>(null)
    val news: StateFlow<TagNews?> = _news.asStateFlow()

    init {
        load()
    }

    fun told() {
        _news.value = null
    }

    fun load() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(detail = account.call { api -> api.tag(name) }, error = null) }
            } catch (e: CoreException) {
                _state.update { it.copy(error = e) }
            }
        }
    }

    fun setCategory(category: TagCategory) =
        change {
            account.call { it.updateTag(name, TagUpdate(category = category)) }
            _news.value = TagNews.Recoloured(name, category)
            account.call { it.tag(name) }
        }

    /** Moves the tag under [parent], or to the top for an empty one. */
    fun setParent(parent: String) =
        change {
            account.call { it.updateTag(name, TagUpdate(parent = parent.trim().lowercase())) }
            account.call { it.tag(name) }
        }

    fun addAlias(alias: String) = change { account.call { it.addAlias(name, alias.trim().lowercase()) } }

    fun removeAlias(alias: String) = change { account.call { it.removeAlias(name, alias) } }

    fun addImplication(tag: String) = change { account.call { it.addImplication(name, tag.trim().lowercase()) } }

    fun removeImplication(tag: String) = change { account.call { it.removeImplication(name, tag) } }

    private fun change(block: suspend () -> TagDetail) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                val detail = block()
                _state.update { it.copy(detail = detail, busy = false) }
            } catch (e: CoreException) {
                _news.value = TagNews.Failed(e)
                _state.update { it.copy(busy = false) }
            }
        }
    }
}
