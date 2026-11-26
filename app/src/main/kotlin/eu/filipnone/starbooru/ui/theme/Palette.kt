package eu.filipnone.starbooru.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlin.math.min

/**
 * What the palette is built from, in OKLCH. A preset is a hue alone, at full
 * colour; a picked colour also brings its own chroma and lightness
 * ([Seed.picked]).
 */
@Immutable
data class Seed(
    /** OKLCH hue in degrees. */
    val hue: Double = DEFAULT_HUE,
    /** A picked colour's chroma: 0 for a grey, 1 (or anything past [SWATCH_CHROMA]) for full colour. */
    val chroma: Double = 1.0,
    /** A picked colour's lightness, 0 to 1. Only a muted colour's primary follows it. */
    val lightness: Double = 0.5,
) {
    /** How colourful the seed is, from 0 for a grey to 1 for a preset: `--k` in the web's tokens.css. */
    val colourful: Double get() = min(1.0, chroma / SWATCH_CHROMA)

    companion object {
        const val DEFAULT_HUE = 285.0

        /** A preset swatch of [hue] degrees. */
        fun preset(hue: Int): Seed = Seed(hue = hue.toDouble())

        /**
         * A colour picked as the accent. A grey has no hue of its own, so it
         * keeps [hueOver], which only the tag categories and the aurora show.
         */
        fun picked(argb: Int, hueOver: Double = DEFAULT_HUE): Seed {
            val lab = oklabOf(argb)
            val chroma = lab.chroma
            return Seed(
                hue = hueOf(argb)?.toDouble() ?: hueOver,
                chroma = if (chroma < MIN_CHROMA) 0.0 else thousandths(chroma),
                lightness = thousandths(lab.l.coerceIn(0.0, 1.0)),
            )
        }

        // Three decimals, as the web rounds them for its stylesheet.
        private fun thousandths(x: Double): Double = Math.round(x * 1000.0) / 1000.0
    }
}

/** The accent presets as OKLCH hues, in the order Settings offers them. */
val SWATCHES: List<Int> = listOf(285, 330, 20, 65, 140, 190, 245, 0)

/** The greys offered beside the colour wheel: white, mid grey and black. */
val NEUTRALS: List<Int> = listOf(0xFFFFFFFF.toInt(), 0xFF808080.toInt(), 0xFF000000.toInt())

/** The lightness and chroma a preset swatch is painted in. */
const val SWATCH_LIGHTNESS = 0.6

/** A swatch's chroma, and the one from which a picked colour counts as fully colourful. */
const val SWATCH_CHROMA = 0.16

/** The colour a preset of [hue] is painted in, as opaque ARGB. */
fun swatchArgb(hue: Int): Int = oklchToArgb(SWATCH_LIGHTNESS, SWATCH_CHROMA, hue.toDouble())

/** Which of the web's three palettes: OLED is the dark one with black behind everything. */
enum class Look {
    Light,
    Dark,
    Oled,
    ;

    val dark: Boolean get() = this != Light

    companion object {
        /** OLED only changes the dark palette. */
        fun of(dark: Boolean, oled: Boolean): Look =
            when {
                !dark -> Light
                oled -> Oled
                else -> Dark
            }
    }
}

/** Every colour role of the web's `style/tokens.css`, for one seed and look. */
@Immutable
data class StarRoles(
    val canvas: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val onSurface: Color,
    val onSurface2: Color,
    val outline: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    /** Tag categories: the text and edge of their chips. */
    val catArtist: Color,
    val catCharacter: Color,
    val catCopyright: Color,
    val catMeta: Color,
    val danger: Color,
    val dangerContainer: Color,
    val onDangerContainer: Color,
    val warnContainer: Color,
    val onWarnContainer: Color,
    val ok: Color,
    /** The bars and floating controls, see-through. */
    val glass: Color,
    /** The hairline around a floating capsule. */
    val glassLine: Color,
    /** A floating capsule's lit top edge. */
    val glassEdge: Color,
    /** Behind the viewer's picture. */
    val stage: Color,
    /** Controls over media, see-through, and what goes on them. */
    val scrim: Color,
    val onScrim: Color,
    /** The third light of the sign-in screen's aurora, see-through. */
    val aurora: Color,
    /** The tint of shadows, opaque: the elevation sets how dark they are. */
    val shadow: Color,
)

/** The palette tokens.css builds from [seed] for [look]. */
fun starRoles(
    seed: Seed,
    look: Look,
): StarRoles {
    val t = Tint(seed)
    val base =
        when (look) {
            Look.Light -> light(t)
            Look.Dark, Look.Oled -> dark(t)
        }
    if (look != Look.Oled) return base
    // Black behind everything, with surfaces that rise just enough to tell
    // cards and sheets apart, and glass dark enough to melt into the black.
    return base.copy(
        canvas = BLACK,
        surface = t.neutral(0.17, 0.014),
        surface2 = t.neutral(0.21, 0.018),
        surface3 = t.neutral(0.26, 0.022),
        glass = t.neutral(0.1, 0.01, alpha = 0.8f),
        stage = BLACK,
    )
}

