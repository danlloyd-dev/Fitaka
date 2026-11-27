package com.pitakaview.app.ui.motion

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.pitakaview.app.domain.DarkModePref
import com.pitakaview.app.domain.ThemeId
import com.pitakaview.app.domain.UiStyle
import com.pitakaview.app.ui.components.CHOICE_PIECE_TAG
import com.pitakaview.app.ui.components.FitakaSegmented
import com.pitakaview.app.ui.theme.FitakaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/**
 * The choice strip (Light / Dark / System, Small / Medium / Large, the four styles): its dark piece
 * slides from the old choice to the new one. At rest it is read from the pixels of a row above the words
 * (in the Plain style). On the way, with the clock held by hand, a picture of the screen is not drawn
 * again, so the piece is found by its name and asked where it is, stepping one frame at a time.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h200dp-xhdpi")
class ChoiceStripSlideTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    /** Where the dark piece starts and ends along the strip, as fractions of the strip's width, as drawn. */
    private fun drawnPiece(): ClosedFloatingPointRange<Float> {
        val pixels = rule.onRoot().captureToImage().toPixelMap()
        val strip = rule.onNodeWithTag("strip").fetchSemanticsNode().boundsInRoot
        with(rule.density) {
            // 8 down from the top is inside the outline and above the words. The sides skip the outline and the shadow.
            val y = (strip.top + 8.dp.toPx()).roundToInt()
            val from = (strip.left + 8.dp.toPx()).roundToInt()
            val to = (strip.right - 12.dp.toPx()).roundToInt()
            val dark = (from..to).filter { pixels[it, y].luminance() < 0.2f }
            assertTrue("no dark piece found on the strip", dark.isNotEmpty())
            val width = (to - from).toFloat()
            return (dark.first() - from) / width..(dark.last() - from) / width
        }
    }

    /** Where the piece's left edge is right now, whether or not the screen has been drawn again. */
    private fun pieceLeft(): Float = rule.onNodeWithTag(CHOICE_PIECE_TAG, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left

    @Test
    fun theDarkPieceSlidesFromTheOldChoiceToTheNew() {
        var chosen by mutableStateOf(0)
        rule.setContent {
            FitakaTheme(ThemeId.MANGO, DarkModePref.LIGHT, style = UiStyle.PLAIN) {
                Box(Modifier.background(FitakaTheme.colors.background).padding(16.dp)) {
                    FitakaSegmented(listOf("One", "Two", "Three"), chosen, { chosen = it }, Modifier.testTag("strip"))
                }
            }
        }
        rule.waitForIdle()
        val first = drawnPiece()
        assertEquals("at rest on the first choice: starts at the left", 0f, first.start, 0.02f)
        assertEquals("and is a third wide", 1f / 3, first.endInclusive, 0.04f)
        val from = pieceLeft()

        rule.mainClock.autoAdvance = false
        rule.onNode(hasText("Three") and isSelectable()).performClick()
        assertEquals(2, chosen)
        // One frame at a time, looking after each: with the clock held, a frame's work lands only when the
        // screen is next looked at, and one long step is a single frame, which only starts the movement.
        val seen = (1..10).map {
            rule.mainClock.advanceTimeByFrame()
            pieceLeft()
        }

        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        val to = pieceLeft()
        val way = to - from
        assertTrue("the third choice is to the right of the first ($from, then $to)", way > 100f)
        assertTrue("within ten frames the piece is somewhere between the two choices: $seen", seen.any { it > from + 4f && it < to - 4f })
        val steps = (listOf(from) + seen).zipWithNext { a, b -> b - a }
        assertTrue("it never jumps: no frame carries it more than a third of the way ($steps)", steps.all { it < way / 3 })
        assertTrue("and it does not turn back on the way there ($steps)", steps.all { it >= 0f })

        val last = drawnPiece()
        assertEquals("at rest on the third choice", 2f / 3, last.start, 0.04f)
        assertEquals(1f, last.endInclusive, 0.02f)
        rule.onNode(hasText("Three") and isSelectable()).assertIsSelected()
    }
}
