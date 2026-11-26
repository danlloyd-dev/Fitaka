package com.pitakaview.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pitakaview.app.domain.DarkModePref
import com.pitakaview.app.domain.FontChoice
import com.pitakaview.app.domain.UiSize
import com.pitakaview.app.domain.UiStyle
import com.pitakaview.app.domain.ThemeId

/** Sizes shared by the sketch-style components. */
object PitakaDimens {
    val border = 2.5.dp
    val borderThin = 2.dp
    val shadow = 4.dp
    val cornerLarge = 18.dp
    val corner = 14.dp
    val cornerSmall = 10.dp
}

val PitakaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(PitakaDimens.cornerSmall),
    medium = RoundedCornerShape(PitakaDimens.corner),
    large = RoundedCornerShape(PitakaDimens.cornerLarge),
    extraLarge = RoundedCornerShape(24.dp),
)

/**
 * Applies a theme app-wide: a Material 3 ColorScheme (so stock components such as dialogs and pickers
 * match) plus [PitakaColors] through [LocalPitakaColors].
 */
@Composable
fun PitakaTheme(
    theme: ThemeId = ThemeId.MANGO,
    darkMode: DarkModePref = DarkModePref.SYSTEM,
    font: FontChoice = FontChoice.SHANTELL,
    style: UiStyle = UiStyle.GLOSSY,
    size: UiSize = UiSize.LARGE,
    content: @Composable () -> Unit,
) {
    val dark = darkMode.resolve()
    val colors = remember(theme, dark, style) { pitakaColors(theme, dark).let { if (style == UiStyle.GLASSY) it.forGlass() else it } }
    val scheme = remember(colors) { colors.toColorScheme() }
    val typography = remember(font) { pitakaTypography(font.family()) }
    CompositionLocalProvider(LocalPitakaColors provides colors, LocalUiStyle provides style) {
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = PitakaShapes) {
            // Everything inside is drawn at the chosen size; the bottom bar steps back out of it (FullSize).
            ScaledUi(size, content)
        }
    }
}

/** Whether this preference means dark mode right now. */
@Composable
fun DarkModePref.resolve(): Boolean = when (this) {
    DarkModePref.SYSTEM -> isSystemInDarkTheme()
    DarkModePref.LIGHT -> false
    DarkModePref.DARK -> true
}

object PitakaTheme {
    val colors: PitakaColors
        @Composable @ReadOnlyComposable
        get() = LocalPitakaColors.current
}

/** The theme pair as Material 3 schemes: flat surfaces (no tonal tint), ink outlines, Gastos red for errors. */
fun PitakaColors.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = mix(primary, card, 0.25f),
        onPrimaryContainer = ink,
        inversePrimary = primary,
        secondary = ink,
        onSecondary = card,
        secondaryContainer = mix(ink, card, 0.12f),
        onSecondaryContainer = ink,
        tertiary = fill(Section.IPON),
        onTertiary = onSectionFill,
        tertiaryContainer = tint(Section.IPON),
        onTertiaryContainer = ink,
        background = background,
        onBackground = ink,
        surface = card,
        onSurface = ink,
        surfaceVariant = background,
        onSurfaceVariant = inkMuted,
        surfaceTint = card,
        inverseSurface = ink,
        inverseOnSurface = background,
        error = fill(Section.GASTOS),
        onError = onSectionFill,
        errorContainer = tint(Section.GASTOS),
        onErrorContainer = ink,
        outline = ink,
        outlineVariant = mix(ink, card, 0.3f),
        scrim = Color.Black,
        surfaceBright = card,
        surfaceDim = background,
        surfaceContainerLowest = card,
        surfaceContainerLow = card,
        surfaceContainer = card,
        surfaceContainerHigh = card,
        surfaceContainerHighest = card,
    )
}
