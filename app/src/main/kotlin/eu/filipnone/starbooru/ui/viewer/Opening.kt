package eu.filipnone.starbooru.ui.viewer

import eu.filipnone.starbooru.core.Post

/**
 * The post a page opens the viewer with, as that page had it. The viewer
 * takes it as it's made, so its first frame already holds the picture the
 * tile grows into, and asks the server for the rest after. The web's
 * `post::opening`.
 */
class Opening {
    private var left: Post? = null

    /** Leaves [post] for the viewer about to open it. */
    fun put(post: Post) {
        left = post
    }

    /** The post left for [id], once. Whatever else was left is dropped. */
    fun take(id: Long): Post? {
        val post = left
        left = null
        return post?.takeIf { it.id == id }
    }
}