private fun light(t: Tint) =
    StarRoles(
        canvas = t.neutral(0.985, 0.006),
        surface = t.neutral(0.965, 0.014),
        surface2 = t.neutral(0.94, 0.02),
        surface3 = t.neutral(0.905, 0.028),
        onSurface = t.neutral(0.23, 0.02),
        onSurface2 = t.neutral(0.43, 0.025),
        outline = t.neutral(0.8, 0.025),
        // Between 0.2 and 0.5 it reads on the canvas and white reads on it: a
        // grey goes there by its own lightness, black darkest, white at 0.5.
        primary = t.primary(own = 0.5, low = 0.2, span = 0.3, chroma = 0.17),
        onPrimary = t.accent(0.99, 0.01),
        primaryContainer = t.accent(0.9, 0.06),
        onPrimaryContainer = t.accent(0.28, 0.1),
        tertiary = t.accent(0.62, 0.15, turn = 70.0),
        onTertiary = t.accent(0.2, 0.05, turn = 70.0),
        catArtist = t.category(0.5, 0.16, turn = 110.0),
        catCharacter = t.category(0.5, 0.14, turn = 215.0),
        catCopyright = t.category(0.5, 0.16, turn = 40.0),
        catMeta = t.category(0.5, 0.12, turn = 300.0),
        danger = oklchColor(0.53, 0.2, 27.0),
        dangerContainer = oklchColor(0.93, 0.05, 27.0),
        onDangerContainer = oklchColor(0.36, 0.13, 27.0),
        warnContainer = oklchColor(0.93, 0.07, 85.0),
        onWarnContainer = oklchColor(0.36, 0.08, 70.0),
        ok = oklchColor(0.5, 0.13, 150.0),
        glass = t.neutral(0.975, 0.012, alpha = 0.78f),
        glassLine = t.neutral(0.2, 0.04, alpha = 0.1f),
        glassEdge = oklchColor(1.0, 0.0, 0.0, alpha = 0.6f),
        stage = t.neutral(0.2, 0.015),
        scrim = t.neutral(0.12, 0.01, alpha = 0.55f),
        onScrim = t.neutral(0.97, 0.01),
        aurora = t.accent(0.75, 0.14, turn = -60.0, alpha = 0.35f),
        shadow = t.neutral(0.2, 0.04),
    )

private fun dark(t: Tint) =
    StarRoles(
        canvas = t.neutral(0.165, 0.012),
        surface = t.neutral(0.205, 0.016),
        surface2 = t.neutral(0.245, 0.02),
        surface3 = t.neutral(0.29, 0.024),
        onSurface = t.neutral(0.93, 0.012),
        onSurface2 = t.neutral(0.77, 0.02),
        outline = t.neutral(0.42, 0.025),
        // Between 0.68 and 0.96, with dark text on it: black goes to 0.68,
        // white to 0.96.
        primary = t.primary(own = 0.8, low = 0.68, span = 0.28, chroma = 0.11),
        onPrimary = t.accent(0.25, 0.09),
        primaryContainer = t.accent(0.37, 0.1),
        onPrimaryContainer = t.accent(0.92, 0.05),
        tertiary = t.accent(0.75, 0.12, turn = 70.0),
        onTertiary = t.accent(0.2, 0.05, turn = 70.0),
        catArtist = t.category(0.8, 0.12, turn = 110.0),
        catCharacter = t.category(0.8, 0.11, turn = 215.0),
        catCopyright = t.category(0.8, 0.12, turn = 40.0),
        catMeta = t.category(0.8, 0.09, turn = 300.0),
        danger = oklchColor(0.75, 0.15, 27.0),
        dangerContainer = oklchColor(0.36, 0.1, 27.0),
        onDangerContainer = oklchColor(0.92, 0.04, 27.0),
        warnContainer = oklchColor(0.37, 0.07, 80.0),
        onWarnContainer = oklchColor(0.93, 0.05, 85.0),
        ok = oklchColor(0.78, 0.13, 150.0),
        glass = t.neutral(0.205, 0.016, alpha = 0.82f),
        glassLine = oklchColor(1.0, 0.0, 0.0, alpha = 0.08f),
        glassEdge = oklchColor(1.0, 0.0, 0.0, alpha = 0.14f),
        stage = t.neutral(0.12, 0.01),
        // The same in both themes: the stage is dark either way.
        scrim = t.neutral(0.12, 0.01, alpha = 0.55f),
        onScrim = t.neutral(0.97, 0.01),
        aurora = t.accent(0.75, 0.14, turn = -60.0, alpha = 0.35f),
        shadow = BLACK,
    )

private val BLACK = oklchColor(0.0, 0.0, 0.0)

/** How each kind of role takes the seed, as tokens.css writes it. */
private class Tint(seed: Seed) {
    val hue = seed.hue.takeIf { it.isFinite() } ?: Seed.DEFAULT_HUE

    /** `--c`: the seed's own chroma. */
    val chroma = seed.chroma.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 1.0

    /** `--l`: the seed's own lightness. */
    val lightness = seed.lightness.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: 0.5

    /** `--k`, and `--pull`, how far the primary moves to the seed's lightness. */
    val k = min(1.0, chroma / SWATCH_CHROMA)
    val pull = 1.0 - k

    /** A neutral role: its chroma scales with how colourful the seed is. */
    fun neutral(l: Double, c: Double, alpha: Float = 1f) = oklchColor(l, c * k, hue, alpha)

    /** An accent role: the seed's own chroma where that is less. */
    fun accent(l: Double, c: Double, turn: Double = 0.0, alpha: Float = 1f) = oklchColor(l, min(c, chroma), hue + turn, alpha)

    /** A tag category: the same chroma with any seed, a grey too. */
    fun category(l: Double, c: Double, turn: Double) = oklchColor(l, c, hue + turn)

    /** The primary: its own lightness, pulled towards `low + span * --l` as the seed greys. */
    fun primary(
        own: Double,
        low: Double,
        span: Double,
        chroma: Double,
    ) = oklchColor(own + pull * (low + span * lightness - own), min(chroma, this.chroma), hue)
}
