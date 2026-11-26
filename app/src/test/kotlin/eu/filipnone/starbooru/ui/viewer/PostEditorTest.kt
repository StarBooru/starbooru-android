package eu.filipnone.starbooru.ui.viewer

import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import eu.filipnone.starbooru.core.PostTag
import eu.filipnone.starbooru.core.Rating
import eu.filipnone.starbooru.core.TagCategory
import eu.filipnone.starbooru.core.TagGroupKind
import eu.filipnone.starbooru.core.TagProblem
import eu.filipnone.starbooru.core.tagEdit
import eu.filipnone.starbooru.data.AccountState
import eu.filipnone.starbooru.data.Identity
import eu.filipnone.starbooru.post
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PostEditorTest {
    private val base = EditBase(7, listOf("cat", "blue_sky"), Rating.SAFE, "")

    private fun detail(
        name: String,
        category: TagCategory = TagCategory.GENERAL,
        section: String? = null,
    ) = PostTag(name, category, section)

    @Test
    fun typedWordsJoinTheTextOnceEach() {
        val added = addTyped("cat", "Dog cat  Blue_Sky dog", TagCategory.GENERAL, emptySet(), emptyMap())
        assertEquals("cat dog blue_sky", added.text)
        assertNull(added.bad)
        assertTrue(added.picks.isEmpty())
        // There already, with its prefix or without: nothing changes.
        val again = addTyped("artist:lavfi", "lavfi", TagCategory.META, emptySet(), emptyMap())
        assertEquals("artist:lavfi", again.text)
        assertTrue(again.picks.isEmpty())
    }

    @Test
    fun thePickedCategoryWaitsOnNewBareTagsOnly() {
        val artist = TagCategory.ARTIST
        assertEquals(mapOf("lavfi" to artist), addTyped("cat", "lavfi", artist, setOf("cat"), emptyMap()).picks)
        // A tag the collection has keeps its own category.
        assertTrue(addTyped("", "fractal", artist, setOf("fractal"), emptyMap()).picks.isEmpty())
        // A prefix typed wins, and so does picking General again.
        val typed = addTyped("", "meta:x", artist, emptySet(), mapOf("x" to artist))
        assertEquals("meta:x", typed.text)
        assertTrue(typed.picks.isEmpty())
        assertTrue(addTyped("", "lavfi", TagCategory.GENERAL, emptySet(), mapOf("lavfi" to artist)).picks.isEmpty())
    }

    @Test
    fun wordsThatCantBeTagsLeaveTheTextAlone() {
        val added = addTyped("cat", "dog -bad", TagCategory.GENERAL, emptySet(), emptyMap())
        assertEquals("cat", added.text)
        assertEquals("-bad", added.bad?.word)
        assertEquals(TagProblem.BAD_PREFIX, added.bad?.problem)
        val full = (1..512).joinToString(" ") { "t$it" }
        val many = addTyped(full, "one_more", TagCategory.GENERAL, emptySet(), emptyMap())
        assertEquals(full, many.text)
        assertEquals(TagProblem.TOO_MANY, many.bad?.problem)
    }

    @Test
    fun picksBecomePrefixesOnSaving() {
        val picks = mapOf("lavfi" to TagCategory.ARTIST, "x" to TagCategory.CHARACTER)
        assertEquals("cat artist:lavfi meta:x", withPicks("  cat \n lavfi meta:x ", picks, setOf("cat")))
        // Found in the collection meanwhile: it stays as it is.
        assertEquals("lavfi", withPicks("lavfi", picks, setOf("lavfi")))
        assertEquals("", withPicks("", picks, emptySet()))
    }

    @Test
    fun aChipTakesItsPrefixsCategoryThenWhatIsKnown() {
        val known = mapOf("lavfi" to TagCategory.COPYRIGHT)
        val picks = mapOf("mika" to TagCategory.META)
        assertEquals(TagCategory.ARTIST, categoryOf("artist:lavfi", known, picks))
        assertEquals(TagCategory.GENERAL, categoryOf("gen:lavfi", known, picks))
        assertEquals(TagCategory.COPYRIGHT, categoryOf("lavfi", known, picks))
        assertEquals(TagCategory.META, categoryOf("mika", known, picks))
        assertEquals(TagCategory.GENERAL, categoryOf("sky", known, picks))
    }

    @Test
    fun theProblemIsTheFirstWordThatCantBeATag() {
        assertNull(problemOf("cat sky", ""))
        assertNull(problemOf("cat", "  "))
        assertEquals("~sky", problemOf("cat", "~sky")?.word)
        assertEquals("-x", problemOf("cat -x", "~sky")?.word)
        assertEquals(TagProblem.TOO_LONG, problemOf("cat", "y".repeat(129))?.problem)
    }

    @Test
    fun onlyWhatChangedIsSent() {
        assertTrue(changes(base, "cat blue_sky", Rating.SAFE, "").isNothing())
        assertTrue(changes(base, " Blue_Sky\ncat ", Rating.SAFE, "  ").isNothing())
        val more = changes(base, "cat blue_sky dog", Rating.SAFE, "")
        assertEquals(listOf("cat", "blue_sky", "dog"), more.tags)
        assertNull(more.rating)
        assertNull(more.source)
        assertEquals(Rating.EXPLICIT, changes(base, "cat blue_sky", Rating.EXPLICIT, "").rating)
        assertEquals("https://x.example", changes(base, "cat blue_sky", Rating.SAFE, " https://x.example ").source)
        // An empty source clears one the post had.
        assertEquals("", changes(base.copy(source = "pixiv"), "cat blue_sky", Rating.SAFE, " ").source)
        // A word that can't be a tag sends no tags.
        assertNull(changes(base, "cat -dog", Rating.SAFE, "").tags)
    }

    @Test
    fun undoPutsBackWhatTheSaveChanged() {
        val saved = post(7, tags = listOf("dog", "cat")).copy(rating = Rating.EXPLICIT, source = "pixiv")
        val undo = undoOf(base, saved)
        assertEquals(listOf("cat", "blue_sky"), undo.tags)
        assertEquals(Rating.SAFE, undo.rating)
        assertEquals("", undo.source)
        // The same tags in another order are no change to undo.
        val same = undoOf(base, post(7, tags = listOf("blue_sky", "cat")))
        assertTrue(same.isNothing())
    }

    @Test
    fun addedChipsJoinTheirGroupAfterThePostsOwn() {
        val details = listOf(detail("cat"), detail("lavfi", TagCategory.ARTIST), detail("red_hair", section = "hair"))
        val saved = listOf("cat", "lavfi", "red_hair")
        val edit = tagEdit("cat red_hair zebra artist:mika apple -bad", saved)
        assertEquals(listOf("lavfi"), edit.removed)
        val groups = editorGroups(details, saved, edit) { categoryOf(it, emptyMap(), emptyMap()) }
        assertEquals(listOf(TagGroupKind.ARTIST, TagGroupKind.SECTION, TagGroupKind.OTHER), groups.map { it.kind })
        assertEquals(listOf("lavfi", "artist:mika"), groups[0].tags.map { it.name })
        assertEquals(listOf("red_hair"), groups[1].tags.map { it.name })
        // The post's own sorted, then the new ones in the order typed.
        assertEquals(listOf("cat", "zebra", "apple", "-bad"), groups[2].tags.map { it.name })
        // An older server sends no details: the post's tags stand alone.
        val plain = editorGroups(emptyList(), listOf("b", "a"), tagEdit("b a", listOf("b", "a"))) { TagCategory.GENERAL }
        assertEquals(listOf(listOf("a", "b")), plain.map { g -> g.tags.map { it.name } })
    }

    @Test
    fun onlyNewWordsAreCheckedForTypos() {
        assertEquals(listOf("blnde", "dgo"), wordsToCheck("cat blnde  Sky dgo ", listOf("cat", "sky")))
        assertTrue(wordsToCheck("cat sky", listOf("cat", "sky")).isEmpty())
    }

    @Test
    fun anyoneSignedInEditsALivePost() {
        val live = post(1)
        val member = AccountState.SignedIn("http://s", Identity(2, "ada", admin = false))
        assertTrue(mayEdit(member, live))
        assertFalse(mayEdit(member, live.copy(deleted = true)))
        assertFalse(mayEdit(AccountState.Guest("http://s"), live))
        assertFalse(mayEdit(AccountState.SignedOut("http://s"), live))
    }

    @Test
    fun theDraftAddsOnASpaceAndFollowsATypedPrefix() {
        val state = PostEditorState()
        state.begin(post(7, tags = listOf("cat", "blue_sky")))
        assertEquals("cat blue_sky", state.text)
        assertFalse(state.changed)
        state.type(TextFieldValue("artist:mi"))
        assertEquals(TagCategory.ARTIST, state.picked)
        assertTrue(state.changed)
        state.type(TextFieldValue("dog "))
        assertEquals("cat blue_sky dog", state.text)
        assertEquals("", state.adding.text)
        assertEquals(listOf("dog"), state.edit.added)
        // Artist was picked and dog isn't known yet: it waits for a prefix.
        assertEquals(mapOf("dog" to TagCategory.ARTIST), state.picks)
        state.remove("cat")
        assertEquals(listOf("cat"), state.edit.removed)
        state.putBack("cat")
        state.remove("dog")
        assertFalse(state.changed)
        // A word that can't be a tag stays in the box, and Save says why.
        state.type(TextFieldValue("-dog "))
        assertEquals("-dog ", state.adding.text)
        assertEquals(TagProblem.BAD_PREFIX, state.problem?.problem)
        assertFalse(state.commit())
    }

    @Test
    fun theDraftFollowsTagsChangedElsewhere() {
        val state = PostEditorState()
        state.begin(post(7, tags = listOf("cat", "boots")))
        state.type(TextFieldValue("hat "))
        state.rating = Rating.EXPLICIT
        state.begin(post(7, tags = listOf("cat", "1girl")).copy(source = "pixiv"))
        assertEquals("cat hat 1girl", state.text)
        assertEquals(Rating.EXPLICIT, state.rating)
        assertEquals("pixiv", state.source)
        // Another post starts afresh.
        state.begin(post(8, tags = listOf("sky")))
        assertEquals("sky", state.text)
        assertEquals(Rating.SAFE, state.rating)
        assertFalse(state.changed)
        state.clear()
        assertNull(state.base)
        assertFalse(state.changed)
    }

    @Test
    fun theDraftSurvivesBeingSaved() {
        val state = PostEditorState()
        state.begin(post(7, tags = listOf("cat")).copy(source = "pixiv"))
        state.picked = TagCategory.CHARACTER
        state.type(TextFieldValue("mika "))
        state.type(TextFieldValue("ha", TextRange(1, 2)))
        state.rating = Rating.QUESTIONABLE
        state.source = "https://x.example"
        state.toggleText()
        val saved = with(PostEditorState.Saver) { SaverScope { true }.save(state) }
        val back = saved?.let { PostEditorState.Saver.restore(it) }
        assertEquals(state.base, back?.base)
        assertEquals("cat mika ha", back?.text)
        assertEquals(Rating.QUESTIONABLE, back?.rating)
        assertEquals("https://x.example", back?.source)
        assertEquals(mapOf("mika" to TagCategory.CHARACTER, "ha" to TagCategory.CHARACTER), back?.picks)
        assertEquals(true, back?.asText)
        // Nothing begun saves nothing.
        assertNull(with(PostEditorState.Saver) { SaverScope { true }.save(PostEditorState()) })
        // Something garbled restores an empty draft rather than failing.
        val garbled = PostEditorState().apply { restore(listOf("x", "cat")) }
        assertNull(garbled.base)
    }
}
