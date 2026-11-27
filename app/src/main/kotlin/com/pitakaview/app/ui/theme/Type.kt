package com.pitakaview.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pitakaview.app.R
import com.pitakaview.app.domain.FontChoice

/**
 * Shantell Sans (SIL OFL 1.1, licence in assets/licenses): drawn like a marker pen, to go with the
 * hand-drawn icons. Has ₱ and ñ, which Patrick Hand, Gaegu, Kalam, Playpen Sans and Fredoka lack.
 *
 * Every font file is cut down to the Latin letters, figures, punctuation and money signs the app shows;
 * a letter outside them is drawn in the phone's own font.
 */
val ShantellSans = FontFamily(
    Font(R.font.shantell_sans_regular, FontWeight.Normal),
    Font(R.font.shantell_sans_semibold, FontWeight.SemiBold),
    Font(R.font.shantell_sans_bold, FontWeight.Bold),
)

/** The app's first font, still offered. */
val Mali = FontFamily(
    Font(R.font.mali_regular, FontWeight.Normal),
    Font(R.font.mali_semibold, FontWeight.SemiBold),
    Font(R.font.mali_bold, FontWeight.Bold),
)

/** Pangolin and Itim exist in one weight; the phone thickens them where the app asks for bold. */
val Pangolin = FontFamily(Font(R.font.pangolin_regular, FontWeight.Normal))
val Itim = FontFamily(Font(R.font.itim_regular, FontWeight.Normal))

/** Fuzzy Bubbles has regular and bold; semibold text takes the bold file. */
val FuzzyBubbles = FontFamily(
    Font(R.font.fuzzy_bubbles_regular, FontWeight.Normal),
    Font(R.font.fuzzy_bubbles_bold, FontWeight.SemiBold),
    Font(R.font.fuzzy_bubbles_bold, FontWeight.Bold),
)

/** The font files behind each choice in Settings. */
fun FontChoice.family(): FontFamily = when (this) {
    FontChoice.SHANTELL -> ShantellSans
    FontChoice.MALI -> Mali
    FontChoice.PANGOLIN -> Pangolin
    FontChoice.FUZZY_BUBBLES -> FuzzyBubbles
    FontChoice.ITIM -> Itim
}

/** Figures of equal width, so amounts under one another line up digit by digit (in the fonts that have them). */
internal const val EQUAL_WIDTH_FIGURES = "tnum"

private fun style(family: FontFamily, size: Int, lineHeight: Int, weight: FontWeight) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontFeatureSettings = EQUAL_WIDTH_FIGURES,
)

/** The app's type sizes in one font. */
fun fitakaTypography(family: FontFamily) = Typography(
    displayLarge = style(family, 52, 60, FontWeight.Bold),
    displayMedium = style(family, 42, 50, FontWeight.Bold),
    displaySmall = style(family, 34, 42, FontWeight.Bold),
    headlineLarge = style(family, 30, 38, FontWeight.Bold),
    headlineMedium = style(family, 26, 34, FontWeight.Bold),
    headlineSmall = style(family, 23, 30, FontWeight.Bold),
    titleLarge = style(family, 21, 28, FontWeight.Bold),
    titleMedium = style(family, 18, 24, FontWeight.SemiBold),
    titleSmall = style(family, 16, 22, FontWeight.SemiBold),
    bodyLarge = style(family, 17, 24, FontWeight.Normal),
    bodyMedium = style(family, 15, 21, FontWeight.Normal),
    bodySmall = style(family, 13, 18, FontWeight.Normal),
    labelLarge = style(family, 16, 22, FontWeight.SemiBold),
    labelMedium = style(family, 14, 18, FontWeight.SemiBold),
    labelSmall = style(family, 12, 16, FontWeight.SemiBold),
)

val FitakaTypography = fitakaTypography(ShantellSans)

/**
 * Button labels. 19sp bold is WCAG "large text" (≥ 14pt bold), where AA needs 3:1 — this is what
 * lets the Ube and Kape light primaries (4.1–4.3:1) pass. Do not shrink text that sits on primary.
 * A button takes the font from the theme; this holds the size and weight.
 */
val ButtonLabelStyle = style(ShantellSans, 19, 24, FontWeight.Bold)
