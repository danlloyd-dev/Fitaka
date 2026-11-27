package com.pitakaview.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.pitakaview.app.domain.UiStyle
import com.pitakaview.app.ui.theme.LocalUiStyle
import com.pitakaview.app.ui.theme.FitakaDimens
import kotlin.math.max
import kotlin.math.sin

/**
 * The app's look in one modifier: a solid shadow offset down-right, the fill, and a thick outline,
 * finished in the style chosen in Settings:
 *
 * - **Plain**: just that.
 * - **Glossy**: the outline is drawn by an unsteady hand, twice over, and the fill carries a [gloss] (a
 *   paler band and a white glint, drawn by the same hand), where the part asks for one.
 * - **Glassy**: a part that says it is a pane of [glass] has a see-through fill and a line of light
 *   inside its top edge; every other part is as in Plain.
 * - **Sketch**: the same hand-drawn outline, no gloss, and a shadow of pencil strokes.
 *
 * The shadow is drawn outside this node's bounds, so callers reserve room for it with
 * `padding(end = offset, bottom = offset)` *before* this modifier.
 *
 * The outline is normally drawn last, over the part's contents, so nothing inside can cover it. A part
 * with something that stands out across its edge (the bottom bar and its chosen tab's tile) passes
 * [outlineUnderContent]: its outline is then drawn first, and what stands out is drawn over it.
 */
fun Modifier.sketchBox(
    shape: Shape,
    fill: Color,
    border: Color,
    borderWidth: Dp = FitakaDimens.border,
    shadow: Color = Color.Unspecified,
    shadowOffset: Dp = 0.dp,
    gloss: Gloss? = null,
    outlineUnderContent: Boolean = false,
    /**
     * A pane of glass in the Glassy style: cards, fields, plain buttons. A part whose colour says
     * something (a section's badge, the primary button, a theme's dots) is not, and neither is the
     * bottom bar, which lists scroll behind.
     */
    glass: Boolean = false,
): Modifier = composed {
    sketchBox(LocalUiStyle.current, shape, fill, border, borderWidth, shadow, shadowOffset, gloss, outlineUnderContent, glass)
}

/** How much of a fill's own colour is left in the Glassy style: the rest is what lies behind it. */
internal const val GLASS_FILL = 0.62f

