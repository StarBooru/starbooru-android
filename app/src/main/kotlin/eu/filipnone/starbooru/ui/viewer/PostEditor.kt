package eu.filipnone.starbooru.ui.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.filipnone.starbooru.R
import eu.filipnone.starbooru.core.BadTag
import eu.filipnone.starbooru.core.CoreException
import eu.filipnone.starbooru.core.Post
import eu.filipnone.starbooru.core.PostTag
import eu.filipnone.starbooru.core.PostUpdate
import eu.filipnone.starbooru.core.Rating
import eu.filipnone.starbooru.core.Role as UserRole
import eu.filipnone.starbooru.core.Tag
import eu.filipnone.starbooru.core.TagCategory
import eu.filipnone.starbooru.core.TagCorrection
import eu.filipnone.starbooru.core.TagEdit
import eu.filipnone.starbooru.core.TagGroup
import eu.filipnone.starbooru.core.TagOrder
import eu.filipnone.starbooru.core.TagProblem
import eu.filipnone.starbooru.core.TypedWord
import eu.filipnone.starbooru.core.User
import eu.filipnone.starbooru.core.baseTag
import eu.filipnone.starbooru.core.checkTags
import eu.filipnone.starbooru.core.completeWord
import eu.filipnone.starbooru.core.groupTags
import eu.filipnone.starbooru.core.hasBaseTag
import eu.filipnone.starbooru.core.maxTagChars
import eu.filipnone.starbooru.core.maxTagsPerPost
import eu.filipnone.starbooru.core.mayEditPost
import eu.filipnone.starbooru.core.newWords
import eu.filipnone.starbooru.core.normalizedTag
import eu.filipnone.starbooru.core.rebaseTags
import eu.filipnone.starbooru.core.replaceTag
import eu.filipnone.starbooru.core.shortCount
import eu.filipnone.starbooru.core.shownFixes
import eu.filipnone.starbooru.core.suggestionCount
import eu.filipnone.starbooru.core.tagBoxWordAt
import eu.filipnone.starbooru.core.tagEdit
import eu.filipnone.starbooru.core.tagGroupsHeaded
import eu.filipnone.starbooru.core.tagOffers
import eu.filipnone.starbooru.core.tagsChanged
import eu.filipnone.starbooru.core.typedCategory
import eu.filipnone.starbooru.core.typingPauseMs
import eu.filipnone.starbooru.core.withCategory
import eu.filipnone.starbooru.core.withTag
import eu.filipnone.starbooru.core.withoutTag
import eu.filipnone.starbooru.data.Account
import eu.filipnone.starbooru.data.AccountState
import eu.filipnone.starbooru.ui.common.LocalNotices
import eu.filipnone.starbooru.ui.common.LocalReducedMotion
import eu.filipnone.starbooru.ui.common.describe
import eu.filipnone.starbooru.ui.gallery.ratingLabel
import eu.filipnone.starbooru.ui.tags.categoryColor
import eu.filipnone.starbooru.ui.tags.categoryName
import eu.filipnone.starbooru.ui.theme.StarBooruTheme
import eu.filipnone.starbooru.ui.theme.StarEasing
import eu.filipnone.starbooru.ui.theme.StarSpringEasing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val CATEGORIES =
    listOf(TagCategory.GENERAL, TagCategory.ARTIST, TagCategory.CHARACTER, TagCategory.COPYRIGHT, TagCategory.META)

private val RATINGS = listOf(Rating.SAFE, Rating.SENSITIVE, Rating.QUESTIONABLE, Rating.EXPLICIT)

private val CHIP = RoundedCornerShape(8.dp)

private val FIELD = RoundedCornerShape(12.dp)

private val SPACE = Regex("\\s+")

// ---------- the rules ----------

/** The post as editing began. The edit is measured against it, and Undo goes back to it. */
data class EditBase(val id: Long, val tags: List<String>, val rating: Rating, val source: String) {
    companion object {
        fun of(post: Post) = EditBase(post.id, post.tags, post.rating, post.source.orEmpty())
    }
}

/** The text after adding, the categories waiting for new tags, or the word that can't be a tag. */
data class Added(val text: String, val picks: Map<String, TagCategory>, val bad: BadTag? = null)

/**
 * Adds each word of [typed] to [text] once. A new tag the collection lacks
 * waits for [picked] in the picks; a prefix typed wins over it.
 */
