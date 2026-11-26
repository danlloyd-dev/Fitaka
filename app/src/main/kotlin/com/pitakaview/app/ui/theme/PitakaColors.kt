package com.pitakaview.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.pitakaview.app.domain.ThemeId

/**
 * Colours Material 3 has no slot for: ink, the offset shadow, and the section colours with their
 * pale tints. Read them with `PitakaTheme.colors`.
 *
 * Section colours are decoration: they always sit with an ink outline or ink text, never carry meaning
 * alone (Baon yellow is only 1.7:1 against a white card).
 */
@Immutable
data class PitakaColors(
    val theme: ThemeId,
    val isDark: Boolean,
    val background: Color,
    val card: Color,
    /** Text and outlines. */
    val ink: Color,
    /** Secondary text: ink softened toward the card. */
    val inkMuted: Color,
    /** Solid offset shadow: ink in light mode, pure black in dark mode. */
    val shadow: Color,
    /** Only the main action on a screen uses this. */
    val primary: Color,
    val onPrimary: Color,
    /** Icons and text drawn on a solid section colour. */
    val onSectionFill: Color,
    private val fills: List<Color>,
    private val tints: List<Color>,
) {
    /** The bright section colour, for small elements: icons, chart bars, tags. */
    fun fill(section: Section): Color = fills[section.ordinal]

    /** A pale tint of the section colour, for card backgrounds. */
    fun tint(section: Section): Color = tints[section.ordinal]
}

/** Share of section colour mixed into the card colour for tints, and of ink for muted text. */
internal const val TINT_LIGHT = 0.18f
internal const val TINT_DARK = 0.24f
internal const val MUTED_INK = 0.72f

fun pitakaColors(theme: ThemeId, dark: Boolean): PitakaColors {
    val base = Palette.base(theme, dark)
    val tintShare = if (dark) TINT_DARK else TINT_LIGHT
    val fills = Section.entries.map { Palette.section(it, dark) }
    return PitakaColors(
        theme = theme,
        isDark = dark,
        background = base.background,
        card = base.card,
        ink = base.ink,
        inkMuted = mix(base.ink, base.card, MUTED_INK),
        shadow = if (dark) Color.Black else base.ink,
        primary = base.primary,
        onPrimary = base.onPrimary,
        onSectionFill = if (dark) base.background else base.ink,
        fills = fills,
        tints = fills.map { mix(it, base.card, tintShare) },
    )
}

/** Plain sRGB mix: [fraction] of [top] over [bottom]. Matches the contrast analysis exactly. */
internal fun mix(top: Color, bottom: Color, fraction: Float): Color = Color(
    red = top.red * fraction + bottom.red * (1 - fraction),
    green = top.green * fraction + bottom.green * (1 - fraction),
    blue = top.blue * fraction + bottom.blue * (1 - fraction),
    alpha = 1f,
)

val LocalPitakaColors = staticCompositionLocalOf { pitakaColors(ThemeId.MANGO, dark = false) }