private fun Modifier.sketchBox(
    style: UiStyle,
    shape: Shape,
    fill: Color,
    border: Color,
    borderWidth: Dp,
    shadow: Color,
    shadowOffset: Dp,
    gloss: Gloss?,
    outlineUnderContent: Boolean,
    pane: Boolean,
): Modifier {
    val glass = style == UiStyle.GLASSY && pane
    // Glossy and Sketch are both drawn by an unsteady hand. Only Sketch's shadow is pencil strokes.
    val handDrawn = style == UiStyle.SKETCH || style == UiStyle.GLOSSY
    val hatched = style == UiStyle.SKETCH
    val drawnFill = if (glass) fill.copy(alpha = fill.alpha * GLASS_FILL) else fill
    val drawOwnOutline = handDrawn || outlineUnderContent
    return this
        .drawWithCache {
            // Built once per size, not on every frame.
            val outline = shape.createOutline(size, layoutDirection, this)
            val offset = shadowOffset.toPx()
            val width = borderWidth.toPx()
            val shine = if (style == UiStyle.GLOSSY) gloss?.let { glossFor(it, fill, inset = width) } else null
            val lines = when {
                handDrawn -> roughEdges(outline, width, density)
                outlineUnderContent -> listOf(RoughLine(insideEdge(outline, width), width, 1f))
                else -> emptyList()
            }
            // Under a see-through fill the shadow must not show through: it is cut out where the part lies.
            // The Sketch style's shadow is pencil strokes, kept to where the solid shadow would show.
            val shadowed = offset > 0f && shadow.isSpecified
            val body = if ((glass || hatched) && shadowed) Path().apply { addOutline(outline) } else null
            val cast = if (hatched && body != null) Path().apply { addPath(body, Offset(offset, offset)) } else null
            val strokes = if (cast != null) hatching(size, offset, density) else null
            val rim = if (glass) insideEdge(outline, width + 2 * density) else null
            val drawLines: DrawScope.() -> Unit = {
                lines.forEach { drawPath(it.path, border.copy(alpha = border.alpha * it.alpha), style = Stroke(it.width, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
            }
            onDrawWithContent {
                if (shadowed) {
                    when {
                        body != null && cast != null && strokes != null -> clipPath(body, ClipOp.Difference) {
                            clipPath(cast) { drawPath(strokes, shadow, style = Stroke(HATCH_WIDTH * density, cap = StrokeCap.Round)) }
                        }
                        body != null -> clipPath(body, ClipOp.Difference) { translate(offset, offset) { drawOutline(outline, shadow) } }
                        else -> translate(offset, offset) { drawOutline(outline, shadow) }
                    }
                }
                drawOutline(outline, drawnFill)
                shine?.invoke(this)
                if (rim != null) {
                    clipRect(bottom = size.height * 0.5f) { drawPath(rim, Color.White.copy(alpha = 0.55f), style = Stroke(1.5f * density)) }
                }
                if (outlineUnderContent) {
                    drawLines()
                    drawContent()
                } else {
                    drawContent()
                    drawLines()
                }
            }
        }
        .then(if (drawOwnOutline) Modifier else Modifier.border(borderWidth, border, shape))
}

private class RoughLine(val path: Path, val width: Float, val alpha: Float)

/** The Sketch style's shadow strokes: how thick each is and how far apart, in dp. */
private const val HATCH_WIDTH = 1.2f
private const val HATCH_STEP = 4f

/**
 * The Sketch style's shadow: slanted pencil strokes down the right side and along the bottom, where the
 * solid shadow would be. Only short strokes across those two strips are made (a part 350 wide gets about
 * 120 of them, each a few dp long), not lines across the whole part; the caller clips them to the shadow's
 * shape. Both strips take their strokes from one family of lines, so they meet at the corner.
 */
private fun hatching(size: Size, offset: Float, density: Float): Path = Path().apply {
    val step = HATCH_STEP * density
    // Past the strip, to fill the wider corner left by a rounded part.
    val reach = offset + 8f * density
    val right = size.width + offset
    val bottom = size.height + offset
    var line = step
    while (line < right + bottom) {
        if (line > right) {
            val y = line - right
            moveTo(right, y)
            lineTo(right - reach, y + reach)
        }
        if (line > bottom) {
            val x = line - bottom
            moveTo(x, bottom)
            lineTo(x + reach, bottom - reach)
        }
        line += step
    }
}

/** The line down the middle of an outline [width] thick drawn just inside [outline], as `border` draws it. */
private fun insideEdge(outline: Outline, width: Float): Path = Path().apply {
    val half = width / 2
    fun CornerRadius.inside() = CornerRadius((x - half).coerceAtLeast(0f), (y - half).coerceAtLeast(0f))
    when (outline) {
        is Outline.Rectangle -> addRect(outline.rect.deflate(half))
        is Outline.Rounded -> outline.roundRect.let {
            addRoundRect(
                RoundRect(
                    it.left + half, it.top + half, it.right - half, it.bottom - half,
                    it.topLeftCornerRadius.inside(), it.topRightCornerRadius.inside(),
                    it.bottomRightCornerRadius.inside(), it.bottomLeftCornerRadius.inside(),
                ),
            )
        }
        is Outline.Generic -> addPath(outline.path)
    }
}

/**
 * [path] as an unsteady hand would draw it: the line wanders a little to either side of the true one, in
 * slow waves, [sway] dp at most. A point is taken every [every] dp along it. The waves depend only on the
 * shape's own size, so nothing shimmers from frame to frame; the caller builds this once per size.
 */
private fun wandering(path: Path, sway: Float, shift: Float, density: Float, every: Float = 4f): Path {
    val measure = PathMeasure().apply { setPath(path, forceClosed = true) }
    val length = measure.length
    if (length <= 0f) return path
    return Path().apply {
        val steps = max(12, (length / (every * density)).toInt())
        for (i in 0..steps) {
            val at = length * i / steps
            val point = measure.getPosition(at)
            val along = measure.getTangent(at)
            val wander = sway * density * (0.6f * sin(at / (21f * density) + shift) + 0.4f * sin(at / (8f * density) + shift * 1.7f))
            val x = point.x - along.y * wander
            val y = point.y + along.x * wander
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

/**
 * The hand-drawn outline of the Glossy and Sketch styles: the part's edge gone over twice. The second
 * pass is fainter and off the first.
 */
private fun roughEdges(outline: Outline, width: Float, density: Float): List<RoughLine> {
    val edge = insideEdge(outline, width)
    val length = PathMeasure().apply { setPath(edge, forceClosed = true) }.length
    if (length <= 0f) return emptyList()
    val seed = length * 0.011f
    return listOf(
        RoughLine(wandering(edge, 0.9f, seed, density), width * 0.8f, 1f),
        RoughLine(wandering(edge, 1.4f, seed + 2.3f, density), width * 0.45f, 0.6f),
    )
}

/** The gloss alone, for a part that has no outline of its own (the chosen part of a segmented control). */
fun Modifier.gloss(gloss: Gloss, on: Color): Modifier = composed {
    if (LocalUiStyle.current != UiStyle.GLOSSY) {
        this
    } else {
        drawWithCache {
            val shine = glossFor(gloss, on, inset = 0f)
            onDrawBehind { shine() }
        }
    }
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

/** How far, in dp, the gloss's band and its glint wander from a ruled shape: less than the outline does. */
private const val GLOSS_SWAY = 0.8f
private const val GLINT_SWAY = 0.45f

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
    // Drawn by the same unsteady hand as the outline: the band's edge wanders a little.
    val path = wandering(
        Path().apply { addRoundRect(RoundRect(band, topLeft = upper, topRight = upper, bottomRight = lower, bottomLeft = lower)) },
        sway = GLOSS_SWAY, shift = band.width * 0.013f, density = density,
    )
    val light = Brush.verticalGradient(
        colors = listOf(Color.White.copy(alpha = if (soft) 0.24f else 0.66f), Color.White.copy(alpha = if (soft) 0.07f else 0.30f)),
        startY = band.top,
        endY = band.bottom,
    )
    val glintColor = Color.White.copy(alpha = if (soft) 0.60f else 0.96f)
    val glintSize = gloss.glint?.let { Size(it.width.toPx(), it.height.toPx()) }
    val glintAt = glintSize?.let { Offset(inset + width - gloss.glintEnd.toPx() - it.width, inset + gloss.glintTop.toPx()) }
    val glint = if (glintSize != null && glintAt != null) {
        wandering(Path().apply { addOval(Rect(glintAt, glintSize)) }, sway = GLINT_SWAY, shift = 1.3f, density = density, every = 2f)
    } else {
        null
    }
    return {
        drawPath(path, light)
        if (glint != null && glintSize != null && glintAt != null) {
            rotate(GLINT_TURN, pivot = glintAt + Offset(glintSize.width / 2, glintSize.height / 2)) {
                drawPath(glint, glintColor)
            }
        }
    }
}

/**
 * The light on a section-tinted card in the Glossy style: a thin streak along its top edge and a dot at
 * the end of it, inside the outline. Fainter in dark mode, where the tint itself is dark.
 */
fun Modifier.tileShine(dark: Boolean, inset: Dp = FitakaDimens.border): Modifier = composed {
    if (LocalUiStyle.current != UiStyle.GLOSSY) {
        this
    } else {
        // Built once per size. The streak and the dot are drawn by the same unsteady hand as the outline.
        drawWithCache {
            val edge = inset.toPx()
            val top = edge + 4.dp.toPx()
            val thick = 6.dp.toPx()
            val streak = Size(size.width - 2 * edge - 9.dp.toPx() - 30.dp.toPx(), thick)
            val streakPath = if (streak.width > 0f) {
                wandering(
                    Path().apply { addRoundRect(RoundRect(Rect(Offset(edge + 9.dp.toPx(), top), streak), CornerRadius(thick / 2))) },
                    sway = GLOSS_SWAY, shift = streak.width * 0.013f, density = density,
                )
            } else {
                null
            }
            val dot = Size(10.dp.toPx(), thick)
            val dotPath = wandering(
                Path().apply { addOval(Rect(Offset(size.width - edge - 13.dp.toPx() - dot.width, top), dot)) },
                sway = GLINT_SWAY, shift = 1.3f, density = density, every = 2f,
            )
            val streakColor = Color.White.copy(alpha = if (dark) 0.20f else 0.72f)
            val dotColor = Color.White.copy(alpha = if (dark) 0.34f else 0.92f)
            onDrawBehind {
                if (streakPath != null) drawPath(streakPath, streakColor)
                drawPath(dotPath, dotColor)
            }
        }
    }
}
