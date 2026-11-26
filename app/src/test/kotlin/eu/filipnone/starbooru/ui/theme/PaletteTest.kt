package eu.filipnone.starbooru.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The web's `src/theme.rs` tests, and its palette's, against these roles. */
class PaletteTest {
    private fun rgb(
        r: Int,
        g: Int,
        b: Int,
    ): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private fun hex(argb: Int): String = "#%06x".format(argb and 0xFFFFFF)

    private fun hex(color: Color): String = hex(color.toArgb())

    private val looks = Look.entries

    // ---------- colour maths ----------

    @Test
    fun knownColoursConvert() {
        assertEquals("#ffffff", hex(oklchToArgb(1.0, 0.0, 0.0)))
        assertEquals("#000000", hex(oklchToArgb(0.0, 0.0, 0.0)))
        // sRGB's red, green and blue in OKLCH.
        assertEquals("#ff0000", hex(oklchToArgb(0.627955, 0.257683, 29.233885)))
        assertEquals("#00ff00", hex(oklchToArgb(0.866440, 0.294827, 142.495339)))
        assertEquals("#0000ff", hex(oklchToArgb(0.452014, 0.313214, 264.052021)))
        // Out of gamut clips instead of wrapping round.
        assertEquals("#ffffff", hex(oklchToArgb(1.2, 0.0, 0.0)))
        // And back, lightness first.
        val red = oklabOf(rgb(0xff, 0, 0))
        assertEquals(0.627955, red.l, 1e-4)
        assertEquals(0.257683, red.chroma, 1e-4)
        assertTrue(oklabOf(rgb(0xff, 0xff, 0xff)).l > 0.999)
        // Hostile numbers come out as a colour all the same.
        assertEquals("#000000", hex(oklchToArgb(Double.NaN, 0.0, 0.0)))
    }

    @Test
    fun theCanvasFollowsTheTheme() {
        val violet = Seed.preset(285)
        val light = hex(starRoles(violet, Look.Light).canvas)
        val dark = hex(starRoles(violet, Look.Dark).canvas)
        // A near-white and a near-black, tinted by the hue.
        assertEquals("#f9fafe", light)
        assertEquals("#0e0e13", dark)
        assertNotEquals(light, hex(starRoles(Seed.preset(120), Look.Light).canvas))
        // A grey takes the tint away.
        val grey = Seed.picked(rgb(0x80, 0x80, 0x80))
        assertEquals("#fafafa", hex(starRoles(grey, Look.Light).canvas))
        assertEquals("#0e0e0e", hex(starRoles(grey, Look.Dark).canvas))
    }

    @Test
    fun theCanvasIsBlackInOled() {
        val violet = Seed.preset(285)
        assertEquals("#000000", hex(starRoles(violet, Look.Oled).canvas))
        assertEquals("#000000", hex(starRoles(violet, Look.Oled).stage))
        // Light has no black to show.
        assertEquals(Look.Light, Look.of(dark = false, oled = true))
        assertEquals(Look.Oled, Look.of(dark = true, oled = true))
        assertEquals(Look.Dark, Look.of(dark = true, oled = false))
    }

    @Test
    fun findsTheHueOfAColour() {
        fun near(
            argb: Int,
            want: Int,
        ) {
            val got = hueOf(argb)
            assertNotNull(hex(argb), got)
            assertTrue("${hex(argb)}: $got vs $want", abs((got ?: -9) - want) <= 1)
        }
        near(rgb(0xff, 0, 0), 29)
        near(rgb(0, 0xff, 0), 142)
        near(rgb(0, 0, 0xff), 264)
        near(rgb(0x67, 0x50, 0xa4), 294)
        // Every hue of a saturated wheel lands in 0 until 360.
        for (i in 0..255) {
            val hue = hueOf(rgb(0xff, 0, i))
            assertTrue("$i: $hue", hue != null && hue in 0 until 360)
        }
    }

