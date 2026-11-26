package eu.filipnone.starbooru.ui.viewer

import eu.filipnone.starbooru.core.MediaKind
import eu.filipnone.starbooru.post
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** After the web's tests for `logic/reverse.rs`. */
class ReverseTest {
    private val image =
        post(1).copy(
            kind = MediaKind.IMAGE,
            fileSize = 1_626_018,
            width = 1600,
            height = 1200,
            fileUrl = "https://b.example.org/media/original/ab.png?exp=1&sig=2",
            thumbUrl = "https://b.example.org/media/thumb/ab.jpg?exp=1&sig=3",
            sampleUrl = "https://b.example.org/media/sample/ab.jpg?exp=1&sig=4",
        )

    @Test
    fun theThumbnailGoesAndAVideoNeedsOne() {
        assertEquals(image.thumbUrl, Reverse.picture(image))
        val bare = image.copy(thumbUrl = null)
        assertEquals(image.sampleUrl, Reverse.picture(bare))
        val bareFile = bare.copy(sampleUrl = null)
        assertEquals(image.fileUrl, Reverse.picture(bareFile))
        assertNull(Reverse.picture(bareFile.copy(fileSize = 9L * 1024 * 1024)))
        assertNull(Reverse.picture(bareFile.copy(width = 8000)))
        assertNull(Reverse.picture(bareFile.copy(fileUrl = "https://b.example.org/media/original/ab.webp?exp=1")))
        val video = image.copy(kind = MediaKind.VIDEO, sampleUrl = null)
        assertEquals(image.thumbUrl, Reverse.picture(video))
        assertNull(Reverse.picture(video.copy(thumbUrl = null)))
        assertFalse(Reverse.offered(video.copy(thumbUrl = null)))
    }

    @Test
    fun nothingFromTheRecycleBin() {
        assertTrue(Reverse.offered(image))
        assertFalse(Reverse.offered(image.copy(deleted = true)))
    }

    @Test
    fun fileTypesComeFromThePath() {
        assertEquals("image/jpeg" to "jpg", Reverse.fileType("/x/a.JPEG"))
        assertEquals("image/png" to "png", Reverse.fileType("/x/a.png#b"))
        assertEquals("image/gif" to "gif", Reverse.fileType("/x/a.gif"))
        assertNull(Reverse.fileType("/x/a.webp"))
        assertNull(Reverse.fileType("/x.jpg/a"))
        assertNull(Reverse.fileType(""))
    }

    @Test
    fun theCopyIsNamedAfterThePost() {
        assertEquals("starbooru-7.gif", Reverse.fileName(7, "/media/original/ab.gif"))
        assertEquals("starbooru-7.jpg", Reverse.fileName(7, ""))
    }

    @Test
    fun aSiteGetsThePicturesAddress() {
        val trace = ReverseSite.TraceMoe.address("https://b.example.org/media/thumb/ab.jpg?exp=1&sig=3")
        assertTrue(trace.startsWith("https://trace.moe/?url=https%3A%2F%2F"))
        assertFalse(trace.contains("&sig"))
    }

    @Test
    fun homeServersAreNotReachable() {
        listOf(
            "http://localhost:8080/media/x.jpg",
            "http://app.localhost/x",
            "http://127.0.0.1:8088/media/x.jpg",
            "http://10.0.0.5/x",
            "http://172.16.4.1/x",
            "http://192.168.1.20:8080/x",
            "http://169.254.0.1/x",
            "http://100.101.102.103/x",
            "http://0.0.0.0/x",
            "http://[::1]:8080/x",
            "http://[fd7a:115c:a1e0::1]/x",
            "http://[fe80::1]/x",
            "http://[::ffff:192.168.0.1]/x",
            "http://nas/x",
            "http://nas.local:8080/x",
            "http://booru.lan/x",
            "http://booru.home.arpa/x",
            "https://box.tail1234.ts.net/x",
            "http://user:pw@192.168.1.2/x",
            "http://booru.local./x",
            "ftp://example.org/x",
            "/media/thumb/x.jpg",
            "",
        ).forEach { assertFalse(it, Reverse.reachable(it)) }
    }

    @Test
    fun publicServersAreReachable() {
        listOf(
            "https://booru.example.org/media/x.jpg",
            "http://203.0.114.7:8080/x",
            "https://8.8.8.8/x",
            "https://[2a00:1450:4001::1]/x",
            "HTTPS://Booru.Example.Org/x",
            "https://user@booru.example.org/x",
            "https://local.example.org/x",
            "https://homes.org/x",
        ).forEach { assertTrue(it, Reverse.reachable(it)) }
    }
}
