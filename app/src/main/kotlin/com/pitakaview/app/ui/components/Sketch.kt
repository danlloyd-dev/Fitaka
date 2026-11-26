package com.pitakaview.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.pitakaview.app.ui.theme.PitakaDimens

/**
 * The sketch look in one modifier: a solid shadow offset down-right, the fill, an optional [gloss] on
 * the fill, and a thick outline.
 * The shadow is drawn outside this node's bounds, so callers reserve room for it with
 * `padding(end = offset, bottom = offset)` *before* this modifier.
 */
fun Modifier.sketchBox(
    shape: Shape,
    fill: Color,
    border: Color,
    borderWidth: Dp = PitakaDimens.border,
    shadow: Color = Color.Unspecified,
    shadowOffset: Dp = 0.dp,
    gloss: Gloss? = null,
): Modifier = this
    .drawWithCache {
        // Built once per size, not on every frame.
        val outline = shape.createOutline(size, layoutDirection, this)
        val offset = shadowOffset.toPx()
        val shine = gloss?.let { glossFor(it, fill, inset = borderWidth.toPx()) }
        onDrawBehind {
            if (offset > 0f && shadow.isSpecified) {
                translate(offset, offset) { drawOutline(outline, shadow) }
            }
            drawOutline(outline, fill)
            shine?.invoke(this)
        }
    }
    .border(borderWidth, border, shape)

/** The gloss alone, for a part that has no outline of its own (the chosen part of a segmented control). */
fun Modifier.gloss(gloss: Gloss, on: Color): Modifier = drawWithCache {
    val shine = glossFor(gloss, on, inset = 0f)
    onDrawBehind { shine() }
}

/**
 * The jelly gloss on a solid-coloured part: a paler band across its top half, with rounded ends and set
 * in a little from the edges, and a small white glint at its top right. It is drawn over the fill and
 * under the icon and words. On white it shows nothing, so white parts stay plain.
 *
 * Sizes are measured from inside the outline. [height] is the band's share of the part's inner height.
 */
@Immutable
class Gloss(
    val side: Dp,
    val top: Dp,
    val height: Float,
    val topRadius: Dp,
    val bottomRadius: Dp,
    val glint: DpSize? = null,
    val glintTop: Dp = 0.dp,
    val glintEnd: Dp = 0.dp,
    /** A round part: the band follows the circle, as a dome. */
    val dome: Boolean = false,
) {
    companion object {
        val Button = Gloss(4.dp, 3.dp, 0.46f, 10.dp, 13.dp, DpSize(12.dp, 6.dp), 7.dp, 14.dp)
        val RoundButton = Gloss(5.dp, 3.dp, 0.45f, 0.dp, 0.dp, DpSize(8.dp, 4.5.dp), 7.dp, 9.dp, dome = true)
        val Badge = Gloss(3.dp, 2.dp, 0.45f, 8.dp, 10.dp, DpSize(7.dp, 4.dp), 4.dp, 5.dp)
        val Tab = Gloss(3.dp, 3.dp, 0.46f, 11.dp, 13.dp, DpSize(9.dp, 5.dp), 6.dp, 8.dp)
        val Chip = Gloss(5.dp, 3.dp, 0.44f, 99.dp, 99.dp, DpSize(9.dp, 5.dp), 6.dp, 13.dp)
        val Segment = Gloss(4.dp, 3.dp, 0.44f, 8.dp, 10.dp, DpSize(9.dp, 5.dp), 6.dp, 11.dp)
        val Switch = Gloss(3.dp, 2.dp, 0.44f, 99.dp, 99.dp)
        val Swatch = Gloss(4.dp, 3.dp, 0.44f, 9.dp, 11.dp)
    }
}

/**
 * A dark fill (ink, or a card in dark mode) takes a soft gloss; a bright one (the primary and section
 * colours, pale and white surfaces) takes the full one. The line sits between the darkest primary
 * (Kape's brown, 0.19) and the lightest dark surface (a dark-mode icon button, 0.08).
 */
internal fun glossIsSoft(fill: Color): Boolean = fill.luminance() < SOFT_GLOSS_BELOW

internal const val SOFT_GLOSS_BELOW = 0.12f

private const val GLINT_TURN = -24f

private fun CacheDrawScope.glossFor(gloss: Gloss, fill: Color, inset: Float): DrawScope.() -> Unit {
    val soft = glossIsSoft(fill)
    val width = size.width - 2 * inset
    val height = size.height - 2 * inset
    val band = Rect(
        left = inset + gloss.side.toPx(),
        top = inset + gloss.top.toPx(),
        right = inset + width - gloss.side.toPx(),
        bottom = inset + gloss.top.toPx() + height * gloss.height,
    )
    if (band.width <= 0f || band.height <= 0f) return {}
    val upper = if (gloss.dome) CornerRadius(band.width / 2, band.height * 0.78f) else CornerRadius(gloss.topRadius.toPx())
    val lower = if (gloss.dome) CornerRadius(band.width / 2, band.height * 0.22f) else CornerRadius(gloss.bottomRadius.toPx())
    val path = Path().apply { addRoundRect(RoundRect(band, topLeft = upper, topRight = upper, bottomRight = lower, bottomLeft = lower)) }
    val light = Brush.verticalGradient(
        colors = listOf(Color.White.copy(alpha = if (soft) 0.24f else 0.66f), Color.White.copy(alpha = if (soft) 0.07f else 0.30f)),
        startY = band.top,
        endY = band.bottom,
    )
    val glintColor = Color.White.copy(alpha = if (soft) 0.60f else 0.96f)
    val glintSize = gloss.glint?.let { Size(it.width.toPx(), it.height.toPx()) }
    val glintAt = glintSize?.let { Offset(inset + width - gloss.glintEnd.toPx() - it.width, inset + gloss.glintTop.toPx()) }
    return {
        drawPath(path, light)
        if (glintSize != null && glintAt != null) {
            rotate(GLINT_TURN, pivot = glintAt + Offset(glintSize.width / 2, glintSize.height / 2)) {
                drawOval(glintColor, glintAt, glintSize)
            }
        }
    }
}

/**
 * The light on a section-tinted card: a thin streak along its top edge and a dot at the end of it,
 * inside the outline. Fainter in dark mode, where the tint itself is dark.
 */
fun Modifier.tileShine(dark: Boolean, inset: Dp = PitakaDimens.border): Modifier = drawBehind {
    val edge = inset.toPx()
    val top = edge + 4.dp.toPx()
    val thick = 6.dp.toPx()
    val streak = Size(size.width - 2 * edge - 9.dp.toPx() - 30.dp.toPx(), thick)
    if (streak.width > 0f) {
        drawRoundRect(
            color = Color.White.copy(alpha = if (dark) 0.20f else 0.72f),
            topLeft = Offset(edge + 9.dp.toPx(), top),
            size = streak,
            cornerRadius = CornerRadius(thick / 2),
        )
    }
    val dot = Size(10.dp.toPx(), thick)
    drawOval(Color.White.copy(alpha = if (dark) 0.34f else 0.92f), Offset(size.width - edge - 13.dp.toPx() - dot.width, top), dot)
}
