package eu.filipnone.starbooru.ui.viewer

import androidx.annotation.StringRes
import eu.filipnone.starbooru.R
import eu.filipnone.starbooru.core.MediaKind
import eu.filipnone.starbooru.core.Post
import java.net.URLEncoder

/**
 * Looking a post's picture up on other sites, after starbooru-web's
 * `logic/reverse.rs`. A site that fetches the picture by its address needs
 * the server on the internet; otherwise the copy is saved, for the site's
 * own upload button.
 */
enum class ReverseSite(
    @param:StringRes val label: Int,
    /** Its page opened with the picture's address. */
    private val byAddress: String,
    /** Its page to upload on, when it can't fetch the picture. */
    val page: String,
) {
    Lens(R.string.reverse_lens, "https://lens.google.com/uploadbyurl?url=", "https://lens.google.com/"),
    Iqdb(R.string.reverse_iqdb, "https://iqdb.org/?url=", "https://iqdb.org/"),
    SauceNao(R.string.reverse_saucenao, "https://saucenao.com/search.php?url=", "https://saucenao.com/"),
    TraceMoe(R.string.reverse_trace, "https://trace.moe/?url=", "https://trace.moe/"),
    ;

    fun address(picture: String): String = byAddress + URLEncoder.encode(picture, Charsets.UTF_8.name())
}

object Reverse {
    /** The largest file sent: iqdb's limit, the lowest of the four. */
    const val MAX_BYTES = 8L * 1024 * 1024

    /** iqdb's longest side. */
    private const val MAX_SIDE = 7500

    /**
     * The copy to send, an absolute address: the thumbnail (a frame for a
     * video, the first one for a GIF), else a picture's lighter copy, else
     * its file when every site takes it. Null when there's nothing to send.
     */
    fun picture(post: Post): String? {
        post.thumbUrl?.takeIf { fileType(it) != null }?.let { return it }
        if (post.kind == MediaKind.VIDEO) return null
        post.sampleUrl?.takeIf { fileType(it) != null }?.let { return it }
        val small = post.fileSize in 0..MAX_BYTES
        val fits = maxOf(post.width ?: 0, post.height ?: 0) <= MAX_SIDE
        return post.fileUrl.takeIf { small && fits && fileType(it) != null }
    }

    /** Whether the post can be looked up: not from the recycle bin, and with a picture to send. */
    fun offered(post: Post): Boolean = !post.deleted && picture(post) != null

    /** The type and extension of the picture at [url], for the formats all four sites read. */
    fun fileType(url: String): Pair<String, String>? {
        val path = url.substringBefore('?').substringBefore('#')
        val name = path.substringAfterLast('/')
        if ('.' !in name) return null
        return when (name.substringAfterLast('.').lowercase()) {
            "jpg", "jpeg" -> "image/jpeg" to "jpg"
            "png" -> "image/png" to "png"
            "gif" -> "image/gif" to "gif"
            else -> null
        }
    }

    /** The name the copy is saved under: not the original's, whose hash says more than the site needs. */
    fun fileName(
        postId: Long,
        url: String,
    ): String = "starbooru-$postId.${fileType(url)?.second ?: "jpg"}"

    /**
     * Whether sites on the internet can fetch [url]: not from this device, a
     * home or office network, or a private one such as Tailscale's. When
     * it's unclear, not: saving works from anywhere.
     */
    fun reachable(url: String): Boolean {
        val scheme = url.substringBefore("://", "")
        if (scheme.lowercase() !in setOf("http", "https")) return false
        val authority = url.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
        val host = authority.substringAfterLast('@')
        if (host.startsWith("[")) return publicV6(host.removePrefix("[").substringBefore(']'))
        val name = host.substringBefore(':').trimEnd('.').lowercase()
        ipv4(name)?.let { return publicV4(it) }
        return publicName(name)
    }

    private fun ipv4(s: String): IntArray? {
        val parts = s.split('.')
        if (parts.size != 4) return null
        val octets = parts.map { p -> p.toIntOrNull()?.takeIf { p.isNotEmpty() && it in 0..255 } ?: return null }
        return octets.toIntArray()
    }

    private fun publicV4(ip: IntArray): Boolean {
        val (a, b) = ip[0] to ip[1]
        val private = a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168)
        val loopback = a == 127
        val linkLocal = a == 169 && b == 254
        // 100.64.0.0/10: carrier-grade NAT, and Tailscale's addresses.
        val shared = a == 100 && b in 64..127
        val benchmark = a == 198 && (b == 18 || b == 19)
        val documentation =
            (a == 192 && b == 0 && ip[2] == 2) || (a == 198 && b == 51 && ip[2] == 100) || (a == 203 && b == 0 && ip[2] == 113)
        return !(private || loopback || linkLocal || shared || benchmark || documentation || a == 0 || a >= 224)
    }

    private fun publicV6(ip: String): Boolean {
        val s = ip.lowercase()
        if (s == "::1" || s == "::" || s.isEmpty()) return false
        s.substringAfter("::ffff:", "").takeIf { it.isNotEmpty() }?.let { v4 -> ipv4(v4)?.let { return publicV4(it) } }
        val first = s.substringBefore(':').toIntOrNull(16) ?: return false
        val uniqueLocal = first and 0xfe00 == 0xfc00
        val linkLocal = first and 0xffc0 == 0xfe80
        val documentation = s.startsWith("2001:db8:") || s == "2001:db8::"
        return !(uniqueLocal || linkLocal || documentation)
    }

    /** Names only a local network or a VPN knows. */
    private val LOCAL =
        listOf("localhost", "local", "lan", "home", "internal", "intranet", "private", "corp", "home.arpa", "ts.net", "test", "invalid", "example")

    private fun publicName(name: String): Boolean {
        if (name.isEmpty() || '.' !in name) return false
        return LOCAL.none { name == it || name.endsWith(".$it") }
    }
}
