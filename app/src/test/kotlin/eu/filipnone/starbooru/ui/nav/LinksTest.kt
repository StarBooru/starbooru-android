package eu.filipnone.starbooru.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** After the web's tests for `route.rs`. Calls `normalizeServer` through the host library. */
class LinksTest {
    private val server = "https://booru.example.org"

    private fun page(url: String) = (Links.open(url, server) as? Opened.Page)?.route

    @Test
    fun aPostOpensWithItsSearchAndShuffle() {
        assertEquals(PostRoute(12), page("https://booru.example.org/post/12"))
        assertEquals(PostRoute(12, "cat dog"), page("https://booru.example.org/post/12?q=cat+dog"))
        assertEquals(PostRoute(5, "order:random", -42), page("https://booru.example.org/post/5?q=order:random&seed=-42"))
        assertNull(page("https://booru.example.org/post/x"))
        assertNull(page("https://booru.example.org/post/1/2"))
    }

    @Test
    fun theGalleryTagsAndPagesOpen() {
        assertEquals(GalleryRoute(""), page("https://booru.example.org/"))
        assertEquals(GalleryRoute("a&b"), page("https://booru.example.org/?q=a%26b"))
        assertEquals(TagRoute("blue_sky"), page("https://booru.example.org/tag?name=blue_sky"))
        assertEquals(TagsRoute, page("https://booru.example.org/tags/"))
        assertEquals(ExploreRoute, page("https://booru.example.org/explore?q=fractal"))
        assertEquals(SettingsRoute, page("https://booru.example.org/settings/viewing"))
        assertNull(page("https://booru.example.org/boorus"))
    }

    @Test
    fun anotherServersPagesDont() {
        assertNull(page("https://other.example.org/post/12"))
        assertNull(page("https://booru.example.org.evil.com/post/12"))
        assertNull(page("ftp://booru.example.org/post/12"))
        assertNull(page("not a link"))
    }

    @Test
    fun aServerUnderAPathKeepsIt() {
        val sub = "https://x.example.org/booru"
        assertEquals(Opened.Page(PostRoute(3)), Links.open("https://x.example.org/booru/post/3", sub))
        assertNull(Links.open("https://x.example.org/post/3", sub))
    }

    @Test
    fun aShareLinkOpensOnAnyServer() {
        val token = "0123456789abcdef0123456789abcdef"
        assertEquals(Opened.SharePage("https://other.example.org", token), Links.open("https://other.example.org/s/$token", server))
        assertEquals(Opened.SharePage("https://x.example.org/booru", token), Links.open("https://x.example.org/booru/s/$token/", server))
        assertEquals(Opened.SharePage("http://10.0.2.2:8080", token), Links.open("http://10.0.2.2:8080/s/$token?utm=x", server))
        // Not a token the server makes: nothing to open.
        assertNull(Links.open("https://other.example.org/s/abc123", server))
    }
}
