package eu.filipnone.starbooru.ui.share

import androidx.compose.ui.geometry.Size
import eu.filipnone.starbooru.core.CoreException
import eu.filipnone.starbooru.core.MediaKind
import eu.filipnone.starbooru.core.SharedPost
import kotlin.math.min

/** How the share page shows what a link holds, after the web's `logic/share.rs`. */
enum class SharedLayout {
    /** Every post went to the recycle bin since. */
    Empty,

    /** One post, on a stage of its own with zoom. */
    One,

    /** Tiles that open a lightbox. */
    Many,
}

object SharedPage {
    fun layout(count: Int): SharedLayout =
        when {
            count <= 0 -> SharedLayout.Empty
            count == 1 -> SharedLayout.One
            else -> SharedLayout.Many
        }

    /** The link's own title, if it has one worth showing; else the page counts the posts. */
    fun title(title: String?): String? = title?.trim()?.takeIf { it.isNotEmpty() }

    /** The lightbox's next or previous place among [count], if there is one. */
    fun step(
        at: Int,
        count: Int,
        forward: Boolean,
    ): Int? = (if (forward) at + 1 else at - 1).takeIf { it in 0 until count }

    /** What to show: the smaller copy where there is one, unless the original is wanted. A video is always its file. */
    fun shown(
        post: SharedPost,
        original: Boolean,
    ): String =
        if (original || post.kind == MediaKind.VIDEO || post.kind == MediaKind.ANIMATION) {
            post.fileUrl
        } else {
            post.sampleUrl ?: post.fileUrl
        }

    /**
     * Expired, removed, never there, or its owner's account is off: the server
     * doesn't say which. A 404 from something in front of the server isn't one.
     */
    fun missing(error: Throwable): Boolean = error is CoreException.Api && error.status.toInt() == 404 && error.code != "http_error"

    /** The size [post] takes at fit on a stage [w] by [h] pixels. */
    fun fitted(
        post: SharedPost,
        w: Float,
        h: Float,
    ): Size {
        val pw = post.width?.toFloat()?.takeIf { it > 0f } ?: return Size(w, h)
        val ph = post.height?.toFloat()?.takeIf { it > 0f } ?: return Size(w, h)
        val k = min(w / pw, h / ph)
        return Size(pw * k, ph * k)
    }
}
