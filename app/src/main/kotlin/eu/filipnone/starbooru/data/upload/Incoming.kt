package eu.filipnone.starbooru.data.upload

import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.content.IntentCompat
import java.io.File
import java.io.IOException
import java.util.UUID

/** A file or a link on its way into the upload list. */
sealed interface IncomingItem {
    /** A file copied into the queue's folder as [id]. [path] is where it was in a picked folder. */
    data class Copied(val id: String, val name: String, val size: Long, val mime: String, val path: String = "") : IncomingItem

    data class Link(val url: String) : IncomingItem

    /** Over the server's limit, so left uncopied. */
    data class TooBig(val name: String, val size: Long, val mime: String, val path: String = "") : IncomingItem

    /** Not an image or a video. */
    data class Skipped(val name: String) : IncomingItem

    /** It couldn't be read, for [reason]. */
    data class Unreadable(val name: String, val reason: String) : IncomingItem
}

/**
 * Files and links handed to the app by the pickers, the clipboard or another
 * app's share sheet. Each file is copied into the queue's folder at once, as
 * the app may lose the right to read it when the screen goes. Every function
 * here reads and writes files, so call them off the main thread.
 */
object Incoming {
    /** How far a copy of several files has got. */
    fun interface Progress {
        fun copied(
            done: Int,
            total: Int,
        )
    }

    private val NONE = Progress { _, _ -> }

    /** Whether [intent] is another app sharing files or text with this one. */
    fun isShare(intent: Intent?): Boolean = intent?.action == Intent.ACTION_SEND || intent?.action == Intent.ACTION_SEND_MULTIPLE

    /**
     * The files and links another app shares through [intent], with their
     * streams copied into the upload folder. Blocking. Hand the result to
     * [UploadQueue.add], or call [UploadQueue.receive] to do both off the
     * main thread.
     */
    fun fromIntent(
        context: Context,
        intent: Intent,
    ): List<IncomingItem> {
        val queue = UploadQueue.get(context)
        return fromIntent(context, intent, queue.dir, queue.limit(), queue.urlImport(), NONE)
    }

