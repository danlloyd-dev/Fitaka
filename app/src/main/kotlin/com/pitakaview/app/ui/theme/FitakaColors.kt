package com.pitakaview.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.pitakaview.app.domain.ThemeId

/**
 * Colours Material 3 has no slot for: ink, the offset shadow, and the section colours with their
 * pale tints. Read them with `FitakaTheme.colors`.
 *
 * Section colours are decoration: they always sit with an ink outline or ink text, never carry meaning
 * alone (Baon yellow is only 1.7:1 against a white card).
 */
@Immutable
data class FitakaColors(
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
    /** The round icon buttons: a pale share of the primary colour, not the card colour, so their gloss shows. */
    val iconButton: Color,
    /** The Home tab's dab in the bottom bar: the primary colour, as pale as a section's tint. */
    val homeTint: Color,
    private val fills: List<Color>,
    private val tints: List<Color>,
) {
    /** The bright section colour, for small elements: icons, chart bars, tags. */
    fun fill(section: Section): Color = fills[section.ordinal]

    /** A tint of the section colour, for card backgrounds and the bottom bar's dabs. */
    fun tint(section: Section): Color = tints[section.ordinal]

    /**
     * Secondary text on a section-tinted card: full ink. Muted ink on a tint is as low as 3.0:1 (yellow
     * in dark mode); full ink keeps every caption there at 4.5:1 or better (ThemeContrastTest).
     * Use this, not [inkMuted], for any small text drawn on [tint] (TintedCardCaptionsTest reads the screens).
     */
    val captionOnTint: Color get() = ink

    /**
     * These colours for the Glassy style: secondary text a little darker. A see-through card over a
     * patch of colour is not as plain a ground as the card colour, and at the usual 72% the text fell
     * to 3.8:1 on it; at 80% it keeps 4.5 everywhere (ThemeContrastTest).
     */
    fun forGlass(): FitakaColors = copy(inkMuted = mix(ink, card, MUTED_INK_ON_GLASS))
}

/**
 * Share of section colour mixed into the card colour for tints, and of ink for muted text.
 * The tints were 18% and 24% before the gloss: the streak of light on a tile needs colour to show on.
 */
internal const val TINT_LIGHT = 0.30f
internal const val TINT_DARK = 0.34f
internal const val MUTED_INK = 0.72f
internal const val MUTED_INK_ON_GLASS = 0.80f

/** Share of the primary colour in a round icon button. */
internal const val ICON_BUTTON_TINT = 0.26f

fun fitakaColors(theme: ThemeId, dark: Boolean): FitakaColors {
    val base = Palette.base(theme, dark)
    val tintShare = if (dark) TINT_DARK else TINT_LIGHT
    val fills = Section.entries.map { Palette.section(it, dark) }
    return FitakaColors(
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
        iconButton = mix(base.primary, base.card, ICON_BUTTON_TINT),
        homeTint = mix(base.primary, base.card, tintShare),
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

val LocalFitakaColors = staticCompositionLocalOf { fitakaColors(ThemeId.MANGO, dark = false) }
