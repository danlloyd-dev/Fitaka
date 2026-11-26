package com.pitakaview.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pitakaview.app.R

/** Mali (SIL OFL 1.1, licence in assets/licenses). Has ₱ and ñ, unlike Patrick Hand, Gaegu and Kalam. */
val Mali = FontFamily(
    Font(R.font.mali_regular, FontWeight.Normal),
    Font(R.font.mali_semibold, FontWeight.SemiBold),
    Font(R.font.mali_bold, FontWeight.Bold),
)

private fun mali(size: Int, lineHeight: Int, weight: FontWeight) =
    TextStyle(fontFamily = Mali, fontWeight = weight, fontSize = size.sp, lineHeight = lineHeight.sp)

val PitakaTypography = Typography(
    displayLarge = mali(52, 60, FontWeight.Bold),
    displayMedium = mali(42, 50, FontWeight.Bold),
    displaySmall = mali(34, 42, FontWeight.Bold),
    headlineLarge = mali(30, 38, FontWeight.Bold),
    headlineMedium = mali(26, 34, FontWeight.Bold),
    headlineSmall = mali(23, 30, FontWeight.Bold),
    titleLarge = mali(21, 28, FontWeight.Bold),
    titleMedium = mali(18, 24, FontWeight.SemiBold),
    titleSmall = mali(16, 22, FontWeight.SemiBold),
    bodyLarge = mali(17, 24, FontWeight.Normal),
    bodyMedium = mali(15, 21, FontWeight.Normal),
    bodySmall = mali(13, 18, FontWeight.Normal),
    labelLarge = mali(16, 22, FontWeight.SemiBold),
    labelMedium = mali(14, 18, FontWeight.SemiBold),
    labelSmall = mali(12, 16, FontWeight.SemiBold),
)

/**
 * Button labels. 19sp bold is WCAG "large text" (≥ 14pt bold), where AA needs 3:1 — this is what
 * lets the Ube and Kape light primaries (4.1–4.3:1) pass. Do not shrink text that sits on primary.
 */
val ButtonLabelStyle = mali(19, 24, FontWeight.Bold)
