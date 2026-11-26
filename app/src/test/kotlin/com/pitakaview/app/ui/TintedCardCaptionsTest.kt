package com.pitakaview.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import com.pitakaview.app.domain.DarkModePref
import com.pitakaview.app.domain.ThemeId
import com.pitakaview.app.ui.home.HomeContent
import com.pitakaview.app.ui.home.HomeState
import com.pitakaview.app.ui.ledger.MonthLedger
import com.pitakaview.app.ui.ledger.MonthTotalCard
import com.pitakaview.app.ui.sections.SectionTotalsCard
import com.pitakaview.app.ui.theme.PitakaTheme
import com.pitakaview.app.ui.theme.Section
import com.pitakaview.app.ui.theme.pitakaColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.YearMonth

/**
 * The small captions on the section-tinted cards are full ink: muted ink on the stronger tints is as low
 * as 3.0:1. ThemeContrastTest holds the colours to 4.5; this reads the colour each caption is really
 * drawn in, on the screens themselves.
 *
 * It was written after a first fix that looked right in the code and changed nothing on the screen: a
 * rule set inside the card never reached the captions, because each tile had already read its colours.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h2400dp-xhdpi")
class TintedCardCaptionsTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun SemanticsNodeInteraction.drawnColor(): Color {
        val results = mutableListOf<TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single().layoutInput.style.color
    }

    /**
     * The colour of the one piece of text that says [text]. Unmerged, on purpose: a tile that can be
     * tapped is one node to a screen reader, and asking that node for its text gives its title, in ink,
     * whatever colour the caption under it has. (The first version of this test did that and passed on
     * a grey caption.)
     */
    private fun caption(text: String) = rule.onNode(hasText(text), useUnmergedTree = true).drawnColor()

    @Test
    fun theCaptionsOnHomesTilesAreFullInkInLightAndInDark() {
        // One screen, switched from light to dark while it is up, as the owner would in Settings.
        var shown by mutableStateOf(DarkModePref.LIGHT)
        rule.setContent {
            PitakaTheme(ThemeId.DAGAT, shown) { HomeContent(HomeState(baonLeftCentavos = 38_000), LocalDate.of(2026, 11, 26), "Magandang hapon!", {}, {}, {}) }
        }
        for (mode in listOf(DarkModePref.LIGHT, DarkModePref.DARK)) {
            shown = mode
            rule.waitForIdle()
            val c = pitakaColors(ThemeId.DAGAT, dark = mode == DarkModePref.DARK)
            rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Owed to you"))
            for (text in listOf("Income", "Spent", "Saved", "Left", "You owe", "Owed to you")) {
                assertEquals("\"$text\" on its tile, $mode", c.ink, caption(text))
            }
            // The same screen's caption on a white card stays muted: only the tinted cards changed.
            assertNotEquals(c.ink, c.inkMuted)
            assertEquals(c.inkMuted, caption("Your whole wallet"))
        }
    }

    @Test
    fun theCaptionsOnASectionsTotalsAndOnAMonthsTotalAreFullInk() {
        val c = pitakaColors(ThemeId.KAPE, dark = true)
        rule.setContent {
            PitakaTheme(ThemeId.KAPE, DarkModePref.DARK) {
                Column {
                    SectionTotalsCard(Section.BAON, listOf("Baon left" to 38_000L))
                    MonthTotalCard(Section.GASTOS, "Spent in November", MonthLedger(YearMonth.of(2026, 11), isThisMonth = true, entryCount = 5, loaded = true))
                }
            }
        }
        assertEquals(c.ink, caption("Baon left"))
        assertEquals(c.ink, caption("5 entries"))
    }
}