    @Test
    fun greysHaveNoHueAndNoChroma() {
        for (grey in listOf(rgb(0, 0, 0), rgb(0x80, 0x80, 0x80), rgb(0xff, 0xff, 0xff), rgb(0x7f, 0x80, 0x81))) {
            assertNull(hex(grey), hueOf(grey))
            assertEquals(hex(grey), 0.0, Seed.picked(grey).chroma, 0.0)
        }
        assertEquals(0.0, Seed.picked(rgb(0, 0, 0)).lightness, 0.0)
        assertEquals(1.0, Seed.picked(rgb(0xff, 0xff, 0xff)).lightness, 0.0)
        assertEquals(0.6, Seed.picked(rgb(0x80, 0x80, 0x80)).lightness, 0.0)
    }

    @Test
    fun aPickedColourSetsHowColourfulThePaletteIs() {
        // A preset, and anything as colourful as its swatch, is all colour.
        assertEquals(1.0, Seed.preset(285).colourful, 0.0)
        assertEquals(1.0, Seed.picked(rgb(0xff, 0, 0)).colourful, 0.0)
        assertEquals(1.0, Seed.picked(rgb(0, 0, 0x80)).colourful, 0.0)
        // A muted colour is calmer, a grey has none at all.
        val sage = Seed.picked(rgb(0x7a, 0x8a, 0x6e)).colourful
        assertTrue("$sage", sage >= 0.2 && sage < 0.4)
        assertEquals(0.0, Seed.picked(rgb(0x80, 0x80, 0x80)).colourful, 0.0)
        // The numbers the web's stylesheet is given.
        assertEquals(Seed(hue = 29.0, chroma = 0.258, lightness = 0.628), Seed.picked(rgb(0xff, 0, 0)))
        assertEquals(Seed(hue = 285.0, chroma = 0.0, lightness = 1.0), Seed.picked(rgb(0xff, 0xff, 0xff)))
        // A grey keeps the hue it was picked over.
        assertEquals(140.0, Seed.picked(rgb(0, 0, 0), hueOver = 140.0).hue, 0.0)
    }

    @Test
    fun aPresetIsDrawnInItsOwnHue() {
        assertEquals(listOf(285, 330, 20, 65, 140, 190, 245, 0), SWATCHES)
        assertEquals(0.6, SWATCH_LIGHTNESS, 0.0)
        assertEquals(0.16, SWATCH_CHROMA, 0.0)
        // Some fall outside sRGB, and clipping them moves the hue a little.
        for (hue in SWATCHES) {
            val got = hueOf(swatchArgb(hue)) ?: -100
            val apart = abs(got - hue).let { min(it, 360 - it) }
            assertTrue("$hue: $got", apart <= 10)
        }
        assertEquals(listOf("#ffffff", "#808080", "#000000"), NEUTRALS.map { hex(it) })
    }

    @Test
    fun marksGoDarkOnPaleColours() {
        assertTrue(darkInk(rgb(0xff, 0xff, 0xff)))
        assertTrue(darkInk(rgb(0xff, 0xe0, 0x00)))
        assertTrue(!darkInk(rgb(0, 0, 0)))
        assertTrue(!darkInk(rgb(0x20, 0x60, 0xe0)))
    }

    // ---------- the palette, as tokens.css builds it ----------

    /** A role of the web's palette test: its lightness, chroma and turn from the hue at full colour. */
    private class Role(val name: String, val l: Double, val c: Double, val turn: Double = 0.0)

