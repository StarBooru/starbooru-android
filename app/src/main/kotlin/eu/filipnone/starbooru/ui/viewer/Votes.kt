package eu.filipnone.starbooru.ui.viewer

import eu.filipnone.starbooru.core.Post
import kotlin.math.roundToLong
import kotlin.math.sign

/**
 * Votes as the web counts them (`logic/votes.rs` and `logic/stars.rs`): what
 * a press does, and the tally to show before the server answers, so a vote
 * shows at once and goes back if it fails.
 */
object Votes {
    const val MAX_STARS = 5

    /** The vote after pressing up (1) or down (-1): the vote already cast takes it back. */
    fun nextVote(
        current: Int,
        pressed: Int,
    ): Int {
        val p = pressed.sign
        return if (current.sign == p) 0 else p
    }

    /** The score once vote [old] gives way to [new]. */
    fun rescored(
        score: Int,
        old: Int,
        new: Int,
    ): Int = score - old.sign + new.sign

    /** The stars after pressing star [pressed]: the one already given takes the vote back. */
    fun nextStars(
        current: Int,
        pressed: Int,
    ): Int {
        val p = pressed.coerceIn(1, MAX_STARS)
        return if (p == current) 0 else p
    }

    /** What stars count as when the server also keeps a score: 4 or 5 a like, 1 or 2 a dislike. */
    fun likeOf(stars: Int): Int =
        when {
            stars >= 4 -> 1
            stars == 1 || stars == 2 -> -1
            else -> 0
        }

    /**
     * The average (null without votes) and the count once stars [old] give
     * way to [new]. The average comes to two decimals, so the sum rounds back
     * to whole stars first.
     */
    fun restarred(
        stars: Double?,
        votes: Int,
        old: Int,
        new: Int,
    ): Pair<Double?, Int> {
        val before = ((stars ?: 0.0) * votes).roundToLong().coerceAtLeast(0)
        val sum = before - old.coerceAtMost(MAX_STARS) + new.coerceAtMost(MAX_STARS)
        val left = if (old > 0) (votes - 1).coerceAtLeast(0) else votes
        val count = if (new > 0) left + 1 else left
        val average = if (count > 0 && sum > 0) Math.round(sum.toDouble() / count * 100.0) / 100.0 else null
        return average to count
    }
}

/** What a post shows of its votes. */
data class Tally(val score: Int, val myVote: Int, val stars: Double?, val votes: Int, val myStars: Int) {
    /** The tally once a like or dislike ([like] is 1, -1 or 0) takes the vote's place. */
    fun liked(like: Int): Tally = copy(score = Votes.rescored(score, myVote, like), myVote = like.sign)

    /** The tally once [new] stars take the vote's place. */
    fun starred(new: Int): Tally {
        val (average, count) = Votes.restarred(stars, votes, myStars, new)
        val like = Votes.likeOf(new)
        return Tally(
            score = Votes.rescored(score, Votes.likeOf(myStars), like),
            myVote = like,
            stars = average,
            votes = count,
            myStars = new,
        )
    }

    fun into(post: Post): Post =
        post.copy(
            score = score,
            myVote = myVote.toShort(),
            stars = stars,
            votes = votes.toUInt(),
            myStars = myStars.toUByte(),
        )

    companion object {
        fun of(post: Post): Tally = Tally(post.score, post.myVote.toInt(), post.stars, post.votes.toInt(), post.myStars.toInt())
    }
}
