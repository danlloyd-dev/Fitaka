package com.pitakaview.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pitakaview.app.ui.theme.PitakaTheme
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
    val alpha = if (PitakaTheme.colors.isDark) GLASS_TINT_DARK else GLASS_TINT_LIGHT
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
