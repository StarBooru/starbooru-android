package eu.filipnone.starbooru.data.upload

import eu.filipnone.starbooru.core.normalizedTag

/**
 * What the Upload page takes, as the web's `logic/upload.rs`: which files,
 * the links in some text, and a folder's name as a tag. The server checks
 * every file again.
 */
object Accept {
    /** The server's formats: type, extension. */
    val FORMATS: List<Pair<String, String>> =
        listOf(
            "image/jpeg" to "jpg",
            "image/png" to "png",
            "image/webp" to "webp",
            "image/bmp" to "bmp",
            "image/tiff" to "tiff",
            "image/gif" to "gif",
            "image/avif" to "avif",
            "image/jxl" to "jxl",
            "video/mp4" to "mp4",
            "video/webm" to "webm",
            "video/x-matroska" to "mkv",
            "video/quicktime" to "mov",
        )

    /** Names the server's file types go by, which save reading the file. */
    private val MEDIA_EXTENSIONS =
        setOf("jpg", "jpeg", "jfif", "png", "gif", "webp", "bmp", "tif", "tiff", "avif", "jxl", "mp4", "m4v", "webm", "mkv", "mov")

    private val VIDEO_EXTENSIONS = setOf("mp4", "m4v", "webm", "mkv", "mov")

    /** How much of a file's start [isMediaHead] needs. */
    const val HEAD_BYTES = 32

    enum class Verdict {
        Take,
        Skip,

        /** Neither the name nor the type says: its first bytes do. */
        Look,
    }

    private fun extension(name: String): String? {
        val dot = name.lastIndexOf('.')
        return if (dot <= 0) null else name.substring(dot + 1)
    }

    /** An image or video by its name. Hidden files (`.nomedia`, `._x.jpg`) are not. */
    fun isMediaName(name: String): Boolean =
        !name.startsWith('.') && extension(name)?.lowercase()?.let { it in MEDIA_EXTENSIONS } == true

    /** One of the server's formats by the type the phone gives a file. */
    fun isMediaType(mime: String): Boolean = FORMATS.any { (m, _) -> m.equals(mime.trim(), ignoreCase = true) }

    /**
     * Whether to take a file called [name], of type [mime] and [size] bytes.
     * A name or a type can only say yes: a saved picture may be called just
     * `anime`, so the rest are looked into.
     */
    fun verdict(
        name: String,
        mime: String,
        size: Long,
    ): Verdict =
        when {
            name.startsWith('.') || size == 0L -> Verdict.Skip
            isMediaName(name) || isMediaType(mime) -> Verdict.Take
            else -> Verdict.Look
        }

    /** Whether a file's first bytes are one of the server's formats. */
    fun isMediaHead(head: ByteArray): Boolean {
        fun at(
            offset: Int,
            vararg bytes: Int,
        ): Boolean = head.size >= offset + bytes.size && bytes.indices.all { head[offset + it].toInt() and 0xFF == bytes[it] }

        fun text(
            offset: Int,
            s: String,
        ): Boolean = at(offset, *s.map { it.code }.toIntArray())
        val ftyp = text(4, "ftyp")
        return at(0, 0xFF, 0xD8, 0xFF) ||
            at(0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) ||
            text(0, "GIF8") ||
            (text(0, "RIFF") && text(8, "WEBP")) ||
            text(0, "BM") ||
            at(0, 0x49, 0x49, 0x2A, 0x00) ||
            at(0, 0x4D, 0x4D, 0x00, 0x2A) ||
            at(0, 0xFF, 0x0A) ||
            at(0, 0x00, 0x00, 0x00, 0x0C, 0x4A, 0x58, 0x4C, 0x20) ||
            at(0, 0x1A, 0x45, 0xDF, 0xA3) ||
            // MP4, MOV and AVIF all start with an ftyp box; HEIC, which the server doesn't keep, too.
            (ftyp && !text(8, "heic") && !text(8, "heix") && !text(8, "mif1"))
    }

    /** Whether a file is a video, for a preview of its first frame. */
    fun isVideo(
        name: String,
        mime: String,
    ): Boolean = mime.startsWith("video/") || extension(name)?.lowercase()?.let { it in VIDEO_EXTENSIONS } == true

    /** Whether a file of [size] bytes is over the server's [limit]. A limit of 0 is no limit. */
    fun tooBig(
        size: Long,
        limit: Long,
    ): Boolean = limit > 0 && size > limit

    /** Names of the files left out, for a notice: the first few, and how many more there were. */
    fun skippedNames(names: List<String>): Pair<String, Int> {
        val shown = 3
        return names.take(shown).joinToString(", ") to (names.size - shown).coerceAtLeast(0)
    }

    /**
     * The name of the folder holding the file at [path] (`Summer 2024/a.jpg`,
     * or `/Summer 2024/a.jpg`), as a tag: `summer_2024`.
     */
    fun folderTag(path: String): String? {
        val trimmed = path.trimEnd('/')
        val slash = trimmed.lastIndexOf('/')
        if (slash < 0) return null
        val folder = trimmed.substring(0, slash).substringAfterLast('/')
        if (folder.isEmpty()) return null
        return normalizedTag(folder)
    }

    /** The `http(s)://` addresses in pasted or typed text, one per word, without repeats. */
    fun parseUrls(text: String): List<String> {
        val out = ArrayList<String>()
        for (word in text.split(Regex("\\s+"))) {
            val lower = word.lowercase()
            val rest = lower.substringAfter("://", "")
            if ((lower.startsWith("http://") || lower.startsWith("https://")) && rest.isNotEmpty() && word !in out) out += word
        }
        return out
    }

    /** Whether an address ends like a picture's. A link to a page doesn't. */
    fun isPictureUrl(url: String): Boolean {
        val path = url.split('?', '#').first()
        val dot = path.lastIndexOf('.')
        if (dot < 0) return false
        return path.substring(dot + 1).lowercase() in setOf("jpg", "jpeg", "png", "gif", "webp", "avif", "bmp")
    }

    /** The site an address is on, as a tile's caption: `gelbooru.com`. */
    fun hostOf(url: String): String {
        val rest = url.substringAfter("://", url)
        return rest.split('/', '?', '#').first().removePrefix("www.")
    }

    /** A format's short name, as `PNG`, for one the server keeps. */
    fun formatOf(mime: String): String? = FORMATS.firstOrNull { (m, _) -> m.equals(mime.trim(), ignoreCase = true) }?.second?.uppercase()

    /** The same from a name or an address that ends like a kept format's. */
    fun formatByName(name: String): String? {
        val path = name.split('?', '#').first()
        val dot = path.lastIndexOf('.')
        if (dot <= 0) return null
        val stem = path.substring(0, dot)
        val raw = path.substring(dot + 1)
        if (stem.endsWith('/') || '/' in raw) return null
        val ext =
            when (val e = raw.lowercase()) {
                "jpeg", "jpe", "jfif" -> "jpg"
                "tif" -> "tiff"
                "m4v" -> "mp4"
                else -> e
            }
        return if (FORMATS.any { it.second == ext }) ext.uppercase() else null
    }
}
