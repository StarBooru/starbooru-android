package eu.filipnone.starbooru.ui.upload

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.filipnone.starbooru.FakeApis
import eu.filipnone.starbooru.core.Rating
import eu.filipnone.starbooru.data.Account
import eu.filipnone.starbooru.data.MemorySecretStore
import eu.filipnone.starbooru.data.upload.Batch
import eu.filipnone.starbooru.data.upload.IncomingItem
import eu.filipnone.starbooru.data.upload.Plan
import eu.filipnone.starbooru.data.upload.UploadItem
import eu.filipnone.starbooru.data.upload.UploadQueue
import eu.filipnone.starbooru.data.upload.UploadSource
import eu.filipnone.starbooru.data.upload.UploadState
import eu.filipnone.starbooru.data.upload.UploadStatus
import eu.filipnone.starbooru.data.upload.UploadStore
import eu.filipnone.starbooru.preferencesIn
import eu.filipnone.starbooru.ui.frame.FrameInfo
import eu.filipnone.starbooru.ui.frame.LocalFrame
import eu.filipnone.starbooru.ui.frame.WidthClass
import eu.filipnone.starbooru.ui.theme.Seed
import eu.filipnone.starbooru.ui.theme.StarBooruTheme
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Upload page over a list in every state, at a phone's and a tablet's width. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class UploadScreenTest {
    @get:Rule val compose = createComposeRule()

    @get:Rule val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()

    // Not MainDispatcherRule: its paused Main would keep the page from hearing the list change.
    private val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())

    @After
    fun stop() = scope.cancel()

    private fun queue(): UploadQueue = UploadQueue(context, tmp.newFolder("empty"), scope, UnconfinedTestDispatcher(), { 0L }, { true }, {})

    /** A file the intake copied in: the first bytes of a PNG are enough here. */
    private fun picture(
        dir: File,
        id: String,
    ): IncomingItem.Copied {
        val file = File(dir, id).apply { writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) }
        return IncomingItem.Copied(id, "$id.png", file.length() * 100_000, "image/png", "Summer 2024/$id.png")
    }

    /** Drafts, one on its way, one waiting, one done, a duplicate, a failure and a link, as kept on disk. */
    private fun filled(): UploadQueue {
        val dir = tmp.newFolder("uploads")
        val names = listOf("sent", "waiting", "done", "known", "draft0", "draft1")
        val files = names.map { picture(dir, it) }
        val plan = Plan(listOf("holiday"), Rating.SAFE)
        fun local(f: IncomingItem.Copied) = UploadSource.Local(f.name, f.size, f.mime, f.path)
        val items =
            listOf(
                UploadItem("sent", local(files[0]), status = UploadStatus.Waiting, plan = plan),
                UploadItem("waiting", local(files[1]), status = UploadStatus.Waiting, plan = plan),
                UploadItem("done", local(files[2]), status = UploadStatus.Done(52), plan = plan),
                UploadItem("known", local(files[3]), status = UploadStatus.Duplicate(12), plan = plan),
                UploadItem(
                    "huge",
                    UploadSource.Local("huge.mp4", 900_000_000, "video/mp4"),
                    status = UploadStatus.Failed("Bigger than this server takes (512.0 MB at most)."),
                ),
                UploadItem("draft0", local(files[4]), tags = "summer_2024 beach sea"),
                UploadItem("draft1", local(files[5]), tags = "summer_2024"),
                UploadItem("link", UploadSource.Link("https://gelbooru.com/index.php?page=post&id=7")),
            )
        UploadStore(File(dir, "list.json")).save(UploadState(items, Batch(tags = "holiday")))
        val q = UploadQueue(context, dir, scope, UnconfinedTestDispatcher(), { 0L }, { true }, {})
        val going = q.claim(1, emptySet()).single()
        q.sent(going.id, going.attempt, going.size?.let { it * 6 / 10 } ?: 0, going.size ?: 0, 1_288_490)
        return q
    }

    private fun show(
        q: UploadQueue,
        dark: Boolean,
        widthDp: Int,
    ): UploadViewModel {
        val account = Account(preferencesIn(tmp.newFolder(), scope), MemorySecretStore(), FakeApis())
        val vm = UploadViewModel(q, account)
        val width = WidthClass.of(widthDp.toFloat())
        val bar = if (width == WidthClass.Compact) 84.dp else 12.dp
        compose.setContent {
            StarBooruTheme(dark = dark, oled = false, dynamic = false, seed = Seed()) {
                androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                    CompositionLocalProvider(
                        LocalFrame provides FrameInfo(width = width, padding = PaddingValues(top = 72.dp, bottom = bar)),
                    ) {
                        UploadPage(vm, onOpenPost = { opened += it })
                    }
                }
            }
        }
        return vm
    }

    private val opened = mutableListOf<Long>()

    @Test
    @Config(qualifiers = "w390dp-h844dp-notnight")
    fun thePhoneBarCountsTheDrafts() {
        show(filled(), dark = false, widthDp = 390)
        compose.onNodeWithText("Upload 3 files").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-night")
    fun theWideBarHasEverythingOnOneLine() {
        show(filled(), dark = true, widthDp = 1280)
        compose.onNodeWithText("Upload 3 files").assertIsDisplayed()
        compose.onNodeWithContentDescription("Source").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-notnight")
    fun emptyPageOffersThePhotoPicker() {
        show(queue(), dark = false, widthDp = 390)
        compose.onNodeWithText("Pick photos and videos to upload, or share them here from another app.").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-notnight")
    fun aDoneTileOpensItsPostAndADraftItsSheet() {
        val q = filled()
        show(q, dark = false, widthDp = 390)
        compose.onNodeWithContentDescription("Open post #52").performClick()
        assertEquals(listOf(52L), opened)
        compose.onNodeWithContentDescription("Open draft0.png").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Tags for draft0.png").assertIsDisplayed()
        compose.onNodeWithText("Same as the rest (safe)").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-notnight")
    fun aBadSharedTagKeepsTheBatchAndSaysWhy() {
        val q = filled()
        q.setBatch(Batch(tags = "-holiday"))
        show(q, dark = false, widthDp = 390)
        compose.onNodeWithText("Upload 3 files").performClick()
        compose.onNodeWithText("“-holiday” can't be a tag: a tag can't start with - or ~.").assertIsDisplayed()
        assertEquals(UploadStatus.Draft, q.state.value.get("draft0")?.status)
    }
}
