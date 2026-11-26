package com.pitakaview.app.ui.mascot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pitakaview.app.ui.theme.PitakaTheme

/** How the alkansya looks: saying hello with a coin above it, pleased with a coin going in, or dozing. */
enum class AlkansyaMood { HELLO, HAPPY, SLEEPY }

/** The drawing is laid out on a 200 x 170 grid and scaled to the size asked for. */
private const val GRID_WIDTH = 200f
private const val GRID_HEIGHT = 170f

/**
 * The app's mascot: an alkansya (a piggy bank), drawn in code in the app's own style, with thick ink
 * outlines, flat fills and a solid offset shadow. It takes its colours from the theme, so it changes with
 * the theme and with light or dark. It is decoration: screen readers skip it, and the words beside it
 * carry the meaning.
 */
@Composable
fun Alkansya(modifier: Modifier = Modifier, mood: AlkansyaMood = AlkansyaMood.HELLO, width: Dp = 120.dp) {
    val c = PitakaTheme.colors
    val body = c.primary
    // The snout and cheek: the body colour lightened, the same way in light and dark themes.
    val pale = lerp(c.primary, Color.White, 0.5f)
    // The outline uses the theme ink, like every card. What is drawn ON the body (eye, slot, smile) must
    // stand out from the body instead: in dark themes the ink is as light as the body is.
    val darkest = if (c.isDark) c.background else c.ink
    val lightest = if (c.isDark) c.ink else c.card
    fun on(fill: Color) = if (fill.luminance() > 0.12f) darkest else lightest
    Canvas(modifier.size(width, width * (GRID_HEIGHT / GRID_WIDTH)).clearAndSetSemantics { }) {
        val u = size.width / GRID_WIDTH
        val ink = Stroke(width = 5f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val thin = Stroke(width = 3.5f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)

        fun oval(left: Float, top: Float, right: Float, bottom: Float, fill: Color, outline: Boolean = true) {
            val topLeft = Offset(left * u, top * u)
            val box = Size((right - left) * u, (bottom - top) * u)
            drawOval(fill, topLeft, box)
            if (outline) drawOval(c.ink, topLeft, box, style = ink)
        }

        fun block(left: Float, top: Float, right: Float, bottom: Float, radius: Float, fill: Color, outline: Boolean = true) {
            val topLeft = Offset(left * u, top * u)
            val box = Size((right - left) * u, (bottom - top) * u)
            val corner = CornerRadius(radius * u)
            drawRoundRect(fill, topLeft, box, corner)
            if (outline) drawRoundRect(c.ink, topLeft, box, corner, style = ink)
        }

        fun line(path: Path, style: Stroke = ink) = drawPath(path, c.ink, style = style)

        fun path(build: Path.() -> Unit) = Path().apply(build)

        // The solid shadow: the whole shape again, shifted down and right, behind everything.
        translate(left = 6f * u, top = 6f * u) {
            block(56f, 128f, 76f, 156f, 6f, c.shadow, outline = false)
            block(112f, 128f, 132f, 156f, 6f, c.shadow, outline = false)
            oval(30f, 48f, 158f, 140f, c.shadow, outline = false)
            block(138f, 78f, 178f, 112f, 14f, c.shadow, outline = false)
        }

        // The coin: above the slot when saying hello, halfway in when happy (the body then covers its lower half).
        if (mood != AlkansyaMood.SLEEPY) {
            val y = if (mood == AlkansyaMood.HAPPY) 42f else 22f
            oval(75f, y - 15f, 105f, y + 15f, c.card)
            drawCircle(on(c.card), radius = 7.5f * u, center = Offset(90f * u, y * u), style = thin)
        }

        // Legs and ear first, so the body overlaps where they join it.
        block(56f, 128f, 76f, 156f, 6f, body)
        block(112f, 128f, 132f, 156f, 6f, body)
        val ear = path {
            moveTo(100f * u, 64f * u)
            lineTo(112f * u, 32f * u)
            lineTo(132f * u, 62f * u)
            close()
        }
        drawPath(ear, body)
        line(ear)

        // The curly tail.
        line(
            path {
                moveTo(32f * u, 88f * u)
                quadraticTo(14f * u, 82f * u, 17f * u, 98f * u)
                quadraticTo(20f * u, 108f * u, 29f * u, 101f * u)
            },
        )

        oval(30f, 48f, 158f, 140f, body)
        // A rosy cheek and the snout, in a paler mix of the same colour.
        drawCircle(pale, radius = 8f * u, center = Offset(113f * u, 106f * u))
        block(138f, 78f, 178f, 112f, 14f, pale)
        drawCircle(on(pale), radius = 3.2f * u, center = Offset(153f * u, 95f * u))
        drawCircle(on(pale), radius = 3.2f * u, center = Offset(165f * u, 95f * u))

        // The slot the coins go in.
        drawLine(on(body), Offset(72f * u, 61f * u), Offset(104f * u, 59f * u), strokeWidth = 6.5f * u, cap = StrokeCap.Round)

        face(mood, u, on(body), c.ink, ink, thin)
    }
}

/** [ink] is for what sits on the body; [outside] for the Zs, which float over the page behind. */
private fun DrawScope.face(mood: AlkansyaMood, u: Float, ink: Color, outside: Color, thick: Stroke, thin: Stroke) {
    when (mood) {
        AlkansyaMood.HELLO -> drawCircle(ink, radius = 4.8f * u, center = Offset(126f * u, 80f * u))
        // An eye smiling shut.
        AlkansyaMood.HAPPY -> drawPath(
            Path().apply {
                moveTo(119f * u, 83f * u)
                quadraticTo(126f * u, 72f * u, 133f * u, 83f * u)
            },
            ink, style = thick,
        )
        AlkansyaMood.SLEEPY -> {
            drawLine(ink, Offset(119f * u, 81f * u), Offset(133f * u, 81f * u), strokeWidth = thick.width, cap = StrokeCap.Round)
            // Two little Zs drifting up.
            fun z(left: Float, top: Float, side: Float) = drawPath(
                Path().apply {
                    moveTo(left * u, top * u)
                    lineTo((left + side) * u, top * u)
                    lineTo(left * u, (top + side) * u)
                    lineTo((left + side) * u, (top + side) * u)
                },
                outside, style = thin,
            )
            z(146f, 38f, 13f)
            z(168f, 18f, 9f)
        }
    }
    if (mood != AlkansyaMood.SLEEPY) {
        drawPath(
            Path().apply {
                moveTo(123f * u, 117f * u)
                quadraticTo(132f * u, 126f * u, 141f * u, 117f * u)
            },
            ink, style = thin,
        )
    }
}

/** Where on the grid the body sits; used by tests to check the drawing stays inside its box. */
internal val AlkansyaBounds = Rect(12f, 5f, 190f, 164f)

/** The dozing alkansya at the size the empty lists use: nothing to do here yet. */
val SleepyAlkansya: @Composable () -> Unit = { Alkansya(mood = AlkansyaMood.SLEEPY, width = 84.dp) }