    internal fun fromIntent(
        context: Context,
        intent: Intent,
        dir: File,
        limit: Long,
        links: Boolean,
        progress: Progress,
    ): List<IncomingItem> {
        if (!isShare(intent)) return emptyList()
        val uris = LinkedHashSet<Uri>()
        if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { uris += it }
        } else {
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { uris += it }
        }
        intent.clipData?.let { clip -> (0 until clip.itemCount).mapNotNullTo(uris) { clip.getItemAt(it)?.uri } }
        if (uris.isNotEmpty()) return fromUris(context, uris.toList(), dir, limit, progress)
        // A browser shares the page's address, perhaps with its title around it.
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
        return if (links) Accept.parseUrls(text).map { IncomingItem.Link(it) } else emptyList()
    }

    /** The files at [uris], from a picker or a share. */
    fun fromUris(
        context: Context,
        uris: List<Uri>,
        dir: File,
        limit: Long,
        progress: Progress = NONE,
    ): List<IncomingItem> {
        val resolver = context.contentResolver
        return uris.mapIndexed { i, uri ->
            progress.copied(i, uris.size)
            val (name, size) = about(resolver, uri)
            copy(resolver, uri, dir, limit, name, size, mimeOf(resolver, uri), "")
        }.also { progress.copied(uris.size, uris.size) }
    }

    /** What the clipboard holds: its files, and the links in its text when [links]. */
    fun fromClip(
        context: Context,
        clip: ClipData,
        dir: File,
        limit: Long,
        links: Boolean,
        progress: Progress = NONE,
    ): List<IncomingItem> {
        val items = (0 until clip.itemCount).mapNotNull { clip.getItemAt(it) }
        val uris = items.mapNotNull { it.uri }
        val text = if (links) items.mapNotNull { it.text?.toString() }.joinToString(" ") else ""
        return fromUris(context, uris, dir, limit, progress) + Accept.parseUrls(text).map { IncomingItem.Link(it) }
    }

    /**
     * Every image and video in the folder [tree] and the folders in it, each
     * with its path from the folder picked (`Summer 2024/beach/a.jpg`). Hidden
     * files and folders are left out without a word.
     */
    fun fromTree(
        context: Context,
        tree: Uri,
        dir: File,
        limit: Long,
        progress: Progress = NONE,
    ): List<IncomingItem> {
        val resolver = context.contentResolver
        val rootId =
            try {
                DocumentsContract.getTreeDocumentId(tree)
            } catch (_: IllegalArgumentException) {
                return emptyList()
            }
        val rootName = about(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, rootId)).first
        val found = ArrayList<Entry>()
        walk(resolver, tree, rootId, rootName, 0, found)
        return found.mapIndexed { i, e ->
            progress.copied(i, found.size)
            copy(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, e.id), dir, limit, e.name, e.size, e.mime, e.path)
        }.also { progress.copied(found.size, found.size) }
    }

    private data class Entry(val id: String, val name: String, val mime: String, val size: Long?, val path: String)

    private fun walk(
        resolver: ContentResolver,
        tree: Uri,
        parent: String,
        path: String,
        depth: Int,
        into: MutableList<Entry>,
    ) {
        if (depth > MAX_DEPTH) return
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
        val columns =
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
            )
        val folders = ArrayList<Pair<String, String>>()
        try {
            resolver.query(children, columns, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    if (name.startsWith('.')) continue
                    val mime = c.getString(2).orEmpty()
                    val size = if (c.isNull(3)) null else c.getLong(3)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        folders += id to "$path/$name"
                    } else {
                        into += Entry(id, name, mime, size, "$path/$name")
                    }
                }
            }
        } catch (_: RuntimeException) {
            // A provider that won't list this folder: the rest still come.
            return
        }
        for ((id, sub) in folders) walk(resolver, tree, id, sub, depth + 1, into)
    }

    /** The name and size the provider gives [uri]. */
    private fun about(
        resolver: ContentResolver,
        uri: Uri,
    ): Pair<String, Long?> {
        var name: String? = null
        var size: Long? = null
        try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    name = c.getString(0)
                    size = if (c.isNull(1)) null else c.getLong(1)
                }
            }
        } catch (_: RuntimeException) {
        }
        val shown = name?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        return shown to size
    }

    private fun mimeOf(
        resolver: ContentResolver,
        uri: Uri,
    ): String =
        try {
            resolver.getType(uri).orEmpty()
        } catch (_: RuntimeException) {
            ""
        }

    /**
     * Copies one file into [dir] under a new id, if it's an image or a video
     * within [limit]. `file://` addresses are refused: they could name the
     * app's own private files.
     */
    private fun copy(
        resolver: ContentResolver,
        uri: Uri,
        dir: File,
        limit: Long,
        name: String,
        listed: Long?,
        mime: String,
        path: String,
    ): IncomingItem {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return IncomingItem.Skipped(name)
        val verdict = Accept.verdict(name, mime, listed ?: -1)
        if (verdict == Accept.Verdict.Skip) return IncomingItem.Skipped(name)
        if (listed != null && Accept.tooBig(listed, limit)) return IncomingItem.TooBig(name, listed, mime, path)
        dir.mkdirs()
        val id = UUID.randomUUID().toString()
        val part = File(dir, "$id$PART")
        try {
            var over = false
            val stream = resolver.openInputStream(uri) ?: return IncomingItem.Unreadable(name, "no stream")
            stream.use { input ->
                part.outputStream().use { out ->
                    val buffer = ByteArray(BUFFER)
                    var total = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        if (Accept.tooBig(total, limit)) {
                            over = true
                            break
                        }
                        out.write(buffer, 0, n)
                    }
                }
            }
            val size = part.length()
            when {
                over -> {
                    part.delete()
                    return IncomingItem.TooBig(name, listed ?: size, mime, path)
                }

                size == 0L || (verdict == Accept.Verdict.Look && !Accept.isMediaHead(head(part))) -> {
                    part.delete()
                    return IncomingItem.Skipped(name)
                }
            }
            val file = File(dir, id)
            if (!part.renameTo(file)) {
                part.delete()
                return IncomingItem.Unreadable(name, "can't keep a copy")
            }
            return IncomingItem.Copied(id, name, size, mime, path)
        } catch (e: IOException) {
            part.delete()
            return IncomingItem.Unreadable(name, e.message ?: e.javaClass.simpleName)
        } catch (e: RuntimeException) {
            // The provider refused, or lost the file.
            part.delete()
            return IncomingItem.Unreadable(name, e.message ?: e.javaClass.simpleName)
        }
    }

    private fun head(file: File): ByteArray =
        file.inputStream().use { input ->
            val bytes = ByteArray(Accept.HEAD_BYTES)
            val n = input.read(bytes)
            if (n <= 0) ByteArray(0) else bytes.copyOf(n)
        }

    /** A copy on its way: removed if the app dies before it's whole. */
    const val PART = ".part"
    private const val BUFFER = 64 * 1024
    private const val MAX_DEPTH = 32
}