    private val lightRoles =
        listOf(
            Role("canvas", 0.985, 0.006),
            Role("surface", 0.965, 0.014),
            Role("surface-2", 0.94, 0.02),
            Role("surface-3", 0.905, 0.028),
            Role("on-surface", 0.23, 0.02),
            Role("on-surface-2", 0.43, 0.025),
            Role("outline", 0.8, 0.025),
            Role("primary", 0.5, 0.17),
            Role("on-primary", 0.99, 0.01),
            Role("primary-container", 0.9, 0.06),
            Role("on-primary-container", 0.28, 0.1),
            Role("cat-artist", 0.5, 0.16, 110.0),
            Role("cat-character", 0.5, 0.14, 215.0),
            Role("cat-copyright", 0.5, 0.16, 40.0),
            Role("cat-meta", 0.5, 0.12, 300.0),
        )

    private val darkRoles =
        listOf(
            Role("canvas", 0.165, 0.012),
            Role("surface", 0.205, 0.016),
            Role("surface-2", 0.245, 0.02),
            Role("surface-3", 0.29, 0.024),
            Role("on-surface", 0.93, 0.012),
            Role("on-surface-2", 0.77, 0.02),
            Role("outline", 0.42, 0.025),
            Role("primary", 0.8, 0.11),
            Role("on-primary", 0.25, 0.09),
            Role("primary-container", 0.37, 0.1),
            Role("on-primary-container", 0.92, 0.05),
            Role("cat-artist", 0.8, 0.12, 110.0),
            Role("cat-character", 0.8, 0.11, 215.0),
            Role("cat-copyright", 0.8, 0.12, 40.0),
            Role("cat-meta", 0.8, 0.09, 300.0),
        )

    /** The roles by the names tokens.css gives them, as opaque ARGB. */
    private fun StarRoles.byName(): Map<String, Int> =
        mapOf(
            "canvas" to canvas,
            "surface" to surface,
            "surface-2" to surface2,
            "surface-3" to surface3,
            "on-surface" to onSurface,
            "on-surface-2" to onSurface2,
            "outline" to outline,
            "primary" to primary,
            "on-primary" to onPrimary,
            "primary-container" to primaryContainer,
            "on-primary-container" to onPrimaryContainer,
            "tertiary" to tertiary,
            "on-tertiary" to onTertiary,
            "cat-artist" to catArtist,
            "cat-character" to catCharacter,
            "cat-copyright" to catCopyright,
            "cat-meta" to catMeta,
            "glass" to glass,
            "glass-line" to glassLine,
            "stage" to stage,
            "scrim" to scrim,
            "on-scrim" to onScrim,
            "aurora" to aurora,
            "shadow" to shadow,
        ).mapValues { (_, color) -> color.copy(alpha = 1f).toArgb() }

    private fun palette(
        look: Look,
        seed: Seed,
    ): Map<String, Int> = starRoles(seed, look).byName()

    private fun Map<String, Int>.role(name: String): Int = this[name] ?: error("no --$name")

    @Test
    fun presetsLookAsTheyAlwaysHave() {
        // Before picked colours had a say, every role was its own lightness
        // and chroma in the preset's hue.
        for (hue in 0 until 360 step 5) {
            for ((look, roles) in listOf(Look.Light to lightRoles, Look.Dark to darkRoles)) {
                val colours = palette(look, Seed.preset(hue))
                for (role in roles) {
                    val was = oklchToArgb(role.l, role.c, hue + role.turn)
                    assertEquals("--${role.name} at $hue in $look", hex(was), hex(colours.role(role.name)))
                }
            }
        }
    }

