package eu.filipnone.starbooru.ui.common

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope

/** The scope shared elements move in, around the whole navigation. */
val LocalSharedTransition = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** The post the viewer shows last, so the gallery can bring its tile into sight. */
val LocalViewerFocus = staticCompositionLocalOf<MutableState<Long?>> { mutableStateOf(null) }

/** Whether motion is off, by the system's Remove animations. */
val LocalReducedMotion = staticCompositionLocalOf { false }

/**
 * Whether the viewer is on screen, coming, shown or going. A tile's picture
 * only moves to or from it: a new search shows some of the same posts, and
 * their tiles stay put. [opening] says a viewer is on top before its first
 * picture is drawn.
 */
class OnStage(private val opening: () -> Boolean = { false }) {
    /** The viewer's pictures drawn now, still there while it leaves. */
    var pictures by mutableIntStateOf(0)
        internal set

    val active: Boolean get() = opening() || pictures > 0
}

val LocalOnStage = staticCompositionLocalOf { OnStage() }

/**
 * A post's picture moving between its tile and the viewer. Its box changes
 * shape on the way, the picture filling it, as the reference's did. One
 * not [enabled] stays where it is: the viewer's pictures beside the one on
 * show. The viewer's own pictures say so with [onStage].
 */
@Composable
fun Modifier.sharedPost(
    id: Long,
    corner: Dp,
    enabled: Boolean = true,
    onStage: Boolean = false,
): Modifier {
    val shared = LocalSharedTransition.current ?: return this
    if (LocalReducedMotion.current) return this
    // Only the pages of the NavDisplay call this, which always provide it.
    val scope: AnimatedVisibilityScope = LocalNavAnimatedContentScope.current
    val stage = LocalOnStage.current
    if (onStage) {
        DisposableEffect(stage) {
            stage.pictures++
            onDispose { stage.pictures-- }
        }
    }
    // Read as the transition runs: a picture beside one turns into the one on show.
    val on = rememberUpdatedState(enabled)
    val config =
        remember(stage, onStage) {
            object : SharedTransitionScope.SharedContentConfig {
                override val SharedTransitionScope.SharedContentState.isEnabled: Boolean
                    get() = on.value && (onStage || stage.active)
            }
        }
    return with(shared) {
        this@sharedPost.sharedBounds(
            sharedContentState = rememberSharedContentState("post-$id", config),
            animatedVisibilityScope = scope,
            // The tile and the stage show the same picture, cropped the same
            // way: both stay opaque, so it doesn't fade halfway.
            enter = EnterTransition.None,
            exit = ExitTransition.None,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(corner)),
        )
    }
}