fun addTyped(
    text: String,
    typed: String,
    picked: TagCategory,
    known: Set<String>,
    picks: Map<String, TagCategory>,
): Added {
    val check = checkTags(typed)
    check.bad?.let { return Added(text, picks, it) }
    var out = text
    val chosen = picks.toMutableMap()
    for (tag in check.tags) {
        if (hasBaseTag(out, tag)) continue
        out = withTag(out, tag)
        val name = baseTag(tag)
        if (name == tag && picked != TagCategory.GENERAL && name !in known) chosen[name] = picked else chosen.remove(name)
    }
    checkTags(out).bad?.let { return Added(text, picks, it) }
    return Added(out, chosen)
}

/** [text] with the waiting categories written in as prefixes, for saving. A tag now in [known] keeps its own. */
fun withPicks(
    text: String,
    picks: Map<String, TagCategory>,
    known: Set<String>,
): String =
    text.split(SPACE).filter { it.isNotEmpty() }.joinToString(" ") { word ->
        val name = baseTag(word)
        val pick = picks[name]
        if (pick == null || normalizedTag(word) != name) word else withCategory(name, pick, name in known)
    }

/** A chip's category: its typed prefix, then what the server said, then the one waiting for it. */
fun categoryOf(
    word: String,
    known: Map<String, TagCategory>,
    picks: Map<String, TagCategory>,
): TagCategory {
    typedCategory(word)?.let { return it }
    val name = baseTag(word)
    return known[name] ?: picks[name] ?: TagCategory.GENERAL
}

/** The first word that can't be a tag, counting the one still in the box that adds. */
fun problemOf(
    text: String,
    adding: String,
): BadTag? = checkTags(if (adding.isBlank()) text else "$text $adding").bad

/** What Save sends: only what differs from [base]. Tags go whole, as the API replaces the list. */
fun changes(
    base: EditBase,
    tags: String,
    rating: Rating,
    source: String,
): PostUpdate {
    val check = checkTags(tags)
    val cleanSource = source.trim()
    return PostUpdate(
        tags = check.tags.takeIf { check.bad == null && tagsChanged(tags, base.tags) },
        rating = rating.takeIf { it != base.rating },
        source = cleanSource.takeIf { it != base.source.trim() },
    )
}

fun PostUpdate.isNothing(): Boolean = tags == null && rating == null && source == null

/** The update that puts back what saving [base] as [saved] changed. */
fun undoOf(
    base: EditBase,
    saved: Post,
): PostUpdate =
    PostUpdate(
        tags = base.tags.takeIf { it.toSet() != saved.tags.toSet() },
        rating = base.rating.takeIf { it != saved.rating },
        source = base.source.takeIf { it != saved.source.orEmpty() },
    )

/** The chips under the viewer's headings: the post's own sorted by name, new ones after them as typed. */
fun editorGroups(
    details: List<PostTag>,
    saved: List<String>,
    edit: TagEdit,
    category: (String) -> TagCategory,
): List<TagGroup> {
    val own = saved.map { name -> details.firstOrNull { it.name == name } ?: PostTag(name, category(name), null) }
    val added = (edit.added + edit.bad).map { PostTag(it, category(it), null) }
    val order = added.map { it.name }
    return groupTags(own + added).map { group ->
        val (fresh, kept) = group.tags.partition { it.name in order }
        group.copy(tags = kept + fresh.sortedBy { order.indexOf(it.name) })
    }
}

/** The words new to the post, which "did you mean" asks about. */
fun wordsToCheck(
    check: String,
    saved: List<String>,
): List<String> = newWords(check, saved).split(SPACE).filter { it.isNotEmpty() }

/** Whether the account in [state] may edit [post], by the core's rule. */
fun mayEdit(
    state: AccountState,
    post: Post,
): Boolean {
    val user =
        (state as? AccountState.SignedIn)?.user?.let {
            User(it.id, it.username, if (it.admin) UserRole.ADMIN else UserRole.USER, 0L)
        }
    return mayEditPost(user, post.deleted)
}

// ---------- the draft ----------

/**
 * The edit in progress. It outlives the sheet, so swiping the sheet away
 * keeps it for next time, and [Saver] carries it through a rotation or the
 * system closing the app.
 */
