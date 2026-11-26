package eu.filipnone.starbooru.ui.gallery

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import eu.filipnone.starbooru.ui.gestures.Side
import eu.filipnone.starbooru.ui.gestures.SwipeAxis
import eu.filipnone.starbooru.ui.gestures.TileSwipe
import eu.filipnone.starbooru.ui.gestures.TrailPoint
import eu.filipnone.starbooru.ui.gestures.speedX
import eu.filipnone.starbooru.ui.gestures.trailWith
import eu.filipnone.starbooru.ui.theme.StarEasing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** What a finger on the grid is doing to its tiles, for the tiles to show. */
@Stable
class GridTouch {
    /** The post under a resting finger. */
    var pressed by mutableStateOf<Long?>(null)

    /** The post being swiped, and how far, in px. */
    var swiped by mutableStateOf<Long?>(null)
    var slide by mutableFloatStateOf(0f)

    /** Whether letting go of the swipe now runs its action. */
    var armed by mutableStateOf(false)
}

/** What the grid needs to know as a finger moves, read fresh each time. */
interface GridHost {
    /** The post at [at] (px in the grid), and the width of its tile in dp. */
    fun tileAt(at: Offset): Pair<Long, Float>?

    val selecting: Boolean

    /** Whether swiping to [side] does something. */
    fun canSwipe(side: Side): Boolean

    fun tap(id: Long)

    fun hold(id: Long)

    fun swiped(
        id: Long,
        side: Side,
    )

    /** A drag across the tiles while selecting: from [from], now over [to]. */
    fun drag(
        from: Long,
        to: Long,
    )

    fun dragEnded()

    /** How fast the page scrolls for a finger at [at] (px in the grid), px a second; 0 away from the edges. */
    fun edgeSpeed(at: Offset): Float

    /** Scrolls the page by [px]; answers how far it went. */
    fun scrollBy(px: Float): Float
}

/**
 * The grid's touch: a tap opens a post (or picks it while selecting), a
 * finger resting on a tile opens its menu (or, while selecting, drags across
 * tiles to pick them, scrolling the page at its edges), and a sideways swipe
 * runs the action chosen for that side. Up and down is the page's.
 */
fun Modifier.gridGestures(
    host: GridHost,
    touch: GridTouch,
    scope: CoroutineScope,
    density: Float,
): Modifier =
    pointerInput(host) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val (id, tileWidth) = host.tileAt(down.position) ?: return@awaitEachGesture
            touch.pressed = id
            var trail = listOf(TrailPoint(down.uptimeMillis, down.position.x / density, down.position.y / density))
            var last: PointerInputChange = down

            // Until the hold: a tap, a scroll, or a swipe.
            val early =
                withTimeoutOrNull(DragPick.HOLD_MS) {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull Early.Gone
                        last = change
                        if (!change.pressed) return@withTimeoutOrNull Early.Tap
                        trail = trailWith(trail, TrailPoint(change.uptimeMillis, change.position.x / density, change.position.y / density))
                        val dx = (change.position.x - down.position.x) / density
                        val dy = (change.position.y - down.position.y) / density
                        when (TileSwipe.axis(dx, dy)) {
                            SwipeAxis.Unknown -> {}

                            SwipeAxis.Scroll -> return@withTimeoutOrNull Early.Gone

                            SwipeAxis.Sideways ->
                                return@withTimeoutOrNull if (!host.selecting && (host.canSwipe(Side.Left) || host.canSwipe(Side.Right))) {
                                    Early.Swipe
                                } else {
                                    Early.Gone
                                }
                        }
                    }
                }
            touch.pressed = null
            when (early) {
                Early.Gone -> return@awaitEachGesture

                Early.Tap -> {
                    last.consume()
                    host.tap(id)
                }

                Early.Swipe -> {
                    touch.swiped = id
                    last.consume()
                    var dx = (last.position.x - down.position.x) / density
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        trail = trailWith(trail, TrailPoint(change.uptimeMillis, change.position.x / density, change.position.y / density))
                        dx = (change.position.x - down.position.x) / density
                        val side = Side.of(dx)
                        touch.slide = TileSwipe.follow(dx, host.canSwipe(side)) * density
                        touch.armed = TileSwipe.release(dx, tileWidth, 0f, host::canSwipe) != null
                    }
                    val side = TileSwipe.release(dx, tileWidth, speedX(trail), host::canSwipe)
                    touch.armed = false
                    if (side != null) host.swiped(id, side)
                    val from = touch.slide
                    scope.launch {
                        animate(from, 0f, animationSpec = tween(320, easing = StarEasing)) { v, _ -> touch.slide = v }
                        if (touch.swiped == id) touch.swiped = null
                    }
                }

                null -> {
                    // Held without moving.
                    if (!host.selecting) {
                        host.hold(id)
                        // The rest of this touch is the menu's.
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            if (!change.pressed) break
                        }
                        return@awaitEachGesture
                    }
                    var over = id
                    host.drag(id, id)
                    var at = last.position
                    // The page scrolls under a finger held at its edges, and the
                    // tile under it changes as it does.
                    val edge: Job =
                        scope.launch {
                            var before = 0L
                            while (isActive) {
                                withFrameNanos { now ->
                                    val ms = if (before == 0L) 16f else ((now - before) / 1_000_000f).coerceAtMost(50f)
                                    before = now
                                    val speed = host.edgeSpeed(at)
                                    if (speed != 0f) {
                                        val moved = host.scrollBy(speed * ms / 1000f)
                                        at = at.copy(y = at.y + moved)
                                        val under = host.tileAt(at)?.first
                                        if (under != null && under != over) {
                                            over = under
                                            host.drag(id, under)
                                        }
                                    }
                                }
                            }
                        }
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            if (!change.pressed) break
                            at = change.position
                            val under = host.tileAt(at)?.first
                            if (under != null && under != over) {
                                over = under
                                host.drag(id, under)
                            }
                        }
                    } finally {
                        edge.cancel()
                        host.dragEnded()
                    }
                }
            }
        }
    }

private enum class Early { Tap, Swipe, Gone }
