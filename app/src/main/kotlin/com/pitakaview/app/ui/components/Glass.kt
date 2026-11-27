package com.pitakaview.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pitakaview.app.domain.UiStyle
import com.pitakaview.app.ui.theme.LocalUiStyle
import com.pitakaview.app.ui.theme.FitakaColors
import com.pitakaview.app.ui.theme.FitakaTheme
import com.pitakaview.app.ui.theme.Section
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource

/** The blur source for frosted bars. Null (e.g. in screenshot tests) means bars fall back to solid colour. */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * How much of the bar colour sits over the blurred content. High enough that ink text on the bar stays
 * ≥ 4.5:1 even if solid ink were behind it (checked in ThemeContrastTest).
 */
const val GLASS_TINT_LIGHT = 0.72f
const val GLASS_TINT_DARK = 0.80f

/** Marks this content as what frosted bars blur. */
@Composable
fun Modifier.glassSource(): Modifier {
    val haze = LocalHazeState.current ?: return this
    return this.hazeSource(haze)
}

/**
 * A frosted-glass background in [color]: real blur of the content behind on Android 12+, a stronger
 * see-through tint on older phones, and a solid fill when no blur source exists.
 */
@Composable
fun Modifier.glassBackground(color: Color): Modifier {
    val haze = LocalHazeState.current ?: return this.background(color)
    val alpha = if (FitakaTheme.colors.isDark) GLASS_TINT_DARK else GLASS_TINT_LIGHT
    return this.hazeBlur(
        input = HazeInput.Backdrop(haze),
        style = HazeBlurStyle {
            blurRadius(24.dp)
            backgroundColor(color)
            colorEffects(listOf(HazeColorEffect.tint(color.copy(alpha = alpha))))
            fallbackColorEffect(HazeColorEffect.tint(color.copy(alpha = 0.94f)))
        },
    )
}

/** How strong the soft colour on the page is in the Glassy style, at the middle of each patch. */
const val GLASS_PAGE_COLOUR = 0.30f

/**
 * The page behind see-through cards in the Glassy style: four soft patches of colour (the theme's own and
 * three of the sections'), fixed to the screen, so the cards pass over them as the list scrolls. Glass
 * shows nothing on a flat page. Nothing is blurred: the patches are soft already.
 *
 * The strip under the top bar, [skipTop] high, is left to the bar ([glassyBar]): the bar lays its own tint
 * over whatever is behind it, and patches drawn under that tint would step in strength at the bar's edge.
 */
fun Modifier.glassyPage(skipTop: Dp = 0.dp): Modifier = composed {
    if (LocalUiStyle.current != UiStyle.GLASSY) return@composed this
    val c = FitakaTheme.colors
    drawWithCache {
        val patches = colourPatches(c, size.width)
        onDrawBehind { clipRect(top = skipTop.toPx()) { patches.forEach { it() } } }
    }
}

/** The same patches on the top bar of a [glassyPage], over the bar's tint, so the colour runs on under it. */
fun Modifier.glassyBar(onGlassyPage: Boolean): Modifier = composed {
    if (!onGlassyPage || LocalUiStyle.current != UiStyle.GLASSY) return@composed this
    val c = FitakaTheme.colors
    drawWithCache {
        val patches = colourPatches(c, size.width)
        onDrawBehind { clipRect { patches.forEach { it() } } }
    }
}

/**
 * The patches, made once per width. They are placed by the width alone, measured from the top left of the
 * page, so the page and the bar at its top draw the very same ones whatever their own heights.
 */
private fun colourPatches(c: FitakaColors, width: Float): List<DrawScope.() -> Unit> {
    val reach = width * 0.75f
    fun patch(color: Color, across: Float, down: Float): DrawScope.() -> Unit {
        val at = Offset(width * across, width * down)
        val brush = Brush.radialGradient(listOf(color.copy(alpha = GLASS_PAGE_COLOUR), Color.Transparent), center = at, radius = reach)
        return { drawCircle(brush, radius = reach, center = at) }
    }
    return listOf(
        patch(c.primary, 0.06f, 0.18f),
        patch(c.fill(Section.IPON), 1f, 0.66f),
        patch(c.fill(Section.UTANG), 0f, 1.22f),
        patch(c.fill(Section.KITA), 1f, 1.78f),
    )
}
