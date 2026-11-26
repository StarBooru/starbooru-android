package eu.filipnone.starbooru.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/** A colour in OKLab: lightness from 0 to 1, then the two axes of its hue. */
data class Oklab(val l: Double, val a: Double, val b: Double) {
    /** OKLCH's chroma: how far from grey it is. */
    val chroma: Double get() = hypot(a, b)
}

/** Below this chroma a colour is a grey, and its hue is noise. */
const val MIN_CHROMA = 0.02

/** An OKLCH colour as opaque sRGB ARGB, each channel clipped to what sRGB can show. */
fun oklchToArgb(lightness: Double, chroma: Double, hueDegrees: Double): Int {
    val h = hueDegrees * (PI / 180.0)
    val a = chroma * cos(h)
    val b = chroma * sin(h)
    // OKLab to linear sRGB (Björn Ottosson's matrices).
    val l = lightness + 0.3963377774 * a + 0.2158037573 * b
    val m = lightness - 0.1055613458 * a - 0.0638541728 * b
    val s = lightness - 0.0894841775 * a - 1.2914855480 * b
    val l3 = l * l * l
    val m3 = m * m * m
    val s3 = s * s * s
    val red = 4.0767416621 * l3 - 3.3077115913 * m3 + 0.2309699292 * s3
    val green = -1.2684380046 * l3 + 2.6097574011 * m3 - 0.3413193965 * s3
    val blue = -0.0041960863 * l3 - 0.7034186147 * m3 + 1.7076147010 * s3
    return argbOf(channel(red), channel(green), channel(blue))
}

/** An OKLCH colour as a Compose colour, with [alpha] from 0 to 1. */
fun oklchColor(lightness: Double, chroma: Double, hueDegrees: Double, alpha: Float = 1f): Color {
    val opaque = Color(oklchToArgb(lightness, chroma, hueDegrees))
    return if (alpha >= 1f) opaque else opaque.copy(alpha = alpha.coerceIn(0f, 1f))
}

/** An sRGB colour (the alpha is ignored) in OKLab. */
fun oklabOf(argb: Int): Oklab {
    val r = linear(argb shr 16 and 0xFF)
    val g = linear(argb shr 8 and 0xFF)
    val b = linear(argb and 0xFF)
    // Linear sRGB to OKLab (Björn Ottosson's matrices).
    val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
    val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
    val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
    return Oklab(
        l = 0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s,
        a = 1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s,
        b = 0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s,
    )
}

/** The OKLCH hue of an sRGB colour in whole degrees (0 to 359), or null for a grey. */
fun hueOf(argb: Int): Int? {
    val lab = oklabOf(argb)
    if (lab.chroma < MIN_CHROMA) return null
    val degrees = atan2(lab.b, lab.a) * (180.0 / PI)
    val turned = (degrees % 360.0).let { if (it < 0.0) it + 360.0 else it }
    // 0 to 360 after the rounding, and 360 is 0 again.
    return (Math.round(turned) % 360).toInt()
}

/** The relative luminance of an sRGB colour, as WCAG works out contrast. */
fun relativeLuminance(argb: Int): Double =
    0.2126 * linear(argb shr 16 and 0xFF) + 0.7152 * linear(argb shr 8 and 0xFF) + 0.0722 * linear(argb and 0xFF)

/** WCAG's contrast ratio between two sRGB colours, from 1 to 21. */
fun contrastRatio(first: Int, second: Int): Double {
    val a = relativeLuminance(first)
    val b = relativeLuminance(second)
    return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
}

/** Whether a mark on [argb] reads better dark than white. */
fun darkInk(argb: Int): Boolean {
    // Contrast with black against contrast with white.
    val y = relativeLuminance(argb) + 0.05
    return y * y > 0.05 * 1.05
}

/** An 8-bit sRGB channel as a linear one. */
private fun linear(channel: Int): Double {
    val c = channel / 255.0
    return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
}

/** A linear channel as an 8-bit sRGB one. NaN comes out as 0. */
private fun channel(linear: Double): Int {
    val x = linear.coerceIn(0.0, 1.0)
    val encoded = if (x <= 0.0031308) 12.92 * x else 1.055 * x.pow(1.0 / 2.4) - 0.055
    return Math.round(encoded * 255.0).coerceIn(0L, 255L).toInt()
}

private fun argbOf(red: Int, green: Int, blue: Int): Int = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
