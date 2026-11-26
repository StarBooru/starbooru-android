package eu.filipnone.starbooru.ui.common

import android.content.Context
import androidx.compose.ui.graphics.Color
import coil3.request.ImageRequest
import eu.filipnone.starbooru.core.Post
import eu.filipnone.starbooru.core.tint

/**
 * Requests for a post's pictures. Each is cached under the file's SHA-256 and
 * the copy it is, not under its address, which is signed again every six
 * hours: a picture seen once opens from the disk after that, offline too.
 */
object Media {
    fun thumb(
        context: Context,
        post: Post
    ): ImageRequest? = post.thumbUrl?.let { request(context, it, "thumb:${post.sha256}") }

    /** The smaller copy of a big still picture, or the original. */
    fun view(
        context: Context,
        post: Post,
        original: Boolean
    ): ImageRequest {
        val sample = post.sampleUrl
        return if (!original && sample != null) {
            request(context, sample, "sample:${post.sha256}")
        } else {
            request(context, post.fileUrl, "original:${post.sha256}")
        }
    }

    private fun request(
        context: Context,
        url: String,
        key: String
    ): ImageRequest =
        ImageRequest
            .Builder(context)
            .data(url)
            .diskCacheKey(key)
            .memoryCacheKey(key)
            .build()
}

/** The post's main colour, for its tile until the thumbnail arrives. */
fun Post.tintColor(): Color? = tint(palette)?.let { Color(0xFF000000L or it.toLong()) }

/** A video's length as m:ss, or h:mm:ss past an hour. */
fun clock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
