package eu.filipnone.starbooru.ui.frame

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.filipnone.starbooru.R
import eu.filipnone.starbooru.core.CoreException
import eu.filipnone.starbooru.core.SavedSearch
import eu.filipnone.starbooru.core.Tag
import eu.filipnone.starbooru.core.TypedWord
import eu.filipnone.starbooru.core.completeWord
import eu.filipnone.starbooru.core.searchWordAt
import eu.filipnone.starbooru.core.shortCount
import eu.filipnone.starbooru.core.suggestionCount
import eu.filipnone.starbooru.core.typingPauseMs
import eu.filipnone.starbooru.data.Account
import eu.filipnone.starbooru.ui.tags.categoryColor
import eu.filipnone.starbooru.ui.theme.StarBooruTheme
import kotlinx.coroutines.delay

/** One thing the list under the search box offers. */
sealed interface Hint {
    data class TagHint(val tag: Tag) : Hint

    data class Saved(val search: SavedSearch) : Hint
}

/**
 * The search box's suggestions: tags for the word at the caret, asked a
 * moment after the typing stops; while the box is empty, the saved searches.
 * Recent answers are kept, so taking a letter back asks nothing.
 */
@Stable
class SearchHints(private val account: Account) {
    var hints by mutableStateOf<List<Hint>>(emptyList())
        private set

    private var word: TypedWord? = null
    private val kept = LinkedHashMap<String, List<Tag>>(32, 0.75f, true)

    /** Called as the box changes; suspends through the typing pause. */
    suspend fun typed(
        value: TextFieldValue,
        saved: List<SavedSearch>?,
        focused: Boolean,
    ) {
        if (!focused) {
            hints = emptyList()
            return
        }
        if (value.text.isBlank()) {
            word = null
            hints = saved.orEmpty().take(suggestionCount().toInt()).map { Hint.Saved(it) }
            return
        }
        val at = searchWordAt(value.text, value.selection.end.toUInt())
        word = at
        if (at == null) {
            hints = emptyList()
            return
        }
        val typed = at.typed.lowercase()
        kept[typed]?.let {
            hints = it.map { t -> Hint.TagHint(t) }
            return
        }
        // While asking, what still fits stays.
        hints = hints.filter { it is Hint.TagHint && it.tag.name.startsWith(typed) }
        delay(typingPauseMs().toLong())
        val found =
            try {
                account.call { it.tags(typed, suggestionCount()) }
            } catch (_: CoreException) {
                return
            }
        kept[typed] = found
        while (kept.size > KEPT) kept.remove(kept.keys.first())
        if (word == at) hints = found.map { Hint.TagHint(it) }
    }

    /** The box with [tag] in place of the word being typed, the caret after it. */
    fun complete(
        value: TextFieldValue,
        tag: String,
    ): TextFieldValue? {
        // The word as the box holds it now: one kept from before more
        // typing could replace only part of it.
        val at = searchWordAt(value.text, value.selection.end.toUInt()) ?: return null
        val done = completeWord(value.text, at, tag) ?: return null
        hints = emptyList()
        return TextFieldValue(done.value, TextRange(done.caret.toInt()))
    }

    fun close() {
        hints = emptyList()
    }

    private companion object {
        const val KEPT = 48
    }
}

/** The list under the search capsule. */
@Composable
fun SuggestionList(
    hints: List<Hint>,
    onTag: (Tag) -> Unit,
    onSaved: (SavedSearch) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (hints.isEmpty()) return
    Column(
        modifier
            .widthIn(max = 680.dp)
            .fillMaxWidth()
            .glass(RoundedCornerShape(26.dp), StarBooruTheme.roles)
            .padding(6.dp),
    ) {
        hints.forEach { hint ->
            when (hint) {
                is Hint.TagHint ->
                    Row(
                        hint.tag.name,
                        categoryColor(hint.tag.category),
                        shortCount(hint.tag.postCount.toLong().coerceAtLeast(0).toULong()),
                        R.drawable.ic_label,
                    ) {
                        onTag(hint.tag)
                    }

                is Hint.Saved -> Row(hint.search.name, MaterialTheme.colorScheme.onSurface, null, R.drawable.ic_bookmark) {
                    onSaved(hint.search)
                }
            }
        }
    }
}

@Composable
private fun Row(
    name: String,
    color: androidx.compose.ui.graphics.Color,
    count: String?,
    icon: Int,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Text(
            name.replace('_', ' '),
            color = color,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        count?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
