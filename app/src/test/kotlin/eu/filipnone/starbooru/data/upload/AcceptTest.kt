package eu.filipnone.starbooru.data.upload

import eu.filipnone.starbooru.data.upload.Accept.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the Upload page takes, as the web's `logic/upload.rs` tests it. */
class AcceptTest {
    @Test
    fun aNameOrATypeCanSayYesAndTheRestAreLookedInto() {
        assertEquals(Verdict.Take, Accept.verdict("a.JPG", "", 10))
        assertEquals(Verdict.Take, Accept.verdict("clip.final.webm", "", 10))
        // The phone's type for a file with no extension.
        assertEquals(Verdict.Take, Accept.verdict("anime", "image/webp", 10))
        assertEquals(Verdict.Look, Accept.verdict("1000", "", 10))
        assertEquals(Verdict.Look, Accept.verdict("notes.txt", "text/plain", 10))
        assertEquals(Verdict.Look, Accept.verdict("saved.jpg_large", "", 10))
        // Hidden files and empty ones are never media; an unknown size isn't empty.
        assertEquals(Verdict.Skip, Accept.verdict("._a.jpg", "image/jpeg", 10))
        assertEquals(Verdict.Skip, Accept.verdict(".nomedia", "", 10))
        assertEquals(Verdict.Skip, Accept.verdict("a.png", "image/png", 0))
        assertEquals(Verdict.Take, Accept.verdict("a.png", "image/png", -1))
        assertFalse(Accept.isMediaName(".png"))
        assertFalse(Accept.isMediaName("README"))
    }

    private fun bytes(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }

    private fun ascii(
        s: String,
        at: Int = 0,
    ): ByteArray = ByteArray(at) + s.toByteArray(Charsets.US_ASCII)

    @Test
    fun aFilesStartSaysWhatItIs() {
        assertTrue(Accept.isMediaHead(bytes(0xFF, 0xD8, 0xFF, 0xE0)))
        assertTrue(Accept.isMediaHead(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0)))
        assertTrue(Accept.isMediaHead(ascii("GIF89a")))
        assertTrue(Accept.isMediaHead(ascii("RIFF") + bytes(1, 2, 3, 4) + ascii("WEBPVP8 ")))
        assertTrue(Accept.isMediaHead(bytes(0x1A, 0x45, 0xDF, 0xA3, 0x01)))
        assertTrue(Accept.isMediaHead(bytes(0, 0, 0, 0x20) + ascii("ftypisom")))
        assertTrue(Accept.isMediaHead(bytes(0, 0, 0, 0x1C) + ascii("ftypavif")))
        // HEIC is an ftyp box too, and the server doesn't keep it.
        assertFalse(Accept.isMediaHead(bytes(0, 0, 0, 0x18) + ascii("ftypheic")))
        assertFalse(Accept.isMediaHead(ascii("RIFF") + bytes(1, 2, 3, 4) + ascii("WAVE")))
        assertFalse(Accept.isMediaHead(ascii("hello, world")))
        assertFalse(Accept.isMediaHead(ByteArray(0)))
    }

    @Test
    fun videosAreToldByTypeOrName() {
        assertTrue(Accept.isVideo("a.MOV", ""))
        assertTrue(Accept.isVideo("clip", "video/webm"))
        assertFalse(Accept.isVideo("a.gif", "image/gif"))
    }

    @Test
    fun aNoticeNamesAFewSkippedFilesAndCountsTheRest() {
        val names = listOf("a.txt", "b.pdf", "c.doc", "d.zip", "e")
        assertEquals("a.txt, b.pdf, c.doc" to 2, Accept.skippedNames(names))
        assertEquals("a.txt" to 0, Accept.skippedNames(names.take(1)))
        assertEquals("" to 0, Accept.skippedNames(emptyList()))
    }

    @Test
    fun tagsFromFolderNames() {
        assertEquals("summer_2024", Accept.folderTag("Summer 2024/a.jpg"))
        assertEquals("cats", Accept.folderTag("/Pics/Cats/a.jpg"))
        assertEquals("cats", Accept.folderTag("/Cats/a.jpg"))
        assertNull(Accept.folderTag("a.jpg"))
        assertNull(Accept.folderTag("/a.jpg"))
        assertNull(Accept.folderTag(""))
        assertNull(Accept.folderTag("-x/a.jpg"))
    }

    @Test
    fun findsUrlsInText() {
        assertEquals(
            listOf("https://a.example/1.png", "HTTP://b.example/2"),
            Accept.parseUrls(" https://a.example/1.png\nftp://x  HTTP://b.example/2 https://a.example/1.png word https://"),
        )
        assertTrue(Accept.parseUrls("").isEmpty())
        // A browser's share: the title, then the page.
        assertEquals(listOf("https://gelbooru.com/index.php?id=1"), Accept.parseUrls("A post\nhttps://gelbooru.com/index.php?id=1"))
    }

    @Test
    fun onlyAPicturesAddressStandsAsItsOwnPreview() {
        assertTrue(Accept.isPictureUrl("https://cdn.example/a/b.JPG?x=1"))
        assertTrue(Accept.isPictureUrl("https://cdn.example/a.webp#top"))
        for (other in listOf(
            "https://gelbooru.com/index.php?page=post&s=view&id=7",
            "https://cdn.example/a.mp4",
            "https://cdn.example/folder.png/",
            "",
        )) {
            assertFalse(other, Accept.isPictureUrl(other))
        }
        assertEquals("gelbooru.com", Accept.hostOf("https://www.gelbooru.com/index.php?page=post"))
        assertEquals("127.0.0.1:8192", Accept.hostOf("http://127.0.0.1:8192/a.png"))
        assertEquals("not an address", Accept.hostOf("not an address"))
    }

    @Test
    fun aFormatIsNamedByItsContentOrItsName() {
        assertEquals("PNG", Accept.formatOf("image/png"))
        assertEquals("MKV", Accept.formatOf("video/x-matroska"))
        assertNull(Accept.formatOf("application/pdf"))
        assertEquals("JPG", Accept.formatByName("Holiday.JPEG"))
        assertEquals("WEBP", Accept.formatByName("https://cdn.example/a/b.webp?x=1#y"))
        assertEquals("TIFF", Accept.formatByName("scan.tif"))
        for (none in listOf("anime", ".png", "notes.txt", "https://example.org/post/12", "https://example.org/v1.2/page")) {
            assertNull(none, Accept.formatByName(none))
        }
    }

    @Test
    fun theLimitIsTheServersAndNoneIsNoLimit() {
        assertTrue(Accept.tooBig(11, 10))
        assertFalse(Accept.tooBig(10, 10))
        assertFalse(Accept.tooBig(Long.MAX_VALUE, 0))
    }
}