@Stable
class PostEditorState {
    var base by mutableStateOf<EditBase?>(null)
        private set
    var text by mutableStateOf("")
        private set
    var rating by mutableStateOf(Rating.SAFE)
    var source by mutableStateOf("")
    var adding by mutableStateOf(TextFieldValue())
        private set
    var picked by mutableStateOf(TagCategory.GENERAL)
    var picks by mutableStateOf<Map<String, TagCategory>>(emptyMap())
        private set
    var asText by mutableStateOf(false)
        private set
    var check by mutableStateOf("")
        private set
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    val known = mutableStateMapOf<String, TagCategory>()
    var fresh by mutableStateOf<Set<String>>(emptySet())
        private set
    private val asked = HashSet<String>()

    /** Kept, added and removed, against the post as it was. */
    val edit: TagEdit by derivedStateOf { tagEdit(text, base?.tags.orEmpty()) }

    /** [edit] with the word still in the box, which Save adds too. */
    val pending: TagEdit by derivedStateOf {
        if (adding.text.isBlank()) edit else tagEdit("$text ${adding.text}", base?.tags.orEmpty())
    }

    /** Whether Save has anything to send. */
    val changed: Boolean
        get() {
            val b = base ?: return false
            return tagsChanged(text, b.tags) || rating != b.rating || source.trim() != b.source.trim() || adding.text.isNotBlank()
        }

    val problem: BadTag? get() = problemOf(text, adding.text)

    /**
     * Starts on [post], or carries on with it: a draft of the same post moves
     * onto the tags, rating and source it has now, keeping what changed here.
     */
    fun begin(post: Post) {
        post.tagDetails.forEach { known[it.name] = it.category }
        error = null
        val b = base
        if (b == null || b.id != post.id) {
            reset(EditBase.of(post))
            return
        }
        val now = EditBase.of(post)
        if (b.tags != now.tags) {
            text = rebaseTags(text, b.tags, now.tags)
            check = text
        }
        if (rating == b.rating) rating = now.rating
        if (source.trim() == b.source.trim()) source = now.source
        base = now
    }

    fun clear() {
        base = null
        text = ""
        rating = Rating.SAFE
        source = ""
        adding = TextFieldValue()
        picked = TagCategory.GENERAL
        picks = emptyMap()
        asText = false
        check = ""
        busy = false
        error = null
        fresh = emptySet()
        asked.clear()
    }

    private fun reset(to: EditBase) {
        clear()
        base = to
        text = to.tags.joinToString(" ")
        rating = to.rating
        source = to.source
        check = text
    }

    /** The box that adds: a space after a word adds it, and a typed prefix picks the category. */
    fun type(value: TextFieldValue) {
        adding = value
        typedCategory(value.text)?.let { picked = it }
        if (value.text.isNotBlank() && value.text.last().isWhitespace()) commit()
    }

    /** Adds what's in the box. False when a word can't be a tag: it stays, and [problem] says why. */
    fun commit(): Boolean {
        val typed = adding.text
        if (typed.isBlank()) {
            if (typed.isNotEmpty()) adding = TextFieldValue()
            return true
        }
        val done = addTyped(text, typed, picked, known.keys, picks)
        if (done.bad != null) return false
        text = done.text
        picks = done.picks
        check = done.text
        adding = TextFieldValue()
        return true
    }

    /** Takes [word] off. Its chip stays, struck through, to put back. */
    fun remove(word: String) {
        text = withoutTag(text, word)
    }

    fun putBack(name: String) {
        text = withTag(text, name)
    }

    fun replace(fix: TagCorrection) {
        text = replaceTag(text, fix.from, fix.to)
        picks = picks - baseTag(fix.from)
        check = text
    }

    /** The whole list as text. Typos are looked up after each word, not each letter. */
    fun typeText(value: String) {
        text = value
        if (value.isNotEmpty() && value.last().isWhitespace()) check = value
    }

    fun checkNow() {
        check = text
    }

    fun toggleText() {
        commit()
        asText = !asText
    }

    /** Keeps the categories of tags the server named. */
    fun learn(tags: List<Tag>) {
        tags.forEach { known[it.name] = it.category }
    }

    /** New tags not looked up yet, each asked about once. */
    fun toAsk(): List<String> {
        val out = mutableListOf<String>()
        for (name in edit.added.map { baseTag(it) }.distinct()) {
            if (name !in known && name !in fresh && asked.add(name)) out += name
        }
        return out
    }

