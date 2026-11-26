package eu.filipnone.starbooru.ui.frame

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import eu.filipnone.starbooru.ui.theme.StarRoles

/** What frosted glass blurs: the content under the bars, or the stage under the viewer's controls. */
val LocalGlassSource = staticCompositionLocalOf<HazeState?> { null }

/** Effects cut back, as the web's Reduced: plain bars instead of frosted glass. */
val LocalPlainGlass = staticCompositionLocalOf { false }

/**
 * A floating surface: the bars, the viewer's controls and the sign-in card,
 * as the web draws them. Frosted (the content behind blurred 24 dp and
 * tinted) on Android 12 and later; plain, in the same colour without its
 * see-through, before that or with effects reduced.
 */
@Composable
fun Modifier.glass(
    shape: Shape,
    roles: StarRoles,
): Modifier {
    val source = LocalGlassSource.current
    val frosted = source != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !LocalPlainGlass.current
    val style =
        remember(roles) {
            HazeBlurStyle {
                blurRadius(24.dp)
                noiseFactor(0f)
                backgroundColor(roles.canvas)
                colorEffects(listOf(HazeColorEffect.tint(roles.glass)))
            }
        }
    val base =
        this
            .shadow(6.dp, shape, ambientColor = roles.shadow, spotColor = roles.shadow)
            .clip(shape)
    val filled =
        if (frosted && source != null) {
            base.hazeBlur(input = HazeInput.Sources(source), style = style)
        } else {
            base.background(roles.glass.copy(alpha = 1f))
        }
    return filled.border(1.dp, roles.glassLine, shape)
}
