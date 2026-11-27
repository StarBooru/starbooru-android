package eu.filipnone.starbooru.ui.nav

import eu.filipnone.starbooru.core.normalizeServer
import eu.filipnone.starbooru.core.shareLink
import java.net.URI
import java.net.URLDecoder

/** What a StarBooru address shared to the app opens. */
sealed interface Opened {
    /** A page of the server the app is signed in to. */
    data class Page(val route: Route) : Opened

    /** A share link's page, which needs no account. */
    data class SharePage(val server: String, val token: String) : Opened
}

/**
 * The web app's addresses as the app's places, after starbooru-web's
 * `route.rs`: `/post/12?q=…&seed=…`, `/?q=…`, `/tag?name=…`, `/tags`,
 * `/explore?q=…`, `/upload`, `/settings`, and `/s/<token>` on any server.
 * The reference app's links work the same.
 */
object Links {
    /** What [url] opens, when it's a StarBooru address of [server] (or any share link). */
    fun open(
        url: String,
        server: String,
    ): Opened? {
        // A share link: on whichever server made it, the instance's own path in front.
        shareLink(url)?.let { return Opened.SharePage(it.server, it.token) }
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        val path = uri.rawPath.orEmpty()
        val base = "$scheme://${uri.rawAuthority}"
        // Anything else only from the server in use, under its own path.
        val ours = normalizeServer(server).trimEnd('/')
        val here = (base + path).trimEnd('/')
        if (!here.equals(ours, ignoreCase = true) && !here.startsWith("$ours/", ignoreCase = true)) return null
        val rest =
            here
                .substring(ours.length)
                .split('/')
                .filter { it.isNotEmpty() }
        val query = params(uri.rawQuery)
        val q = query["q"].orEmpty().trim()
        val route: Route =
            when {
                rest.isEmpty() -> GalleryRoute(q)

                rest.size == 2 && rest[0] == "post" -> {
                    val id = rest[1].toLongOrNull()?.takeIf { it > 0 } ?: return null
                    PostRoute(id, q, query["seed"]?.toLongOrNull())
                }

                rest == listOf("tag") -> TagRoute(query["name"]?.takeIf { it.isNotBlank() } ?: return null)

                rest == listOf("tags") -> TagsRoute

                rest == listOf("explore") -> ExploreRoute

                rest == listOf("upload") -> UploadRoute

                rest.firstOrNull() == "settings" -> SettingsRoute

                rest == listOf("trash") -> TrashRoute

                rest == listOf("privacy") -> LegalRoute(privacy = true)

                rest == listOf("terms") -> LegalRoute(privacy = false)

                else -> return null
            }
        return Opened.Page(route)
    }

    private fun params(raw: String?): Map<String, String> =
        raw
            .orEmpty()
            .split('&')
            .filter { it.isNotEmpty() }
            .associate { pair ->
                val key = pair.substringBefore('=')
                val value = pair.substringAfter('=', "")
                decode(key) to decode(value)
            }

    private fun decode(s: String): String = runCatching { URLDecoder.decode(s, Charsets.UTF_8.name()) }.getOrDefault(s)
}