    /** Asks whether [name] exists, for its colour or the "new" mark. After a failure it can be asked again. */
    suspend fun lookUp(
        account: Account,
        name: String,
    ) {
        var settled = false
        try {
            val found = account.call { it.tagList(name, TagOrder.NAME, 1u, LOOKUP) }.tags.firstOrNull { it.name == name }
            if (found != null) known[name] = found.category else fresh = fresh + name
            settled = true
        } catch (_: CoreException) {
        } finally {
            if (!settled) asked.remove(name)
        }
    }

    /** What Save sends, once it's settled which waiting categories still apply. Null when nothing changed. */
    suspend fun update(account: Account): PostUpdate? {
        val b = base ?: return null
        for (name in picks.keys.toList()) {
            if (name !in known && name !in fresh && hasBaseTag(text, name)) lookUp(account, name)
        }
        return changes(b, withPicks(text, picks, known.keys), rating, source).takeUnless { it.isNothing() }
    }

    internal fun saved(): List<String> {
        val b = base ?: return emptyList()
        return listOf(
            b.id.toString(),
            b.tags.joinToString(" "),
            b.rating.name,
            b.source,
            text,
            rating.name,
            source,
            adding.text,
            adding.selection.start.toString(),
            adding.selection.end.toString(),
            picked.name,
            picks.entries.joinToString(" ") { "${it.value.name}:${it.key}" },
            asText.toString(),
        )
    }

    /** The draft from [saved]. Anything unreadable gives an empty one. */
    internal fun restore(list: List<String>) {
        clear()
        val id = list.getOrNull(0)?.toLongOrNull() ?: return
        fun ratingAt(at: Int) = Rating.entries.firstOrNull { it.name == list.getOrNull(at) }
        fun category(name: String?) = TagCategory.entries.firstOrNull { it.name == name }
        val tags = list.getOrNull(1).orEmpty().split(' ').filter { it.isNotEmpty() }
        base = EditBase(id, tags, ratingAt(2) ?: return, list.getOrNull(3).orEmpty())
        text = list.getOrNull(4).orEmpty()
        rating = ratingAt(5) ?: Rating.SAFE
        source = list.getOrNull(6).orEmpty()
        val typed = list.getOrNull(7).orEmpty()
        val start = list.getOrNull(8)?.toIntOrNull()?.coerceIn(0, typed.length) ?: typed.length
        val end = list.getOrNull(9)?.toIntOrNull()?.coerceIn(0, typed.length) ?: typed.length
        adding = TextFieldValue(typed, TextRange(start, end))
        picked = category(list.getOrNull(10)) ?: TagCategory.GENERAL
        picks =
            list
                .getOrNull(11)
                .orEmpty()
                .split(' ')
                .mapNotNull { pair ->
                    val c = category(pair.substringBefore(':', "")) ?: return@mapNotNull null
                    pair.substringAfter(':', "").takeIf { it.isNotEmpty() }?.let { it to c }
                }.toMap()
        asText = list.getOrNull(12) == "true"
        check = text
    }

    companion object {
        private const val LOOKUP = 5u

        val Saver: Saver<PostEditorState, Any> =
            listSaver(
                save = { it.saved() },
                restore = { list -> PostEditorState().apply { restore(list) } },
            )
    }
}

// ---------- suggestions ----------

/** Tags for the word at the caret, asked for after a typing pause and kept by prefix. */
@Stable
class TagHints(private val account: Account, private val onFound: (List<Tag>) -> Unit = {}) {
    var tags by mutableStateOf<List<Tag>>(emptyList())
        private set

    private var word: TypedWord? = null
    private val kept = LinkedHashMap<String, List<Tag>>(32, 0.75f, true)

    suspend fun typed(
        value: TextFieldValue,
        focused: Boolean,
    ) {
        if (!focused) {
            word = null
            tags = emptyList()
            return
        }
        val at = tagBoxWordAt(value.text, value.selection.end.toUInt())
        word = at
        if (at == null) {
            tags = emptyList()
            return
        }
        val typed = at.typed.lowercase()
        kept[typed]?.let {
            tags = it
            return
        }
        tags = tags.filter { it.name.startsWith(typed) }
        delay(typingPauseMs().toLong())
        val found =
            try {
                account.call { it.tags(typed, suggestionCount()) }
            } catch (_: CoreException) {
                return
            }
        kept[typed] = found
        while (kept.size > KEPT) kept.remove(kept.keys.first())
        onFound(found)
        if (word == at) tags = found
    }

