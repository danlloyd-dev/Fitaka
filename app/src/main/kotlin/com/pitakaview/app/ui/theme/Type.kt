package com.pitakaview.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pitakaview.app.R

/**
 * Shantell Sans (SIL OFL 1.1, licence in assets/licenses): drawn like a marker pen, to go with the
 * hand-drawn icons. Has ₱ and ñ, which Patrick Hand, Gaegu, Kalam, Playpen Sans and Fredoka lack.
 *
 * The three files are cut down to the Latin letters, figures, punctuation and money signs the app shows
 * (181 KB each, from 393 KB); a letter outside them is drawn in the phone's own font.
 */
val ShantellSans = FontFamily(
    Font(R.font.shantell_sans_regular, FontWeight.Normal),
    Font(R.font.shantell_sans_semibold, FontWeight.SemiBold),
    Font(R.font.shantell_sans_bold, FontWeight.Bold),
)

/** Figures of equal width, so amounts under one another line up digit by digit. */
internal const val EQUAL_WIDTH_FIGURES = "tnum"

private fun shantell(size: Int, lineHeight: Int, weight: FontWeight) = TextStyle(
    fontFamily = ShantellSans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontFeatureSettings = EQUAL_WIDTH_FIGURES,
)

val PitakaTypography = Typography(
    displayLarge = shantell(52, 60, FontWeight.Bold),
    displayMedium = shantell(42, 50, FontWeight.Bold),
    displaySmall = shantell(34, 42, FontWeight.Bold),
    headlineLarge = shantell(30, 38, FontWeight.Bold),
    headlineMedium = shantell(26, 34, FontWeight.Bold),
    headlineSmall = shantell(23, 30, FontWeight.Bold),
    titleLarge = shantell(21, 28, FontWeight.Bold),
    titleMedium = shantell(18, 24, FontWeight.SemiBold),
    titleSmall = shantell(16, 22, FontWeight.SemiBold),
    bodyLarge = shantell(17, 24, FontWeight.Normal),
    bodyMedium = shantell(15, 21, FontWeight.Normal),
    bodySmall = shantell(13, 18, FontWeight.Normal),
    labelLarge = shantell(16, 22, FontWeight.SemiBold),
    labelMedium = shantell(14, 18, FontWeight.SemiBold),
    labelSmall = shantell(12, 16, FontWeight.SemiBold),
)

/**
 * Button labels. 19sp bold is WCAG "large text" (≥ 14pt bold), where AA needs 3:1 — this is what
 * lets the Ube and Kape light primaries (4.1–4.3:1) pass. Do not shrink text that sits on primary.
 */
val ButtonLabelStyle = shantell(19, 24, FontWeight.Bold)