    @Test
    fun theOtherRolesFollowTokensCss() {
        val r = starRoles(Seed.preset(285), Look.Light)
        assertEquals(hex(oklchToArgb(0.62, 0.15, 355.0)), hex(r.tertiary))
        assertEquals(hex(oklchToArgb(0.53, 0.2, 27.0)), hex(r.danger))
        assertEquals(hex(oklchToArgb(0.975, 0.012, 285.0)), hex(r.glass.copy(alpha = 1f)))
        assertEquals(0.78f, r.glass.alpha, 0.01f)
        assertEquals(0.55f, r.scrim.alpha, 0.01f)
        assertEquals(0.35f, r.aurora.alpha, 0.01f)
        assertEquals(hex(oklchToArgb(0.75, 0.14, 225.0)), hex(r.aurora.copy(alpha = 1f)))
        val d = starRoles(Seed.preset(285), Look.Dark)
        assertEquals(hex(oklchToArgb(0.75, 0.12, 355.0)), hex(d.tertiary))
        assertEquals(0.08f, d.glassLine.alpha, 0.01f)
        assertEquals("#ffffff", hex(d.glassLine.copy(alpha = 1f)))
        // OLED keeps the dark theme's, but for the surfaces, glass and stage.
        val o = starRoles(Seed.preset(285), Look.Oled)
        assertEquals(
            d.copy(canvas = o.canvas, surface = o.surface, surface2 = o.surface2, surface3 = o.surface3, glass = o.glass, stage = o.stage),
            o,
        )
        assertEquals(hex(oklchToArgb(0.17, 0.014, 285.0)), hex(o.surface))
        assertEquals(0.8f, o.glass.alpha, 0.01f)
    }

    @Test
    fun aGreyMakesAPaletteWithoutATint() {
        for (argb in NEUTRALS) {
            val grey = Seed.picked(argb, hueOver = 285.0)
            for (look in looks) {
                for ((name, colour) in palette(look, grey)) {
                    if (name.startsWith("cat-")) continue
                    val r = colour shr 16 and 0xFF
                    val g = colour shr 8 and 0xFF
                    val b = colour and 0xFF
                    val spread = maxOf(r, g, b) - minOf(r, g, b)
                    assertTrue("${hex(argb)} $look --$name: $r $g $b", spread <= 1)
                }
            }
        }
    }

    @Test
    fun whiteAndBlackAreToldApartAndStayReadable() {
        fun primary(
            look: Look,
            argb: Int,
        ) = hex(starRoles(Seed.picked(argb), look).primary)
        // Light can't show a white primary on its white canvas: white is the
        // lightest grey that white text still reads on, black is near black.
        assertEquals("#636363", primary(Look.Light, rgb(0xff, 0xff, 0xff)))
        assertEquals("#424242", primary(Look.Light, rgb(0x80, 0x80, 0x80)))
        assertEquals("#161616", primary(Look.Light, rgb(0, 0, 0)))
        // In dark, the other way round: black is the darkest grey that dark
        // text reads on, and white is nearly white.
        assertEquals("#989898", primary(Look.Dark, rgb(0, 0, 0)))
        assertEquals("#cdcdcd", primary(Look.Dark, rgb(0x80, 0x80, 0x80)))
        assertEquals("#f2f2f2", primary(Look.Dark, rgb(0xff, 0xff, 0xff)))
    }

    /** `starbooru_core::color::wheel_color`: the colour picker's wheel, hues clockwise from red at the top. */
    private fun wheelColor(
        x: Double,
        y: Double,
    ): Int {
        fun remEuclid(
            a: Double,
            b: Double,
        ) = (a % b).let { if (it < 0) it + b else it }
        val saturation = min(hypot(x, y), 1.0)
        val hue = remEuclid(atan2(x, -y) * (180.0 / PI), 360.0) / 60.0
        val rising = 1.0 - abs(remEuclid(hue, 2.0) - 1.0)
        val channels =
            when (hue.toInt()) {
                0 -> listOf(1.0, rising, 0.0)
                1 -> listOf(rising, 1.0, 0.0)
                2 -> listOf(0.0, 1.0, rising)
                3 -> listOf(0.0, rising, 1.0)
                4 -> listOf(rising, 0.0, 1.0)
                else -> listOf(1.0, 0.0, rising)
            }.map { c -> Math.round(255.0 * (1.0 - saturation * (1.0 - c))).toInt() }
        return rgb(channels[0], channels[1], channels[2])
    }