    /** [value] with the word at the caret replaced by [tag]. */
    fun complete(
        value: TextFieldValue,
        tag: String,
    ): TextFieldValue? {
        val at = tagBoxWordAt(value.text, value.selection.end.toUInt()) ?: return null
        val done = completeWord(value.text, at, tag) ?: return null
        word = null
        tags = emptyList()
        return TextFieldValue(done.value, TextRange(done.caret.toInt()))
    }

    private companion object {
        const val KEPT = 48
    }
}

// ---------- the sheet ----------

/** Why a word can't be a tag. */
@Composable
fun badTagText(bad: BadTag): String =
    when (bad.problem) {
        TagProblem.BAD_PREFIX -> stringResource(R.string.tag_error_prefix, bad.word)
        TagProblem.TOO_LONG -> stringResource(R.string.tag_error_long, bad.word, maxTagChars().toString())
        TagProblem.TOO_MANY -> stringResource(R.string.tag_error_many, maxTagsPerPost().toString())
        TagProblem.OTHER -> stringResource(R.string.tag_error_other, bad.word)
    }

/**
 * What follows a save: the viewer takes the post, and a notice offers Undo,
 * which sends back the old tags, rating and source.
 */
@Composable
fun rememberEditUndo(
    account: Account,
    onChanged: (Post) -> Unit,
): (Post, PostUpdate) -> Unit {
    val notices = LocalNotices.current
    val res = LocalResources.current
    val scope = rememberCoroutineScope()
    val changed by rememberUpdatedState(onChanged)
    return remember(account, notices, res) {
        { saved, undo ->
            changed(saved)
            val text = res.getString(R.string.post_edit_saved)
            if (undo.isNothing()) {
                notices?.say(text)
            } else {
                notices?.offer(text, res.getString(R.string.press_undo)) {
                    scope.launch {
                        try {
                            changed(account.call { it.updatePost(saved.id, undo) })
                        } catch (e: CoreException) {
                            val reason = describe(e, res, account.api.value?.server().orEmpty())
                            notices.say(res.getString(R.string.post_edit_undo_failed, reason))
                        }
                    }
                }
            }
        }
    }
}

