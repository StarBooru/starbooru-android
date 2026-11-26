package eu.filipnone.starbooru.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import eu.filipnone.starbooru.core.ApiInterface
import eu.filipnone.starbooru.core.MediaKind
import eu.filipnone.starbooru.core.Post
import eu.filipnone.starbooru.core.downloadName
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Posts' originals saved to the phone's gallery, or handed to another app. */
object Files {
    /** The folder under Pictures or Movies the app saves into. */
    private const val FOLDER = "StarBooru"

    /** Whether saving to the gallery needs the storage permission first (Android 9 and older). */
    val needsPermission: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    /**
     * Saves [post]'s original to the gallery, under Pictures/StarBooru (or
     * Movies for a video), and answers where. A file of the same name is
     * never written over: the new one gets a number.
     */
    suspend fun saveToGallery(
        context: Context,
        api: ApiInterface,
        post: Post,
    ): Uri {
        val name = downloadName(post.sha256, post.fileUrl, post.mime)
        val local = fetch(context, api, post, name)
        return withContext(Dispatchers.IO) {
            try {
                val (into, folder) = collection(post)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    intoMediaStore(context, into, folder, post.mime, name, local)
                } else {
                    intoFolder(context, folder, post.mime, name, local)
                }
            } finally {
                local.delete()
            }
        }
    }

    /**
     * Saves the picture at [url] (a lighter copy of a post) to the gallery as
     * [name], for a site's own upload button, and answers where.
     */
    suspend fun saveCopy(
        context: Context,
        api: ApiInterface,
        url: String,
        name: String,
        mime: String,
    ): Uri {
        val dir = File(context.cacheDir, "downloads").apply { mkdirs() }
        val local = File(dir, name)
        api.downloadTo(url, local.absolutePath)
        return withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    intoMediaStore(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, Environment.DIRECTORY_PICTURES, mime, name, local)
                } else {
                    intoFolder(context, Environment.DIRECTORY_PICTURES, mime, name, local)
                }
            } finally {
                local.delete()
            }
        }
    }

    /** Opens Android's share sheet with the posts' files themselves. */
    suspend fun share(
        context: Context,
        api: ApiInterface,
        posts: List<Post>,
    ) {
        val uris =
            posts.map { post ->
                val name = downloadName(post.sha256, post.fileUrl, post.mime)
                val dir = File(context.cacheDir, "shared").apply { mkdirs() }
                val file = File(dir, name)
                if (!file.exists() || file.length() != post.fileSize) {
                    val part = fetch(context, api, post, "$name.dl")
                    withContext(Dispatchers.IO) { part.renameTo(file) }
                }
                FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            }
        val first = uris.firstOrNull() ?: return
        val type = posts.map { it.mime }.distinct().singleOrNull() ?: "*/*"
        val send =
            if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, first)
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
        send.type = type
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Opens a saved file in the app that shows its kind. */
    fun open(
        context: Context,
        uri: Uri,
        mime: String,
    ) {
        val view =
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(view) }
    }

    private suspend fun fetch(
        context: Context,
        api: ApiInterface,
        post: Post,
        name: String,
    ): File {
        val dir = File(context.cacheDir, "downloads").apply { mkdirs() }
        val file = File(dir, name)
        api.downloadTo(post.fileUrl, file.absolutePath)
        return file
    }

    private fun collection(post: Post): Pair<Uri, String> =
        if (post.kind == MediaKind.VIDEO) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_MOVIES
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_PICTURES
        }

    private fun intoMediaStore(
        context: Context,
        into: Uri,
        folder: String,
        mime: String,
        name: String,
        local: File,
    ): Uri {
        val resolver = context.contentResolver
        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/$FOLDER")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        // MediaStore numbers a name already taken in the folder.
        val uri = resolver.insert(into, values) ?: throw java.io.IOException("the gallery refused the file")
        try {
            resolver.openOutputStream(uri)?.use { out -> local.inputStream().use { it.copyTo(out) } }
                ?: throw java.io.IOException("the gallery refused the file")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: java.io.IOException) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    @Suppress("DEPRECATION")
    private fun intoFolder(
        context: Context,
        folder: String,
        mime: String,
        name: String,
        local: File,
    ): Uri {
        val dir = File(Environment.getExternalStoragePublicDirectory(folder), FOLDER).apply { mkdirs() }
        val file = unique(dir, name)
        local.inputStream().use { input -> file.outputStream().use { input.copyTo(it) } }
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mime), null)
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    /** [name] in [dir], or `name (2).ext` and onwards when it's taken. */
    internal fun unique(
        dir: File,
        name: String,
    ): File {
        val stem = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "")
        var file = File(dir, name)
        var n = 2
        while (file.exists()) {
            file = File(dir, if (ext.isEmpty()) "$stem ($n)" else "$stem ($n).$ext")
            n++
        }
        return file
    }
}
