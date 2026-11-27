package eu.filipnone.starbooru.ui.viewer

import eu.filipnone.starbooru.ui.gestures.Turn

/**
 * How many swipes can wait for the server to say what's beside the post,
 * as on the web. A burst stops soon after the finger does.
 */
const val MOST_QUEUED = 6

/** The steps waiting after one more to [side], next counting up. The other way cancels one out. */
fun queued(
    steps: Int,
    side: Side,
): Int {
    val step = if (side == Side.Next) 1 else -1
    return (steps + step).coerceIn(-MOST_QUEUED, MOST_QUEUED)
}

/** The side a swipe that turns [this] way goes to. */
val Turn.side: Side get() = if (this == Turn.Next) Side.Next else Side.Prev

/**
 * Whether there's no post on [side]: only once the server has said so.
 * Until then a swipe that way may still go, and waits for the answer.
 */
fun ViewerState.endAt(side: Side): Boolean {
    val standIn = if (side == Side.Next) next else prev
    if (standIn != null) return false
    val near = neighbors ?: return false
    return (if (side == Side.Next) near.next else near.prev) == null
}

/**
 * Where the slide stands once a turn to [to] has been made, with the
 * pictures [span] apart. The picture taking the middle stays where the
 * finger left it, and goes on from there. In a burst of turns it comes in
 * from one span out at most, so it's never off the stage.
 */
fun slideAfter(
    slide: Float,
    to: Turn,
    span: Float,
): Float = (if (to == Turn.Next) slide + span else slide - span).coerceIn(-span, span)
