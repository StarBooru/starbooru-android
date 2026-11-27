package eu.filipnone.starbooru.ui.gallery

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.filipnone.starbooru.ui.common.LocalReducedMotion

/** What a choice's picture shows: one tile's shape, or how a staggered grid sits. */
enum class Glyph { Square, Wide, Tall, Columns, Rows, Mosaic }

/** One choice of the view menu: its value, its label and its picture. */
data class ShapeChoice<T>(val value: T, val label: Int, val glyph: Glyph)

/** How the view menu's choices share their rows. */
object ChoiceRows {
    // The view menu's widths, in dp.
    private const val NARROW = 300
    private const val WIDE = 400

    /** From this grid width, in dp, the menu is wide: its choices keep to one row, and it fits a short screen. */
    private const val WIDE_GRID = 600.0

    /** The view menu's width, in dp, over a grid [gridWidth] dp wide. */
    fun menuWidth(gridWidth: Double): Int = if (gridWidth >= WIDE_GRID) WIDE else NARROW

    /**
     * How many choices go in a row: all [count] in one when they fit,
     * [fit] being how many do, otherwise rows as even as they can be, so
     * four that don't fit make two rows of two rather than three and one.
     */
    fun columns(
        count: Int,
        fit: Int,
    ): Int {
        if (count <= 0) return 1
        val most = fit.coerceIn(1, count)
        val rows = (count + most - 1) / most
        return (count + rows - 1) / rows
    }

    /** How many cells [widest] wide, each with [padding] on both sides, fit side by side in [width], with a [line] between. */
    fun fit(
        width: Float,
        widest: Float,
        padding: Float,
        line: Float,
    ): Int {
        val cell = widest + 2 * padding
        if (!(cell > 0f) || !width.isFinite()) return 1
        return ((width + line) / (cell + line)).toInt().coerceAtLeast(1)
    }
}

private val CellPadding = 8.dp
private val LabelSize = 13.sp

/**
 * A segmented choice, each with a picture over its full label, that goes
 * into rows when the labels don't fit side by side in [width].
 */
@Composable
fun <T> ShapeChoices(
    choices: List<ShapeChoice<T>>,
    selected: T,
    width: Dp,
    onPick: (T) -> Unit,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    // The bolder, picked label is the widest a label gets.
    val style = LocalTextStyle.current.copy(fontSize = LabelSize, fontWeight = FontWeight.SemiBold)
    val labels = choices.map { stringResource(it.label) }
    val widest = labels.maxOfOrNull { measurer.measure(it, style, maxLines = 1, softWrap = false).size.width } ?: 0
    val columns =
        with(density) {
            ChoiceRows.columns(choices.size, ChoiceRows.fit(width.toPx(), widest.toFloat(), CellPadding.toPx(), 1.dp.toPx()))
        }
    val rows = choices.indices.chunked(columns)
    val colors = SegmentedButtonDefaults.colors()
    val shape = if (rows.size == 1) RoundedCornerShape(50) else RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, colors.activeBorderColor, shape)
            .selectableGroup(),
    ) {
        rows.forEachIndexed { r, row ->
            if (r > 0) HorizontalDivider(color = colors.activeBorderColor)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                row.forEachIndexed { c, i ->
                    if (c > 0) VerticalDivider(color = colors.activeBorderColor)
                    val choice = choices[i]
                    Cell(
                        label = labels[i],
                        glyph = choice.glyph,
                        on = choice.value == selected,
                        onClick = { onPick(choice.value) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun Cell(
    label: String,
    glyph: Glyph,
    on: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = SegmentedButtonDefaults.colors()
    val reduced = LocalReducedMotion.current
    val fill by animateColorAsState(
        if (on) colors.activeContainerColor else colors.inactiveContainerColor,
        if (reduced) snap() else tween(200),
        label = "fill",
    )
    val ink = if (on) colors.activeContentColor else colors.inactiveContentColor
    Column(
        modifier
            .background(fill)
            .selectable(selected = on, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = CellPadding, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        GlyphPicture(glyph, ink, Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = ink,
            fontSize = LabelSize,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A small picture of [glyph] in [ink]. */
@Composable
private fun GlyphPicture(
    glyph: Glyph,
    ink: Color,
    modifier: Modifier,
) {
    Canvas(modifier) {
        // Laid out on a 20 by 20 grid.
        val u = size.minDimension / 20f
        fun box(
            x: Float,
            y: Float,
            w: Float,
            h: Float,
        ) = block(ink, x * u, y * u, w * u, h * u, 2f * u)
        when (glyph) {
            Glyph.Square -> box(3f, 3f, 14f, 14f)

            Glyph.Wide -> box(1f, 4.5f, 18f, 11f)

            Glyph.Tall -> box(4.5f, 1f, 11f, 18f)

            Glyph.Columns -> {
                box(1.5f, 1.5f, 8f, 10f)
                box(1.5f, 13.5f, 8f, 5f)
                box(10.5f, 1.5f, 8f, 5f)
                box(10.5f, 8.5f, 8f, 10f)
            }

            Glyph.Rows -> {
                box(1.5f, 1.5f, 10f, 8f)
                box(13.5f, 1.5f, 5f, 8f)
                box(1.5f, 10.5f, 5f, 8f)
                box(8.5f, 10.5f, 10f, 8f)
            }

            Glyph.Mosaic -> {
                box(1.5f, 1.5f, 11f, 11f)
                box(13.5f, 1.5f, 5f, 11f)
                box(1.5f, 13.5f, 17f, 5f)
            }
        }
    }
}

private fun DrawScope.block(
    ink: Color,
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    corner: Float,
) = drawRoundRect(ink, Offset(x, y), Size(w, h), CornerRadius(corner, corner))
