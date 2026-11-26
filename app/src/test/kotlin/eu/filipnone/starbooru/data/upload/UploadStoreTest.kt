package eu.filipnone.starbooru.data.upload

import eu.filipnone.starbooru.core.Rating
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UploadStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun file() = File(tmp.root, "list.json")

    @Test
    fun everyStateComesBackAsItWent() {
        val local = UploadSource.Local("a.png", 10, "image/png", "Cats/a.png")
        val plan = Plan(listOf("cat"), Rating.QUESTIONABLE, "https://src.example")
        val state =
            UploadState(
                items =
                    listOf(
                        UploadItem("1", local, tags = "own", rating = Rating.EXPLICIT),
                        UploadItem("2", local, status = UploadStatus.Waiting, plan = plan, attempt = 2, tries = 1),
                        UploadItem("3", local, status = UploadStatus.Sending(total = 10), plan = plan),
                        UploadItem("4", local, status = UploadStatus.Done(5, Thumb("https://x/t.webp", "ab")), plan = plan),
                        UploadItem(
                            "5",
                            UploadSource.Link("https://cdn.example/a.gif"),
                            status = UploadStatus.Duplicate(6, true),
                            plan = plan,
                        ),
                        UploadItem("6", local, status = UploadStatus.Failed("Bigger than this server takes")),
                        UploadItem("7", local, status = UploadStatus.Stopped, plan = plan),
                    ),
                batch = Batch("sky", Rating.SENSITIVE, "src"),
                folderTags = false,
            )
        val store = UploadStore(file())
        store.save(state)
        assertEquals(state, store.load())
    }

    @Test
    fun nothingKeptIsAnEmptyList() {
        assertEquals(UploadState(), UploadStore(file()).load())
    }

    @Test
    fun aBrokenFileIsToldApartFromNone() {
        file().writeText("{ not json")
        assertNull(UploadStore(file()).load())
    }

    @Test
    fun anUnknownRatingReadsAsSafe() {
        file().writeText("""{"items":[{"id":"1","source":{"kind":"link","url":"https://a.example"},"rating":"GALACTIC"}]}""")
        assertEquals(Rating.SAFE, UploadStore(file()).load()?.items?.single()?.rating)
    }
}