    /**
     * Every preset, and picks from every corner of what the picker makes:
     * greys, the wheel's edge and halfway in, and a grid of hex colours.
     */
    private fun accents(): List<Seed> {
        val all = (0 until 360 step 5).map { Seed.preset(it) }.toMutableList()
        val picks = (0..255 step 17).map { rgb(it, it, it) }.toMutableList()
        for (step in 0 until 36) {
            val angle = step * 10.0 * (PI / 180.0)
            for (radius in listOf(1.0, 0.5, 0.2)) picks += wheelColor(radius * sin(angle), -radius * cos(angle))
        }
        val levels = listOf(0, 0x40, 0x80, 0xc0, 0xff)
        for (r in levels) for (g in levels) for (b in levels) picks += rgb(r, g, b)
        // Muted, pastel and dark ones.
        picks +=
            listOf(
                rgb(0x7a, 0x8a, 0x6e),
                rgb(0x8a, 0x7f, 0x99),
                rgb(0xff, 0xc0, 0xc0),
                rgb(0xc0, 0xe0, 0xff),
                rgb(0x40, 0x00, 0x00),
                rgb(0x00, 0x00, 0x80),
                rgb(0x00, 0xa0, 0xa0),
            )
        return all + picks.map { Seed.picked(it) }
    }

    /** Text or an edge in [on] over [under], which must stand [least] apart. */
    private data class Pairing(val what: String, val on: String, val under: String, val least: Double)

    @Test
    fun everyAccentStaysReadable() {
        val pairs =
            listOf(
                Pairing("text on the primary", "on-primary", "primary", 4.5),
                Pairing("the primary as text", "primary", "canvas", 4.5),
                Pairing("a focus ring on a card", "primary", "surface", 3.0),
                Pairing("a switch's track", "primary", "surface-3", 3.0),
                Pairing("a chosen chip's edge", "primary", "primary-container", 3.0),
                Pairing("a chosen chip", "on-primary-container", "primary-container", 4.5),
                Pairing("a chip", "on-surface", "surface-2", 4.5),
                Pairing("a switch that's off", "on-surface-2", "surface-3", 3.0),
                // The presets' own least, in light.
                Pairing("an artist's chip", "cat-artist", "surface-2", 4.0),
                Pairing("a character's chip", "cat-character", "surface-2", 4.0),
                Pairing("a copyright's chip", "cat-copyright", "surface-2", 4.0),
                Pairing("a meta tag's chip", "cat-meta", "surface-2", 4.0),
            )
        for (seed in accents()) {
            for (look in looks) {
                val colours = palette(look, seed)
                for ((what, on, under, least) in pairs) {
                    val fg = colours.role(on)
                    val bg = colours.role(under)
                    val ratio = contrastRatio(fg, bg)
                    assertTrue("$what in $look, $seed: $ratio (${hex(fg)} on ${hex(bg)})", ratio >= least)
                }
            }
        }
    }

    @Test
    fun oledIsBlackWithSurfacesThatStandApart() {
        fun Map<String, Int>.step(
            a: String,
            b: String,
        ) = oklabOf(role(a)).l - oklabOf(role(b)).l
        for (seed in accents()) {
            val oled = palette(Look.Oled, seed)
            val dark = palette(Look.Dark, seed)
            assertEquals("#000000", hex(oled.role("canvas")))
            // A card stands out from the black, and a chip or a track from
            // its card, by as much as the dark theme's do.
            assertTrue("$seed", oled.step("surface", "canvas") >= dark.step("surface", "canvas"))
            assertTrue("$seed", oled.step("surface-2", "surface") >= 0.035)
            assertTrue("$seed", oled.step("surface-3", "surface-2") >= 0.035)
            // Edges show on the cards as well as in dark.
            assertTrue(
                "$seed",
                contrastRatio(oled.role("outline"), oled.role("surface")) >=
                    contrastRatio(dark.role("outline"), dark.role("surface")),
            )
        }
    }

