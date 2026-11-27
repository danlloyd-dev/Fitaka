package com.pitakaview.app.ui.icons

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.unit.dp
import com.pitakaview.app.ui.theme.Section
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandDrawnIconsTest {
    private fun penLines(icon: ImageVector) = icon.root.filterIsInstance<VectorPath>().filter { it.stroke != null }
    private fun shapes(icon: ImageVector) = icon.root.filterIsInstance<VectorPath>().filter { it.fill != null }

    @Test
    fun everyHandDrawnIconCanBeBuilt() {
        assertEquals(23, HandDrawn.all.size)
        assertEquals("each drawing is its own", 23, HandDrawn.all.distinct().size)
        for (icon in HandDrawn.all) {
            // Building the image reads its path data: a slip in it fails here, not on a screen.
            assertEquals("${icon.name} is drawn on the 24 by 24 grid", 24f, icon.viewportWidth)
            assertEquals(24f, icon.viewportHeight)
            assertTrue("${icon.name} has an outline to fill", shapes(icon).isNotEmpty())
            assertTrue(icon.isHandDrawn)
        }
    }

    /** The set's money icons carried a dollar sign. Each one's peso sign is four pen lines: stem, bowl, two bars. */
    @Test
    fun theMoneyIconsCarryAPesoSign() {
        assertEquals(4, penLines(HandDrawn.MoneyBag).size)
        assertEquals(4, penLines(HandDrawn.Receipt).size)
        assertEquals(4, penLines(HandDrawn.Banknote).size)
        assertEquals("two small signs above the pig", 8, penLines(HandDrawn.PiggyBank).size)
        assertEquals("the coin's own P, plus the two bars", 2, penLines(HandDrawn.PesoCoin).size)
        val drawnIn = setOf(HandDrawn.MoneyBag, HandDrawn.Receipt, HandDrawn.Banknote, HandDrawn.PiggyBank, HandDrawn.PesoCoin)
        for (icon in HandDrawn.all - drawnIn) assertTrue("${icon.name} has nothing drawn in", penLines(icon).isEmpty())
        for (line in drawnIn.flatMap(::penLines)) assertTrue("a pen line is thinner than the app's own strokes", line.strokeLineWidth in 0.5f..1f)
    }

    @Test
    fun theSectionsAndTheMainActionsAreHandDrawn() {
        for (section in Section.entries) assertTrue("${section.name}'s icon", section.icon.isHandDrawn)
        for (icon in listOf(FitakaIcons.Home, FitakaIcons.Wallet, FitakaIcons.Settings, FitakaIcons.Add, FitakaIcons.Search)) assertTrue(icon.name, icon.isHandDrawn)
        assertEquals("no two sections share a drawing", Section.entries.size, Section.entries.map { it.icon }.distinct().size)
    }

    /** The small arrows and the tick are controls: they stay plain strokes at the app's full line. */
    @Test
    fun theArrowsAndTheTickStayPlainStrokes() {
        for (icon in listOf(FitakaIcons.Back, FitakaIcons.Check, FitakaIcons.ChevronLeft, FitakaIcons.ChevronRight)) {
            assertFalse(icon.name, icon.isHandDrawn)
            assertTrue(penLines(icon).all { it.strokeLineWidth == STROKE })
        }
    }

    /** Fourteen category icons are hand-drawn; the set has nothing for the other eight, which get a lighter line. */
    @Test
    fun categoryIconsWithNoHandDrawnMatchGetALighterLine() {
        val (handDrawn, own) = CategoryIcons.all.partition { it.image.isHandDrawn }
        assertEquals(14, handDrawn.size)
        assertEquals(setOf("food", "coffee", "transport", "health", "family", "heart", "shirt", "gift"), own.map { it.key }.toSet())
        for (icon in own) assertTrue(icon.key, penLines(icon.image).isNotEmpty() && penLines(icon.image).all { it.strokeLineWidth == STROKE_BESIDE_HAND_DRAWN })
        assertTrue(STROKE_BESIDE_HAND_DRAWN < STROKE)
    }

    /** Hand-drawn icons have thinner lines, so they are shown larger; a stroke icon keeps its size. */
    @Test
    fun handDrawnIconsAreShownALittleLarger() {
        assertEquals(26.dp, FitakaIcons.Kita.sizeFor(22.dp))
        assertEquals(24.dp, FitakaIcons.Kita.sizeFor(22.dp, more = 2.dp))
        assertEquals(22.dp, FitakaIcons.Back.sizeFor(22.dp))
        assertEquals(26.dp, CategoryIcons.forKey("food").sizeFor(26.dp))
        assertEquals(30.dp, CategoryIcons.forKey("cart").sizeFor(26.dp))
    }
}
