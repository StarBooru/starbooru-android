package eu.filipnone.starbooru.ui.share

import androidx.compose.ui.geometry.Size
import eu.filipnone.starbooru.core.CoreException
import eu.filipnone.starbooru.core.MediaKind
import eu.filipnone.starbooru.core.SharedPost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** After the web's tests for the public page in `logic/share.rs`. */
class SharedPageTest {
    private fun post(
        kind: MediaKind = MediaKind.IMAGE,
        sample: String? = "s",
        width: Int? = 200,
        height: Int? = 100,
    ) = SharedPost(kind, "image/png", width, height, null, "f", "t", sample)

    @Test
    fun oneManyOrNone() {
        assertEquals(SharedLayout.Empty, SharedPage.layout(0))
        assertEquals(SharedLayout.One, SharedPage.layout(1))
        assertEquals(SharedLayout.Many, SharedPage.layout(2))
    }

    @Test
    fun aBlankTitleCountsThePostsInstead() {
        assertEquals("Trip", SharedPage.title("  Trip "))
        assertNull(SharedPage.title("   "))
        assertNull(SharedPage.title(null))
    }

    @Test
    fun theLightboxStopsAtTheEnds() {
        assertEquals(1, SharedPage.step(0, 3, forward = true))
        assertNull(SharedPage.step(2, 3, forward = true))
        assertNull(SharedPage.step(0, 3, forward = false))
        assertEquals(1, SharedPage.step(2, 3, forward = false))
        assertNull(SharedPage.step(0, 0, forward = true))
    }

    @Test
    fun theSmallerCopyUnlessZoomedOrAVideo() {
        assertEquals("s", SharedPage.shown(post(), original = false))
        assertEquals("f", SharedPage.shown(post(), original = true))
        assertEquals("f", SharedPage.shown(post(sample = null), original = false))
        assertEquals("f", SharedPage.shown(post(MediaKind.VIDEO), original = false))
        assertEquals("f", SharedPage.shown(post(MediaKind.ANIMATION), original = false))
    }

    @Test
    fun onlyTheServersOwn404IsAMissingLink() {
        assertTrue(SharedPage.missing(CoreException.Api(404u, "not_found", "gone")))
        assertFalse(SharedPage.missing(CoreException.Api(404u, "http_error", "proxy")))
        assertFalse(SharedPage.missing(CoreException.Api(500u, "internal", "boom")))
        assertFalse(SharedPage.missing(CoreException.Unreachable("down")))
    }

    @Test
    fun aPictureFitsItsStage() {
        assertEquals(Size(400f, 200f), SharedPage.fitted(post(), 400f, 400f))
        assertEquals(Size(400f, 400f), SharedPage.fitted(post(width = null), 400f, 400f))
        assertEquals(Size(400f, 400f), SharedPage.fitted(post(height = 0), 400f, 400f))
    }
}