    // ---------- Material's roles ----------

    @Test
    fun materialsRolesComeFromThePalette() {
        for (look in looks) {
            val seed = Seed.preset(140)
            val r = starRoles(seed, look)
            val s = starColorScheme(seed, look)
            assertEquals(r.canvas, s.background)
            assertEquals(r.surface, s.surface)
            assertEquals(r.canvas, s.surfaceContainerLowest)
            assertEquals(r.surface, s.surfaceContainerLow)
            assertEquals(r.surface2, s.surfaceContainerHigh)
            assertEquals(r.surface3, s.surfaceContainerHighest)
            assertEquals(r.onSurface, s.onSurface)
            assertEquals(r.onSurface2, s.onSurfaceVariant)
            assertEquals(r.outline, s.outline)
            assertEquals(r.primary, s.primary)
            assertEquals(r.onPrimary, s.onPrimary)
            assertEquals(r.primaryContainer, s.primaryContainer)
            assertEquals(r.onPrimaryContainer, s.onPrimaryContainer)
            assertEquals(r.primaryContainer, s.secondaryContainer)
            assertEquals(r.tertiary, s.tertiary)
            assertEquals(r.danger, s.error)
            assertEquals(r.dangerContainer, s.errorContainer)
            assertEquals(r.onSurface, s.inverseSurface)
            assertEquals(r.canvas, s.inverseOnSurface)
            assertEquals(1f, s.scrim.alpha, 0f)
            // The container between the card and the chip sits between them.
            val container = oklabOf(s.surfaceContainer.toArgb()).l
            val lo = oklabOf(r.surface.toArgb()).l
            val hi = oklabOf(r.surface2.toArgb()).l
            assertTrue("$look", container in minOf(lo, hi)..maxOf(lo, hi))
        }
        assertEquals(Color.Black, starColorScheme(Seed(), Look.Oled).background)
        assertEquals(starRoles(Seed(), Look.Dark).primary, starColorScheme(Seed(), Look.Light).inversePrimary)
    }

    @Test
    fun wallpaperColoursKeepTheirRolesAndLendTheirHue() {
        val green = oklchColor(0.5, 0.12, 140.0)
        val scheme = lightColorScheme(primary = green)
        val roles = dynamicStarRoles(scheme, Look.Light, fallbackHue = 285.0)
        assertEquals(scheme.background, roles.canvas)
        assertEquals(scheme.surfaceContainerHigh, roles.surface2)
        assertEquals(scheme.primary, roles.primary)
        assertEquals(scheme.error, roles.danger)
        // What Material has no place for takes the wallpaper's hue.
        val hue = hueOf(green.toArgb()) ?: error("green has a hue")
        val own = starRoles(Seed.preset(hue), Look.Light)
        assertEquals(own.catArtist, roles.catArtist)
        assertEquals(own.glass, roles.glass)
        // A grey wallpaper makes grey extras.
        val grey = dynamicStarRoles(lightColorScheme(primary = oklchColor(0.5, 0.0, 0.0)), Look.Light, 285.0)
        assertEquals(starRoles(Seed(hue = 285.0, chroma = 0.0), Look.Light).stage, grey.stage)
    }

    @Test
    fun oledBlacksOutAWallpaperScheme() {
        val scheme = darkColorScheme()
        val black = blackedOut(scheme)
        assertEquals(Color.Black, black.background)
        assertEquals(Color.Black, black.surfaceContainerLowest)
        assertEquals(scheme.surfaceContainerLowest, black.surface)
        assertEquals(scheme.surfaceContainerLow, black.surfaceContainer)
        assertEquals(scheme.surfaceContainerHigh, black.surfaceContainerHighest)
        val roles = dynamicStarRoles(black, Look.Oled, 285.0)
        assertEquals(Color.Black, roles.canvas)
        assertEquals("#000000", hex(roles.stage))
    }
}
