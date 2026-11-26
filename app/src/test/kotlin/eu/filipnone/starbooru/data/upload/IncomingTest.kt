package eu.filipnone.starbooru.data.upload

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** What another app's share sheet hands over, turned into items with their files copied in. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class IncomingTest {
    @get:Rule val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** The start of a generated PNG, enough for the app to know it. */
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D)

    private fun offered(
        name: String,
        bytes: ByteArray,
    ): Uri {
        val uri = Uri.parse("content://eu.filipnone.test/shared/$name")
        shadowOf(context.contentResolver).registerInputStreamSupplier(uri) { ByteArrayInputStream(bytes) }
        return uri
    }

    private fun read(
        intent: Intent,
        limit: Long = 0,
        links: Boolean = true,
    ) = Incoming.fromIntent(context, intent, tmp.root, limit, links) { _, _ -> }

    @Test
    fun aSharedPictureIsCopiedIn() {
        val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, offered("photo.png", png))
        val item = read(send).single() as IncomingItem.Copied
        assertEquals("photo.png", item.name)
        assertEquals(png.size.toLong(), item.size)
        assertArrayEquals(png, File(tmp.root, item.id).readBytes())
        assertTrue(tmp.root.listFiles().orEmpty().none { it.name.endsWith(Incoming.PART) })
    }

    @Test
    fun severalComeAndWhatIsntMediaIsLeftOut() {
        val uris = arrayListOf(offered("a.png", png), offered("notes.txt", "hello".toByteArray()), offered("1000", png))
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        val items = read(send)
        assertEquals(3, items.size)
        assertTrue(items[0] is IncomingItem.Copied)
        assertEquals(IncomingItem.Skipped("notes.txt"), items[1])
        // No name to go by: its first bytes say it's a picture.
        assertEquals("1000", (items[2] as IncomingItem.Copied).name)
        assertEquals(2, tmp.root.listFiles().orEmpty().size)
    }

    @Test
    fun aLinkSharedFromABrowserIsTakenWhenTheServerImports() {
        val send = Intent(
            Intent.ACTION_SEND,
        ).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "A post https://gelbooru.com/index.php?id=1")
        assertEquals(listOf(IncomingItem.Link("https://gelbooru.com/index.php?id=1")), read(send))
        assertTrue(read(send, links = false).isEmpty())
    }

    @Test
    fun aFileOverTheLimitIsntCopied() {
        val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, offered("big.png", ByteArray(4096) + png))
        val item = read(send, limit = 1024).single()
        assertTrue(item is IncomingItem.TooBig)
        assertTrue(tmp.root.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun theAppsOwnFilesCantBeSharedBack() {
        val private = File(context.filesDir, "secret.png").apply { writeBytes(png) }
        val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, Uri.fromFile(private))
        assertEquals(listOf(IncomingItem.Skipped("secret.png")), read(send))
    }

    @Test
    fun aStreamThatCantBeReadSaysSo() {
        val uri = Uri.parse("content://eu.filipnone.test/shared/gone.png")
        val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
        val item = read(send).single()
        assertTrue(item.toString(), item is IncomingItem.Unreadable)
        assertTrue(tmp.root.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun onlySharesAreTaken() {
        assertTrue(Incoming.isShare(Intent(Intent.ACTION_SEND)))
        assertTrue(Incoming.isShare(Intent(Intent.ACTION_SEND_MULTIPLE)))
        assertFalse(Incoming.isShare(Intent(Intent.ACTION_VIEW)))
        assertFalse(Incoming.isShare(null))
        assertTrue(read(Intent(Intent.ACTION_VIEW)).isEmpty())
    }
}
