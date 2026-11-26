package eu.filipnone.starbooru.ui.theme

import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.min

/** The StarBooru roles Material's colour scheme has no place for. */
val LocalStarRoles = staticCompositionLocalOf { starRoles(Seed(), Look.Light) }

object StarBooruTheme {
    /** The palette in use: the web's roles, for what MaterialTheme.colorScheme doesn't cover. */
    val roles: StarRoles
        @Composable
        @ReadOnlyComposable
        get() = LocalStarRoles.current
}

/**
 * The app's theme. With [dynamic] on Android 12 and later the Material roles
 * come from the wallpaper and StarBooru's own roles from its primary's hue;
 * otherwise everything comes from [seed]. [oled] blacks out the dark theme.
 */
@Composable
fun StarBooruTheme(
    dark: Boolean,
    oled: Boolean,
    dynamic: Boolean,
    seed: Seed,
    content: @Composable () -> Unit,
) {
    val look = Look.of(dark, oled)
    val context = LocalContext.current
    val wallpaper =
        when {
            !dynamic || Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> null
            dark -> dynamicDarkColorScheme(context)
            else -> dynamicLightColorScheme(context)
        }
    val (scheme, roles) =
        if (wallpaper != null) {
            val scheme = if (look == Look.Oled) blackedOut(wallpaper) else wallpaper
            scheme to dynamicStarRoles(scheme, look, seed.hue)
        } else {
            remember(seed, look) { starColorScheme(seed, look) to starRoles(seed, look) }
        }
    CompositionLocalProvider(LocalStarRoles provides roles) {
        MaterialTheme(colorScheme = scheme, shapes = StarShapes, typography = StarTypography, content = content)
    }
}

/** Material's colour scheme over the web's roles for [seed]. */
fun starColorScheme(
    seed: Seed,
    look: Look,
): ColorScheme {
    val r = starRoles(seed, look)
    // Snackbars put the inverse primary on the inverse surface, which is the
    // other theme's colour.
    val inverse = starRoles(seed, if (look.dark) Look.Light else Look.Dark)
    val base = if (look.dark) darkColorScheme() else lightColorScheme()
    // tokens.css has no tertiary containers, so these are the primary's in the tertiary's hue.
    val turned = (seed.hue.takeIf { it.isFinite() } ?: Seed.DEFAULT_HUE) + 70.0
    val c = seed.chroma.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 1.0
    val tertiaryContainer = if (look.dark) oklchColor(0.37, min(0.1, c), turned) else oklchColor(0.9, min(0.06, c), turned)
    val onTertiaryContainer = if (look.dark) oklchColor(0.92, min(0.05, c), turned) else oklchColor(0.28, min(0.1, c), turned)
    return base.copy(
        primary = r.primary,
        onPrimary = r.onPrimary,
        primaryContainer = r.primaryContainer,
        onPrimaryContainer = r.onPrimaryContainer,
        inversePrimary = inverse.primary,
        secondary = r.primary,
        onSecondary = r.onPrimary,
        secondaryContainer = r.primaryContainer,
        onSecondaryContainer = r.onPrimaryContainer,
        tertiary = r.tertiary,
        onTertiary = r.onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        background = r.canvas,
        onBackground = r.onSurface,
        surface = r.surface,
        onSurface = r.onSurface,
        surfaceVariant = r.surface3,
        onSurfaceVariant = r.onSurface2,
        surfaceTint = r.primary,
        inverseSurface = r.onSurface,
        inverseOnSurface = r.canvas,
        error = r.danger,
        // The web writes on its danger buttons in the primary's ink.
        onError = r.onPrimary,
        errorContainer = r.dangerContainer,
        onErrorContainer = r.onDangerContainer,
        outline = r.outline,
        outlineVariant = lerp(r.outline, r.surface3, 0.5f),
        // Material puts its own alpha on the scrim.
        scrim = r.scrim.copy(alpha = 1f),
        surfaceBright = if (look.dark) r.surface3 else r.canvas,
        surfaceDim = if (look.dark) r.canvas else r.surface3,
        surfaceContainerLowest = r.canvas,
        surfaceContainerLow = r.surface,
        surfaceContainer = lerp(r.surface, r.surface2, 0.5f),
        surfaceContainerHigh = r.surface2,
        surfaceContainerHighest = r.surface3,
    )
}

/**
 * The web's roles beside a wallpaper [scheme]: those Material has come from
 * it, the rest from its primary's hue. A grey primary makes a grey palette
 * over [fallbackHue].
 */
fun dynamicStarRoles(
    scheme: ColorScheme,
    look: Look,
    fallbackHue: Double,
): StarRoles {
    val hue = hueOf(scheme.primary.toArgb())
    val seed = if (hue != null) Seed(hue = hue.toDouble()) else Seed(hue = fallbackHue, chroma = 0.0)
    return starRoles(seed, look).copy(
        canvas = scheme.background,
        // The web's cards stand on the canvas; Material's surface is the canvas itself.
        surface = scheme.surfaceContainer,
        surface2 = scheme.surfaceContainerHigh,
        surface3 = scheme.surfaceContainerHighest,
        onSurface = scheme.onSurface,
        onSurface2 = scheme.onSurfaceVariant,
        outline = scheme.outline,
        primary = scheme.primary,
        onPrimary = scheme.onPrimary,
        primaryContainer = scheme.primaryContainer,
        onPrimaryContainer = scheme.onPrimaryContainer,
        tertiary = scheme.tertiary,
        onTertiary = scheme.onTertiary,
        danger = scheme.error,
        dangerContainer = scheme.errorContainer,
        onDangerContainer = scheme.onErrorContainer,
    )
}

/** A dark wallpaper scheme on black, each surface a step darker, as OLED does to the web's dark theme. */
fun blackedOut(scheme: ColorScheme): ColorScheme =
    scheme.copy(
        background = Color.Black,
        surface = scheme.surfaceContainerLowest,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = scheme.surfaceContainerLowest,
        surfaceContainer = scheme.surfaceContainerLow,
        surfaceContainerHigh = scheme.surfaceContainer,
        surfaceContainerHighest = scheme.surfaceContainerHigh,
    )

/** The web's corner radii: `--radius-s`, `-m` and `-l`. */
object StarRadius {
    val S = 12.dp
    val M = 16.dp
    val L = 28.dp
}

val StarShapes =
    Shapes(
        small = RoundedCornerShape(StarRadius.S),
        medium = RoundedCornerShape(StarRadius.M),
        large = RoundedCornerShape(StarRadius.L),
        extraLarge = RoundedCornerShape(StarRadius.L),
    )

/** Material's type in the system's Roboto, with page titles a little heavier. */
val StarTypography = Typography().run { copy(titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold)) }

/** `--ease`: quick to start, gentle to land. */
val StarEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** `--ease-spring`: overshoots a little and settles back. */
val StarSpringEasing: Easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

/** The web's durations, in milliseconds. */
object StarDuration {
    const val FAST = 150
    const val MED = 250
    const val SLOW = 420
    const val FADE = 640
}
