/*
 * The rules of the swipes, without the pointer handling: which way a finger
 * is going, how far the picture follows it, and what letting go does. Ported
 * from the web's `logic/swipe.rs` and `logic/viewer.rs`, and the reference's
 * pull down to close.
 *
 * Distances are in dp (the web's CSS pixels), times in milliseconds and
 * speeds in dp per millisecond. Divide pointer positions by the density
 * before they come here.
 */

package eu.filipnone.starbooru.ui.gestures

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sign

/** What swiping a gallery tile to one side does, as Settings › Gestures picks it. */
enum class SwipeAction(
    /** How it is stored, the same as on the web. */
    val key: String,
) {
    Off("off"),
    Like("like"),
    Select("select"),
    Share("share"),
    Download("download"),
    Delete("delete"),
    ;

    companion object {
        /** The action stored as [key]. An unknown one, from a newer version, is [Off]. */
        fun of(key: String?): SwipeAction = entries.firstOrNull { it.key == key } ?: Off
    }
}

/** The side a tile is swiped to: [Left] when the finger goes left. */
enum class Side {
    Left,
    Right,
    ;

    companion object {
        fun of(dx: Float): Side = if (dx < 0f) Left else Right
    }
}

/** What a finger on a tile is doing. */
enum class SwipeAxis {
    /** Not far enough to tell yet. */
    Unknown,

    /** Up or down: the gallery's scroll. */
    Scroll,
    Sideways,
}

/** Where a finger was at a moment: [timeMillis] on any steady clock, such as `PointerInputChange.uptimeMillis`. */
data class TrailPoint(val timeMillis: Long, val x: Float, val y: Float)

/** How many of a finger's last positions are worth keeping for its speed. */
const val TRAIL_LENGTH = 8

/** The time a finger's speed is measured over as it lets go. */
const val SPEED_WINDOW_MILLIS = 100L

/** [trail] with [point] at its end, no longer than [TRAIL_LENGTH]. */
fun trailWith(
    trail: List<TrailPoint>,
    point: TrailPoint,
): List<TrailPoint> = (trail + point).takeLast(TRAIL_LENGTH)

/** The finger's speed along x as it let go, in dp per ms, over its last [SPEED_WINDOW_MILLIS]. */
fun speedX(trail: List<TrailPoint>): Float = speed(trail) { it.x }

/** The finger's speed along y, downwards positive, as [speedX] works it out. */
fun speedY(trail: List<TrailPoint>): Float = speed(trail) { it.y }

private inline fun speed(
    trail: List<TrailPoint>,
    along: (TrailPoint) -> Float,
): Float {
    val last = trail.lastOrNull() ?: return 0f
    // A slow start doesn't count against a fast end.
    val first = trail.firstOrNull { last.timeMillis - it.timeMillis <= SPEED_WINDOW_MILLIS } ?: last
    val time = last.timeMillis - first.timeMillis
    return if (time > 0) (along(last) - along(first)) / time else 0f
}

/** Whether a flick at [speed] goes the way [d] does, [past] dp at least. */
private fun flung(
    d: Float,
    speed: Float,
    past: Float,
): Boolean = abs(speed) > FLING_SPEED && speed.sign == d.sign && abs(d) > past

/** A flick this fast does it however short. */
private const val FLING_SPEED = 0.5f

/** The smaller of two lengths, where a NaN one doesn't count. */
private fun least(
    a: Float,
    b: Float,
): Float =
    when {
        a.isNaN() -> b
        b.isNaN() -> a
        else -> minOf(a, b)
    }

/** Swiping a gallery tile aside, to do what Settings › Gestures picked for that side. */
object TileSwipe {
    /** How far a finger moves before it counts as going somewhere. */
    const val SLOP = 10f

    /** How much more sideways than up or down a move must be to be a swipe. */
    private const val SIDEWAYS = 1.2f

    /** A side with nothing to do gives this share of the way, and no more. */
    private const val STIFF = 0.15f

    /** A flick must go this far to count. */
    private const val FLING_MIN = 24f

    /** What a finger [dx], [dy] dp from where it came down is doing. */
    fun axis(
        dx: Float,
        dy: Float,
    ): SwipeAxis =
        when {
            !dx.isFinite() || !dy.isFinite() || hypot(dx, dy) < SLOP -> SwipeAxis.Unknown
            abs(dx) >= abs(dy) * SIDEWAYS -> SwipeAxis.Sideways
            else -> SwipeAxis.Scroll
        }

    /** How far a swipe must go to do its action, on a tile [width] dp wide. */
    fun reach(width: Float): Float = (width * 0.4f).coerceIn(56f, 120f)

    /** How far the picture follows a finger [dx] along, where [can] says whether that side does something. */
    fun follow(
        dx: Float,
        can: Boolean,
    ): Float = if (can) dx else dx * STIFF

