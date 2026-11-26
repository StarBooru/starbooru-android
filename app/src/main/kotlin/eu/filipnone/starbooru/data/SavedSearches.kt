package eu.filipnone.starbooru.data

import eu.filipnone.starbooru.core.CoreException
import eu.filipnone.starbooru.core.SavedSearch
import eu.filipnone.starbooru.core.searchKey
import eu.filipnone.starbooru.core.tidySearch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** The account's saved searches, kept on the server; null until they're known. */
class SavedSearches(private val account: Account, scope: CoroutineScope) {
    private val _list = MutableStateFlow<List<SavedSearch>?>(null)
    val list: StateFlow<List<SavedSearch>?> = _list.asStateFlow()

    init {
        scope.launch {
            account.api.collectLatest { api ->
                _list.value = null
                if (api != null && account.state.value is AccountState.SignedIn) refresh()
            }
        }
    }

    suspend fun refresh() {
        try {
            _list.value = account.call { it.savedSearches() }
        } catch (_: CoreException) {
        }
    }

    /** The saved search for [query], written however. */
    fun find(query: String): SavedSearch? {
        val key = searchKey(query)
        return _list.value?.firstOrNull { searchKey(it.query) == key }
    }

    fun keys(): Set<String> = _list.value.orEmpty().mapTo(HashSet()) { searchKey(it.query) }

    /** Saves [query] under its own words, as the web does. */
    suspend fun save(query: String): SavedSearch {
        val tidy = tidySearch(query)
        val saved = account.call { it.saveSearch(tidy, tidy) }
        _list.value = listOf(saved) + _list.value.orEmpty().filter { it.id != saved.id }
        return saved
    }

    suspend fun delete(search: SavedSearch) {
        account.call { it.deleteSearch(search.id) }
        _list.value = _list.value.orEmpty().filter { it.id != search.id }
    }
}
