package com.pitakaview.app.ui.components

import com.pitakaview.app.domain.ThemeId
import com.pitakaview.app.ui.theme.Section
import com.pitakaview.app.ui.theme.fitakaColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The gloss is full on bright fills and soft on dark ones. Which one a part gets is decided from its
 * fill colour alone, so it has to come out right for every theme, light and dark.
 */
class GlossTest {
    private val all = ThemeId.entries.flatMap { theme -> listOf(false, true).map { fitakaColors(theme, it) } }

    @Test
    fun solidColoursAlwaysTakeTheFullGloss() {
        for (c in all) {
            val where = "${c.theme} ${if (c.isDark) "dark" else "light"}"
            assertFalse("$where primary", glossIsSoft(c.primary))
            Section.entries.forEach { assertFalse("$where ${it.name}", glossIsSoft(c.fill(it))) }
        }
    }

    /** Ink is dark in light mode and pale in dark mode; the card and the round buttons are the other way round. */
    @Test
    fun inkAndSurfacesTakeTheSoftGlossOnlyWhereTheyAreDark() {
        for (c in all) {
            val where = "${c.theme} ${if (c.isDark) "dark" else "light"}"
            assertEquals("$where ink (a chosen chip, a switch that is on)", !c.isDark, glossIsSoft(c.ink))
            assertEquals("$where card (a plain button)", c.isDark, glossIsSoft(c.card))
            assertEquals("$where round icon button", c.isDark, glossIsSoft(c.iconButton))
            assertEquals("$where page (a theme swatch, a wallet's badge)", c.isDark, glossIsSoft(c.background))
        }
    }
}