/**
 * The sheet that edits a post's tags, rating and source. [onSaved] gets the
 * saved post and the update that undoes it; [onClose] says whether the draft
 * goes too. A failed save leaves the sheet open with the edit as it was.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostEditor(
    post: Post,
    state: PostEditorState,
    account: Account,
    onSaved: (Post, PostUpdate) -> Unit,
    onClose: (discard: Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val res = LocalResources.current
    val busy by rememberUpdatedState(state.busy)
    val sheet =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            // The sheet stays while saving.
            confirmValueChange = { it != SheetValue.Hidden || !busy },
        )
    val hints = remember(account, state) { TagHints(account) { state.learn(it) } }
    LaunchedEffect(post) { state.begin(post) }
    val edit = state.edit
    LaunchedEffect(edit.added) {
        for (name in state.toAsk()) launch { state.lookUp(account, name) }
    }
    var fixes by remember { mutableStateOf<List<TagCorrection>>(emptyList()) }
    LaunchedEffect(state.check) {
        val words = wordsToCheck(state.check, state.base?.tags.orEmpty())
        fixes =
            if (words.isEmpty()) {
                emptyList()
            } else {
                try {
                    account.call { it.tagCorrections(words) }
                } catch (_: CoreException) {
                    emptyList()
                }
            }
    }

    fun close(discard: Boolean) {
        scope.launch { sheet.hide() }.invokeOnCompletion { onClose(discard) }
    }

    fun save() {
        val base = state.base ?: return
        if (state.busy || !state.commit()) return
        state.busy = true
        state.error = null
        scope.launch {
            val update = state.update(account)
            if (update == null) {
                state.busy = false
                close(discard = true)
                return@launch
            }
            try {
                val saved = account.call { it.updatePost(base.id, update) }
                state.busy = false
                onSaved(saved, undoOf(base, saved))
                close(discard = true)
            } catch (e: CoreException) {
                state.error = describe(e, res, account.api.value?.server().orEmpty())
                state.busy = false
            }
        }
    }

    val problem = state.problem?.let { badTagText(it) }
    val roles = StarBooruTheme.roles
    ModalBottomSheet(onDismissRequest = { onClose(false) }, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.post_edit_tags),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconToggleButton(checked = state.asText, onCheckedChange = { state.toggleText() }) {
                    Icon(
                        painterResource(R.drawable.ic_article),
                        stringResource(R.string.post_tags_as_text),
                        tint = if (state.asText) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (state.asText) {
                    WholeText(state, hints, problem)
                } else {
                    EditChips(post, state, edit)
                    Adder(state, hints, problem)
                }
                DidYouMean(shownFixes(fixes, state.text), state::replace)
                RatingRow(state.rating) { state.rating = it }
                SourceField(state.source) { state.source = it }
            }
            state.error?.let {
                Text(
                    it,
                    color = roles.onDangerContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier =
                        Modifier
                            .padding(horizontal = 24.dp, vertical = 4.dp)
                            .fillMaxWidth()
                            .clip(FIELD)
                            .background(roles.dangerContainer)
                            .padding(12.dp),
                )
            }
            Footer(
                state = state,
                edit = state.pending,
                canSave = state.changed && problem == null && !state.busy,
                onCancel = { close(discard = true) },
                onSave = ::save,
            )
        }
    }
}

/** The chips: kept, struck through to put back, new with a coloured edge, or red for a word that can't be a tag. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditChips(
    post: Post,
    state: PostEditorState,
    edit: TagEdit,
) {
    val known = state.known
    val picks = state.picks
    val groups = editorGroups(post.tagDetails, state.base?.tags.orEmpty(), edit) { categoryOf(it, known, picks) }
    if (groups.isEmpty()) {
        Text(stringResource(R.string.post_no_tags), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val headed = tagGroupsHeaded(groups)
    val removed = edit.removed.toSet()
    val added = edit.added.toSet()
    val bad = edit.bad.toSet()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        groups.forEach { group ->
            if (headed) {
                Text(
                    groupTitle(group).uppercase(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                group.tags.forEach { tag ->
                    key(tag.name) {
                        val kind =
                            when (tag.name) {
                                in removed -> ChipKind.Removed
                                in bad -> ChipKind.Bad
                                in added -> ChipKind.Added
                                else -> ChipKind.Kept
                            }
                        val fresh = kind == ChipKind.Added && baseTag(tag.name) in state.fresh
                        EditChip(tag.name, tag.category, kind, fresh) {
                            if (kind == ChipKind.Removed) state.putBack(tag.name) else state.remove(tag.name)
                        }
                    }
                }
            }
        }
    }
}

private enum class ChipKind { Kept, Removed, Added, Bad }

@Composable
private fun EditChip(
    word: String,
    category: TagCategory,
    kind: ChipKind,
    fresh: Boolean,
    onClick: () -> Unit,
) {
    val roles = StarBooruTheme.roles
    val scheme = MaterialTheme.colorScheme
    val reduced = LocalReducedMotion.current
    val spec = tween<Color>(if (reduced) 0 else 220, easing = StarEasing)
    val removed = kind == ChipKind.Removed
    val name = (if (kind == ChipKind.Bad) word else baseTag(word)).replace('_', ' ')
    val ink by animateColorAsState(
        when (kind) {
            ChipKind.Removed -> scheme.onSurfaceVariant
            ChipKind.Bad -> roles.danger
            else -> categoryColor(category)
        },
        spec,
        label = "chip ink",
    )
    val edge by animateColorAsState(
        when (kind) {
            ChipKind.Added -> scheme.primary
            ChipKind.Bad -> roles.danger
            else -> roles.outline
        },
        spec,
        label = "chip edge",
    )
    val fill by animateColorAsState(if (removed) roles.surface2.copy(alpha = 0f) else roles.surface2, spec, label = "chip fill")
    val label = if (removed) stringResource(R.string.press_undo) else stringResource(R.string.tags_remove_named, name)
    val said = if (removed) stringResource(R.string.post_tag_removed, name) else null
    // A new chip pops in; the others are already there.
    val shown = remember { MutableTransitionState(kind != ChipKind.Added || reduced).apply { targetState = true } }
    AnimatedVisibility(
        shown,
        enter = fadeIn(tween(180)) + scaleIn(tween(260, easing = StarSpringEasing), initialScale = 0.6f),
    ) {
        Row(
            Modifier
                .heightIn(min = 36.dp)
                .clip(CHIP)
                .background(fill)
                .border(if (kind == ChipKind.Added) 1.5.dp else 1.dp, edge, CHIP)
                .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
                .then(if (said != null) Modifier.semantics { contentDescription = said } else Modifier)
                .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                name,
                color = ink,
                fontSize = 14.sp,
                fontWeight = if (category == TagCategory.GENERAL || removed) null else FontWeight.Medium,
                textDecoration = if (removed) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (fresh) {
                Text(stringResource(R.string.ai_new_tag), style = MaterialTheme.typography.labelSmall, color = scheme.primary)
            }
            Icon(
                painterResource(if (removed) R.drawable.ic_undo else R.drawable.ic_close),
                null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** The box that adds, with the category new tags get and the suggestions under it. */
