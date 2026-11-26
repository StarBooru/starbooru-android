package eu.filipnone.starbooru.ui.gallery

/**
 * The posts picked in the gallery, as the web keeps them (`logic/bulk.rs`
 * and `logic/drag_select.rs`): toggles, ranges, all or none, never more than
 * one edit takes. Immutable, so a screen can hold it in state.
 */
data class Selection(
    val ids: Set<Long> = emptySet(),
    /** Where a range starts from: the post picked last. */
    val anchor: Long? = null,
) {
    val size: Int get() = ids.size

    operator fun contains(id: Long): Boolean = id in ids

    /** A tap on [id]: only that post changes. */
    fun toggle(id: Long): Selection = with(id, id !in ids).copy(anchor = id)

    /**
     * Every post of [loaded] from place [from] to place [to], either way
     * round, picked or let go of as [on] says. A range goes on from [to].
     */
    fun range(
        loaded: List<Long>,
        from: Int,
        to: Int,
        on: Boolean,
    ): Selection {
        if (loaded.isEmpty()) return this
        val lo = from.coerceIn(loaded.indices)
        val hi = to.coerceIn(loaded.indices)
        val order = if (lo <= hi) (lo..hi) else (lo downTo hi)
        // From [from] outwards, so a full selection keeps the posts beside it.
        var next = this
        for (i in order) next = next.with(loaded[i], on)
        return next.copy(anchor = loaded[hi])
    }

    /** Whether "select all" has nothing left to add. */
    fun isFull(loaded: List<Long>): Boolean = ids.size >= MAX || (loaded.isNotEmpty() && loaded.all { it in ids })

    /** Every loaded post, or none once that's done. */
    fun toggleAll(loaded: List<Long>): Selection =
        if (isFull(loaded)) {
            Selection()
        } else {
            var next = copy(anchor = null)
            for (id in loaded) next = next.with(id, true)
            next
        }

    /** The picked posts in the order the gallery shows them, then any it no longer shows. */
    fun inOrder(loaded: List<Long>): List<Long> = loaded.filter { it in ids } + ids.filter { it !in loaded.toSet() }.sorted()

    fun without(id: Long): Selection = copy(ids = ids - id)

    private fun with(
        id: Long,
        on: Boolean,
    ): Selection =
        when {
            !on -> copy(ids = ids - id)
            id in ids || ids.size >= MAX -> this
            else -> copy(ids = ids + id)
        }

    companion object {
        /** As many posts as one edit takes on the server. */
        const val MAX = 500
    }
}

/**
 * A drag across the tiles from post [from]: it lets go of posts when that
 * one was picked already, and picks them otherwise, from the selection
 * [before] it.
 */
data class DragPick(val before: Selection, val from: Long, val to: Long = from) {
    val adding: Boolean get() = from !in before

    /** The selection as the drag leaves it; null once its first post has left the list. */
    fun selection(loaded: List<Long>): Selection? {
        val start = loaded.indexOf(from).takeIf { it >= 0 } ?: return null
        val end = loaded.indexOf(to).takeIf { it >= 0 } ?: start
        return before.range(loaded, start, end, adding)
    }

    companion object {
        /** How long a finger rests on a tile before moving it drags, in milliseconds. */
        const val HOLD_MS = 450L

        /** The fastest the page scrolls under a finger at an edge, in px a second. */
        private const val EDGE_SPEED = 1600f

        /**
         * How fast the page scrolls under a finger at [y] while dragging, with
         * the tiles in sight from [top] to [bottom]: up near the top, down near
         * the bottom, faster the closer to the edge. In px a second.
         */
        fun edgeSpeed(
            y: Float,
            top: Float,
            bottom: Float,
        ): Float {
            if (!y.isFinite() || !top.isFinite() || !bottom.isFinite()) return 0f
            val band = ((bottom - top) * 0.12f).coerceIn(32f, 96f).coerceAtMost((bottom - top) / 2f)
            if (band <= 0f) return 0f
            val up = (top + band - y) / band
            val down = (y - (bottom - band)) / band
            fun pace(depth: Float): Float {
                val d = depth.coerceAtMost(1f)
                return EDGE_SPEED * d * d
            }
            return when {
                up > 0f && up >= down -> -pace(up)
                down > 0f -> pace(down)
                else -> 0f
            }
        }
    }
}
