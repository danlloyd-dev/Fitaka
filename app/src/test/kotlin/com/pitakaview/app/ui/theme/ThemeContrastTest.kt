package com.pitakaview.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.pitakaview.app.domain.ThemeId
import com.pitakaview.app.ui.components.GLASS_FILL
import com.pitakaview.app.ui.components.GLASS_PAGE_COLOUR
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

    /** The frosted top bar: even with solid ink scrolled right under the glass, its text must stay readable. */
    @Test
    fun theGlassTopBarStaysReadableInTheWorstCase() {
        for (c in all) {
            val alpha = if (c.isDark) com.pitakaview.app.ui.components.GLASS_TINT_DARK else com.pitakaview.app.ui.components.GLASS_TINT_LIGHT
            // All text on glass is full ink (title and subtitle): text needs 4.5.
            need(c, "text on top-bar glass", c.ink, mix(c.background, c.ink, alpha), 4.5)
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /**
     * The floating bottom bar is solid, in the card colour. Its words are full ink on the card; each
     * icon sits on a dab of its section's tint, and the chosen one on a solid tile.
     */
    @Test
    fun theFloatingBottomBarStaysReadable() {
        for (c in all) {
            need(c, "tab label on the bar", c.ink, c.card, 4.5)
            need(c, "icon on the Home dab", c.ink, c.homeTint, 3.0)
            need(c, "icon on the chosen Home tile", c.onPrimary, c.primary, 3.0)
            Section.entries.forEach {
                need(c, "icon on the ${it.label} dab", c.ink, c.tint(it), 3.0)
                need(c, "icon on the chosen ${it.label} tile", c.onSectionFill, c.fill(it), 3.0)
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /**
     * The tints grew stronger with the gloss (30% and 34% of the section colour). Muted ink on them falls
     * to 3.0:1, so small text on a tinted card is full ink ([PitakaColors.captionOnTint]): every caption there keeps 4.5.
     */
    @Test
    fun captionsOnTintedCardsAreFullInkAndMeetAA() {
        for (c in all) {
            Section.entries.forEach { need(c, "caption on ${it.label} tint", c.captionOnTint, c.tint(it), 4.5) }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
        // What the rule is for: without it, at least one caption would fall below AA.
        val unreadable = all.any { c -> Section.entries.any { ratio(c.inkMuted, c.tint(it)) < 4.5 } }
        assertTrue("muted ink would have been enough: the rule is not needed any more", unreadable)
    }

    /** Round icon buttons are a pale share of the primary colour: their ink icon is a graphic, 3.0. */
    @Test
    fun roundIconButtonsKeepTheirIconReadable() {
        for (c in all) need(c, "icon on a round button", c.ink, c.iconButton, 3.0)
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /**
     * Glassy: a card is partly its own colour and partly the page behind it, and the page carries up to
     * [GLASS_PAGE_COLOUR] of a patch of colour. Even over the strongest patch, the text on the card and
     * on the page itself keeps 4.5: full ink, and the secondary text at the 80% this style gives it
     * (at the usual 72% it was 3.8 on the page, which is why the style darkens it).
     */
    @Test
    fun glassyCardsStayReadableOverTheStrongestPatchOfColour() {
        for (c in all) {
            for (patch in listOf(c.primary, c.fill(Section.IPON), c.fill(Section.UTANG), c.fill(Section.KITA))) {
                val page = mix(patch, c.background, GLASS_PAGE_COLOUR)
                val card = mix(c.card, page, GLASS_FILL)
                need(c, "ink on a glass card", c.ink, card, 4.5)
                need(c, "muted ink on a glass card", c.forGlass().inkMuted, card, 4.5)
                need(c, "ink on the glassy page", c.ink, page, 4.5)
                need(c, "muted ink on the glassy page", c.forGlass().inkMuted, page, 4.5)
                for (section in Section.entries) need(c, "ink on a glass ${section.label} card", c.ink, mix(c.tint(section), page, GLASS_FILL), 4.5)
                need(c, "icon on a glass round button", c.ink, mix(c.iconButton, page, GLASS_FILL), 3.0)
            }
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