@Composable
private fun Adder(
    state: PostEditorState,
    hints: TagHints,
    problem: String?,
) {
    val focus = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val offers = tagOffers("${state.text} ${state.adding.text}", hints.tags)
    val bring = remember { BringIntoViewRequester() }
    LaunchedEffect(state.adding, focused) { hints.typed(state.adding, focused) }
    // Keeps the suggestions above the keyboard.
    LaunchedEffect(offers, state.text) { if (focused) bring.bringIntoView() }
    val enter = {
        if (state.adding.text.isBlank()) focus.clearFocus() else state.commit()
    }
    Column(Modifier.bringIntoViewRequester(bring), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.adding,
            onValueChange = state::type,
            label = { Text(stringResource(R.string.post_add_as, categoryName(state.picked))) },
            leadingIcon = { CategoryPick(state.picked) { state.picked = it } },
            trailingIcon = {
                IconButton(onClick = { state.commit() }, enabled = state.adding.text.isNotBlank()) {
                    Icon(painterResource(R.drawable.ic_add), stringResource(R.string.add))
                }
            },
            isError = problem != null,
            supportingText = problem?.let { { Text(it) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onDone = { enter() }),
            shape = FIELD,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused }
                    // A hardware keyboard's Enter adds, as the keyboard's Done does.
                    .onPreviewKeyEvent {
                        val isEnter = it.key == Key.Enter || it.key == Key.NumPadEnter
                        if (isEnter && it.type == KeyEventType.KeyDown) enter()
                        isEnter
                    },
        )
        Offers(offers) { tag ->
            state.learn(listOf(tag))
            hints.complete(state.adding, tag.name)?.let(state::type)
        }
    }
}

/** The whole list as one text, as the web's editor has it. */
@Composable
private fun WholeText(
    state: PostEditorState,
    hints: TagHints,
    problem: String?,
) {
    var value by remember { mutableStateOf(TextFieldValue(state.text, TextRange(state.text.length))) }
    if (value.text != state.text) value = TextFieldValue(state.text, TextRange(state.text.length))
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value, focused) { hints.typed(value, focused) }
    OutlinedTextField(
        value = value,
        onValueChange = {
            value = it
            state.typeText(it.text)
        },
        label = { Text(stringResource(R.string.post_tags)) },
        isError = problem != null,
        supportingText = { Text(problem ?: stringResource(R.string.post_tags_hint)) },
        minLines = 3,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
        shape = FIELD,
        modifier =
            Modifier.fillMaxWidth().onFocusChanged {
                if (focused && !it.isFocused) state.checkNow()
                focused = it.isFocused
            },
    )
    Offers(tagOffers(state.text, hints.tags)) { tag ->
        state.learn(listOf(tag))
        hints.complete(value, tag.name)?.let {
            value = it
            state.typeText(it.text)
        }
    }
}

