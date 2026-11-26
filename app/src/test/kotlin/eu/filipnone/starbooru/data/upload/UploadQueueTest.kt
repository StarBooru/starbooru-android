package eu.filipnone.starbooru.data.upload

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.filipnone.starbooru.core.Rating
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The upload list over a real folder: kept through a restart, its copies tidied, its words in English. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class UploadQueueTest {
    @get:Rule val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scopes = mutableListOf<CoroutineScope>()
    private var scheduled = 0

    private val dir: File get() = tmp.root

    private fun queue(
        limit: Long = 0,
        links: Boolean = true,
    ): UploadQueue {
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
        scopes += scope
        return UploadQueue(context, dir, scope, UnconfinedTestDispatcher(), { limit }, { links }, { scheduled++ })
    }

    @After
    fun stop() = scopes.forEach { it.cancel() }

    /** A file the intake copied in, as a generated PNG's first bytes. */
    private fun copied(
        id: String,
        path: String = "",
    ): IncomingItem.Copied {
        val bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        File(dir, id).writeBytes(bytes)
        return IncomingItem.Copied(id, "$id.png", bytes.size.toLong(), "image/png", path)
    }

    /** Waits for the list on disk to be [expected], as it's written in the background. */
    private fun savedAs(expected: UploadState) {
        val store = UploadStore(File(dir, "list.json"))
        repeat(200) {
            if (store.load() == expected.forDisk()) return
            Thread.sleep(10)
        }
        assertEquals(expected.forDisk(), store.load())
    }

    @Test
    fun whatWasAddedIsThereAfterARestart() {
        val first = queue()
        first.add(listOf(copied("a"), IncomingItem.Link("https://cdn.example/b.gif")))
        first.setBatch(Batch(tags = "sky", rating = Rating.SENSITIVE))
        first.setTags("a", "cat")
        assertEquals(null, first.start())
        assertEquals(1, scheduled)
        savedAs(first.state.value)
        val again = queue()
        assertEquals(first.state.value, again.state.value)
        assertEquals(listOf("sky", "cat"), again.state.value.get("a")?.plan?.tags)
        // Something waits: the worker is asked for again.
        assertEquals(2, scheduled)
    }

    @Test
    fun whatWasOnItsWayWhenTheAppDiedWaitsAgain() {
        val first = queue()
        first.add(listOf(copied("a")))
        first.start()
        assertEquals(1, first.claim(AT_ONCE, emptySet()).size)
        savedAs(first.state.value)
        assertEquals(UploadStatus.Waiting, queue().state.value.get("a")?.status)
    }

    @Test
    fun copiesNoLongerOnTheListGoAndThoseOnItStay() {
        val first = queue()
        first.add(listOf(copied("a")))
        savedAs(first.state.value)
        File(dir, "orphan").writeText("x")
        File(dir, "half.part").writeText("x")
        queue()
        assertTrue(File(dir, "a").exists())
        assertFalse(File(dir, "orphan").exists())
        assertFalse(File(dir, "half.part").exists())
    }

    @Test
    fun aBrokenListLeavesTheCopiesBe() {
        File(dir, "list.json").writeText("{ broken")
        File(dir, "a").writeText("x")
        val q = queue()
        assertTrue(q.state.value.items.isEmpty())
        assertTrue(File(dir, "a").exists())
    }

    @Test
    fun aFileOverTheLimitFailsAtOnceInTheWebsWords() {
        val q = queue(limit = 1024)
        q.add(listOf(IncomingItem.TooBig("big.png", 4096, "image/png")))
        val item = q.state.value.items.single()
        assertEquals(UploadStatus.Failed("Bigger than this server takes (1.0 KB at most)."), item.status)
        assertFalse(item.retryable)
    }

    @Test
    fun filesLeftOutAreNamedInANotice() {
        val q = queue()
        val notes = mutableListOf<UploadNote>()
        scopes.first().launch { q.notes.collect { notes += it } }
        q.add(listOf(IncomingItem.Skipped("a.txt"), copied("b")))
        assertEquals(listOf(UploadNote.Skipped(listOf("a.txt"))), notes)
        assertEquals(
            "Only images and videos are taken. Left out: a.txt, b.pdf, c.doc and 1 more.",
            Words.skipped(context.resources, listOf("a.txt", "b.pdf", "c.doc", "d.zip")),
        )
        assertEquals("Only images and videos are taken. Left out: a.txt.", Words.skipped(context.resources, listOf("a.txt")))
        assertEquals(1, q.state.value.items.size)
    }

    @Test
    fun aFoldersNameIsATagWhileTheSwitchIsOn() {
        val q = queue()
        q.add(listOf(copied("a", "Summer 2024/a.png")))
        assertEquals("summer_2024", q.state.value.get("a")?.tags)
        q.setFolderTags(false)
        q.add(listOf(copied("b", "Summer 2024/b.png")))
        assertEquals("", q.state.value.get("b")?.tags)
    }

    @Test
    fun linksAreTakenOnlyWhenTheServerImportsThem() {
        val off = queue(links = false)
        off.add(listOf(IncomingItem.Link("https://a.example/x.png")))
        assertTrue(off.state.value.items.isEmpty())
        val on = queue()
        assertTrue(on.addLinks("see https://a.example/x.png and https://b.example/y"))
        assertFalse(on.addLinks("no links here"))
        assertEquals(2, on.state.value.items.size)
    }

    @Test
    fun aBadTagKeepsTheBatchBack() {
        val q = queue()
        q.add(listOf(copied("a")))
        q.setBatch(Batch(tags = "-sky"))
        val bad = q.start()
        assertNotNull(bad)
        assertEquals("“-sky” can't be a tag: a tag can't start with - or ~.", bad?.let { Words.badTag(context.resources, it) })
        assertEquals(UploadStatus.Draft, q.state.value.get("a")?.status)
        assertEquals(0, scheduled)
    }

    @Test
    fun aCopyGoesWithItsItemOrOnceItsUp() {
        val q = queue()
        q.add(listOf(copied("a"), copied("b")))
        q.remove("a")
        assertFalse(File(dir, "a").exists())
        q.start()
        val sent = q.claim(AT_ONCE, emptySet()).single()
        assertTrue(q.finish(sent.id, sent.attempt, Outcome.Posted(9)))
        assertFalse(File(dir, "b").exists())
        assertEquals(UploadStatus.Done(9), q.state.value.get("b")?.status)
    }

    @Test
    fun aFinishedRunIsSummedUp() {
        val res = context.resources
        assertEquals("2 files uploaded · 1 already here", Words.summary(res, Tally(done = 2, duplicate = 1)))
        assertEquals("1 already here · 3 failed", Words.summary(res, Tally(duplicate = 1, failed = 3)))
        assertEquals("0 files uploaded", Words.summary(res, Tally()))
    }
}