    /** The side whose action runs as a finger lets go [dx] along a tile [width] wide at [speed]: far enough, or flicked that way. */
    fun release(
        dx: Float,
        width: Float,
        speed: Float,
        can: (Side) -> Boolean,
    ): Side? {
        val side = Side.of(dx)
        val far = abs(dx) >= reach(width) || flung(dx, speed, FLING_MIN)
        return side.takeIf { far && can(side) }
    }

    /** What swiping to [side] does, of the two chosen. */
    fun action(
        side: Side,
        left: SwipeAction,
        right: SwipeAction,
    ): SwipeAction =
        when (side) {
            Side.Left -> left
            Side.Right -> right
        }
}

/** Where a swipe on the viewer's stage takes it. */
enum class Turn { Next, Previous }

/** What a finger on the viewer's stage is doing. */
enum class StageDrag {
    /** Still within [ViewerSwipe.SLOP]. */
    Undecided,

    /** To the previous or next post. */
    Sideways,

    /** Pulling the picture down to close. */
    Down,

    /** Not the stage's: the zoom's pan or the page's scroll. */
    None,
}

/** Swiping the viewer's picture to the previous or next post. */
object ViewerSwipe {
    /** How far a finger travels before it counts as a swipe. */
    const val SLOP = 10f

    /** A flick must go this far to turn. */
    private const val FLING_MIN = 30f

    /** At either end of the list, the picture follows this share of the way. */
    private const val AT_END = 0.25f

    /** Null while the finger is still within [SLOP], then whether it went more sideways than up or down. */
    fun sideways(
        dx: Float,
        dy: Float,
    ): Boolean? = if (hypot(dx, dy) >= SLOP) abs(dx) > abs(dy) else null

    /**
     * What a finger [dx], [dy] dp from where it came down is doing: a zoomed
     * picture pans instead, and a pull down only works where [pullable] (in
     * fullscreen, or with the page at its top).
     */
    fun drag(
        dx: Float,
        dy: Float,
        zoomed: Boolean,
        pullable: Boolean,
    ): StageDrag {
        val sideways = sideways(dx, dy) ?: return StageDrag.Undecided
        return when {
            zoomed -> StageDrag.None
            sideways -> StageDrag.Sideways
            dy > 0f && pullable -> StageDrag.Down
            else -> StageDrag.None
        }
    }

    /** How far a swipe must go to turn: 22% of a stage [stageWidth] dp wide, at most 140 dp. */
    fun reach(stageWidth: Float): Float = least(stageWidth * 0.22f, 140f)

    /** Whether letting go [dx] dp across at [speed] turns: [Turn.Next] when the finger went left. */
    fun turn(
        dx: Float,
        speed: Float,
        stageWidth: Float,
    ): Turn? {
        if (!(abs(dx) >= reach(stageWidth) || flung(dx, speed, FLING_MIN))) return null
        return if (dx < 0f) Turn.Next else Turn.Previous
    }

    /** Where the picture follows the finger to: at an end of the list it gives a little and no more. */
    fun resist(
        dx: Float,
        atEnd: Boolean,
    ): Float = if (atEnd) dx * AT_END else dx
}

/** Pulling the viewer's picture down to close it, as the reference did. */
object PullToClose {
    /** A pull faster than [FLING_SPEED] closes once it has gone this far. */
    private const val FLING_MIN = 40f

    /** How far a pull must go to close: 18% of a stage [stageHeight] dp tall, at most 160 dp. */
    fun reach(stageHeight: Float): Float = least(160f, stageHeight * 0.18f)

    /**
     * Where the picture sits as a finger drags it [fx], [fy] dp from where it
     * came down, starting from [base] (where a picture on its way back was
     * caught). It follows the finger half as far sideways, and a fifth of the
     * way up.
     */
    fun follow(
        fx: Float,
        fy: Float,
        base: Offset = Offset.Zero,
    ): Offset {
        val dy = base.y + fy
        return Offset(base.x + fx * 0.5f, if (dy > 0f) dy else dy * 0.2f)
    }

    /** How far the page behind has faded, 0 to 1, with the picture [y] dp down a stage [stageHeight] dp tall. */
    fun pull(
        y: Float,
        stageHeight: Float,
    ): Float {
        val p = maxOf(0f, y) / maxOf(1f, stageHeight * 0.5f)
        return if (p.isNaN()) 0f else p.coerceIn(0f, 1f)
    }

    /** The picture's size at [pull]: a quarter smaller all the way down. */
    fun scale(pull: Float): Float = if (pull.isNaN()) 1f else 1f - 0.25f * pull.coerceIn(0f, 1f)

    /** Whether letting go after [dy] dp down (the drag's own, not where the picture is) at [speed] closes. */
    fun closes(
        dy: Float,
        speed: Float,
        stageHeight: Float,
    ): Boolean = dy >= reach(stageHeight) || (speed > FLING_SPEED && dy > FLING_MIN)
}
