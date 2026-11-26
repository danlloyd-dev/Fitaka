package com.pitakaview.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.pitakaview.app.domain.ThemeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * WCAG 2.x contrast for every theme × mode, so a palette edit that breaks readability fails the build.
 * AA: 4.5 for body text, 3.0 for large/bold text (button labels) and for graphics such as outlines and icons.
 */
class ThemeContrastTest {
    private val all = ThemeId.entries.flatMap { theme -> listOf(false, true).map { pitakaColors(theme, it) } }
    private val failures = mutableListOf<String>()

    private fun luminance(c: Color): Double {
        fun channel(v: Float): Double = v.toDouble().let { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    }

    private fun ratio(a: Color, b: Color): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun need(colors: PitakaColors, what: String, fg: Color, bg: Color, min: Double) {
        val r = ratio(fg, bg)
        if (r < min) failures += "${colors.theme}/${if (colors.isDark) "dark" else "light"} $what = ${"%.2f".format(r)} < $min"
    }

    @Test
    fun textMeetsAA() {
        for (c in all) {
            need(c, "ink on background", c.ink, c.background, 4.5)
            need(c, "ink on card", c.ink, c.card, 4.5)
            need(c, "muted ink on card", c.inkMuted, c.card, 4.5)
            need(c, "muted ink on background", c.inkMuted, c.background, 4.5)
            Section.entries.forEach { need(c, "ink on ${it.label} tint", c.ink, c.tint(it), 4.5) }
            // Button labels on primary are 19sp bold = WCAG large text.
            need(c, "button label on primary", c.onPrimary, c.primary, 3.0)
            // Selected segment in DarkModePicker: card-coloured text on an ink fill.
            need(c, "card text on ink", c.card, c.ink, 4.5)
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun graphicsMeetAA() {
        for (c in all) {
            need(c, "outline on background", c.ink, c.background, 3.0)
            need(c, "outline on card", c.ink, c.card, 3.0)
            Section.entries.forEach { need(c, "icon on ${it.label} fill", c.onSectionFill, c.fill(it), 3.0) }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /** Frosted bars: even with solid ink scrolled right under the glass, bar text must stay readable. */
    @Test
    fun glassBarsStayReadableInTheWorstCase() {
        for (c in all) {
            val alpha = if (c.isDark) com.pitakaview.app.ui.components.GLASS_TINT_DARK else com.pitakaview.app.ui.components.GLASS_TINT_LIGHT
            val topBar = mix(c.background, c.ink, alpha)
            val bottomBar = mix(c.card, c.ink, alpha)
            // All text on glass is full ink (title, subtitle, every tab label): text needs 4.5.
            need(c, "text on top-bar glass", c.ink, topBar, 4.5)
            need(c, "tab label on bottom-bar glass", c.ink, bottomBar, 4.5)
            // Unselected tab icons are muted ink: graphics need 3.0.
            need(c, "unselected tab icon on bottom-bar glass", c.inkMuted, bottomBar, 3.0)
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun shadowIsInkInLightAndPureBlackInDark() {
        for (c in all) assertEquals(if (c.isDark) Color.Black else c.ink, c.shadow)
    }

    @Test
    fun materialSchemeCarriesThePalette() {
        for (c in all) {
            val s = c.toColorScheme()
            assertEquals(c.primary, s.primary)
            assertEquals(c.onPrimary, s.onPrimary)
            assertEquals(c.background, s.background)
            assertEquals(c.card, s.surface)
            assertEquals(c.ink, s.onSurface)
            assertEquals(c.ink, s.outline)
            assertEquals(c.card, s.surfaceTint) // no tonal tint on elevated surfaces
        }
    }
}