/** The category new tags get, as a dot in its colour. */
@Composable
private fun CategoryPick(
    picked: TagCategory,
    onPick: (TagCategory) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.post_add_category)
    val said = "$label: ${categoryName(picked)}"
    Box {
        Row(
            Modifier
                .padding(start = 6.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = label) { open = true }
                .semantics { contentDescription = said }
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Dot(categoryColor(picked))
            Icon(
                painterResource(R.drawable.ic_chevron_down),
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            CATEGORIES.forEach { category ->
                DropdownMenuItem(
                    text = { Text(categoryName(category), color = categoryColor(category), fontWeight = FontWeight.Medium) },
                    leadingIcon = { Dot(categoryColor(category)) },
                    trailingIcon =
                        if (category == picked) {
                            { Icon(painterResource(R.drawable.ic_check), null, Modifier.size(20.dp)) }
                        } else {
                            null
                        },
                    onClick = {
                        onPick(category)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun Dot(colour: Color) {
    Box(Modifier.size(16.dp).clip(CircleShape).background(colour))
}

/** Suggested tags in their category's colour, with how many posts have each. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Offers(
    tags: List<Tag>,
    onTake: (Tag) -> Unit,
) {
    if (tags.isEmpty()) return
    val roles = StarBooruTheme.roles
    val add = stringResource(R.string.add)
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tags.forEach { tag ->
            key(tag.id) {
                Row(
                    Modifier
                        .heightIn(min = 36.dp)
                        .clip(CHIP)
                        .background(roles.surface3)
                        .clickable(role = Role.Button, onClickLabel = add) { onTake(tag) }
                        .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_add),
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        tag.name.replace('_', ' '),
                        color = categoryColor(tag.category),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        shortCount(tag.postCount.toLong().coerceAtLeast(0).toULong()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Known tags close to the new words. A tap swaps one in. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DidYouMean(
    fixes: List<TagCorrection>,
    onTake: (TagCorrection) -> Unit,
) {
    if (fixes.isEmpty()) return
    val roles = StarBooruTheme.roles
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.post_did_you_mean),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            fixes.forEach { fix ->
                val label = stringResource(R.string.post_replace_tag, fix.from, fix.to)
                Row(
                    Modifier
                        .heightIn(min = 36.dp)
                        .clip(CHIP)
                        .background(roles.surface3)
                        .clickable(role = Role.Button, onClickLabel = label) { onTake(fix) }
                        .semantics { contentDescription = label }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        fix.from.replace('_', ' '),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textDecoration = TextDecoration.LineThrough,
                        fontSize = 14.sp,
                    )
                    Icon(
                        painterResource(R.drawable.ic_chevron_right),
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        fix.to.replace('_', ' '),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun RatingRow(
    rating: Rating,
    onRating: (Rating) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.rating), style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            RATINGS.forEachIndexed { i, r ->
                SegmentedButton(
                    selected = rating == r,
                    onClick = { onRating(r) },
                    shape = SegmentedButtonDefaults.itemShape(i, RATINGS.size),
                    icon = {},
                ) {
                    Text(
                        ratingLabel(r).replaceFirstChar { it.titlecase(locale) },
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 13.sp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceField(
    source: String,
    onSource: (String) -> Unit,
) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = source,
        onValueChange = onSource,
        label = { Text(stringResource(R.string.post_source)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        shape = FIELD,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** What will change, then Cancel and Save. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Footer(
    state: PostEditorState,
    edit: TagEdit,
    canSave: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val roles = StarBooruTheme.roles
    val base = state.base
    val added = edit.added.size + edit.bad.size
    val removed = edit.removed.size
    val rated = state.rating.takeIf { base != null && it != base.rating }
    val sourced = base != null && state.source.trim() != base.source.trim()
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FlowRow(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (added == 0 && removed == 0 && rated == null && !sourced) {
                Text(
                    stringResource(R.string.post_tags_no_changes),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (added > 0) Pill("+$added", roles.ok, pluralStringResource(R.plurals.post_tags_added, added, added))
            if (removed > 0) Pill("\u2212$removed", roles.danger, pluralStringResource(R.plurals.post_tags_removed, removed, removed))
            rated?.let { Pill(ratingLabel(it), MaterialTheme.colorScheme.primary, null) }
            if (sourced) Pill(stringResource(R.string.post_source), MaterialTheme.colorScheme.primary, null)
        }
        TextButton(onClick = onCancel, enabled = !state.busy) { Text(stringResource(R.string.cancel)) }
        Button(onClick = onSave, enabled = canSave) {
            if (state.busy) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Icon(painterResource(R.drawable.ic_check), null, Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.save))
        }
    }
}

@Composable
private fun Pill(
    text: String,
    colour: Color,
    said: String?,
) {
    Text(
        text,
        color = colour,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        maxLines = 1,
        modifier =
            Modifier
                .clip(CircleShape)
                .border(1.dp, colour.copy(alpha = 0.5f), CircleShape)
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .then(if (said != null) Modifier.semantics { contentDescription = said } else Modifier),
    )
}
