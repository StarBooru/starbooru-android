package eu.filipnone.starbooru.ui.common

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
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
 * A post's picture moving between its tile and the viewer. Its box changes
 * shape on the way, the picture filling it, as the reference's did.
 */
@Composable
fun Modifier.sharedPost(
    id: Long,
    corner: Dp,
): Modifier {
    val shared = LocalSharedTransition.current ?: return this
    if (LocalReducedMotion.current) return this
    // Only the pages of the NavDisplay call this, which always provide it.
    val scope: AnimatedVisibilityScope = LocalNavAnimatedContentScope.current
    return with(shared) {
        this@sharedPost.sharedBounds(
            sharedContentState = rememberSharedContentState("post-$id"),
            animatedVisibilityScope = scope,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(corner)),
        )
    }
}
