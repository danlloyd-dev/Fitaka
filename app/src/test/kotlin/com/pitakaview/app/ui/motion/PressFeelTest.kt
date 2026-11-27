package com.pitakaview.app.ui.motion

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.pitakaview.app.domain.DarkModePref
import com.pitakaview.app.domain.ThemeId
import com.pitakaview.app.ui.components.FitakaButton
import com.pitakaview.app.ui.components.FitakaCard
import com.pitakaview.app.ui.theme.FitakaDimens
import com.pitakaview.app.ui.theme.FitakaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * How a press feels (the owner, 2026-11-27: "make it interactive and smooth"): a button, and a card that
 * can be tapped, go down into their shadow under the finger and come back up to exactly where they were;
 * a quick tap is still felt; a card that cannot be tapped does not move. The clock is held by hand, so
 * the part can be looked at while it is down and while it is on its way back.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h500dp-xhdpi")
class PressFeelTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var taps = 0

    private fun show() = rule.setContent {
        FitakaTheme(ThemeId.MANGO, DarkModePref.LIGHT) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                FitakaButton("Save", onClick = { taps++ }, modifier = Modifier.fillMaxWidth().testTag("button"))
                FitakaCard(Modifier.fillMaxWidth().testTag("card"), onClick = { taps++ }) { Text("A tile you can tap") }
                FitakaCard(Modifier.fillMaxWidth().testTag("still")) { Text("Only a card") }
            }
        }
    }

    /** Where the part's own edge is: a tappable part's bounds move with it. */
    private fun box(tag: String): Rect = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    @Test
    fun aButtonAndATappableCardSinkIntoTheirShadowAndComeBackExactly() {
        show()
        rule.waitForIdle()
        val depth = with(rule.density) { FitakaDimens.shadow.toPx() }
        for (tag in listOf("button", "card")) {
            val rest = box(tag)
            rule.mainClock.autoAdvance = false
            rule.onNodeWithTag(tag).performTouchInput { down(center) }
            rule.mainClock.advanceTimeBy(250)
            val held = box(tag)
            assertEquals("$tag held: down by the depth of its shadow", depth, held.top - rest.top, 1f)
            assertEquals("$tag held: and across by the same", depth, held.left - rest.left, 1f)

            rule.onNodeWithTag(tag).performTouchInput { up() }
            rule.mainClock.advanceTimeBy(48)
            val rising = box(tag)
            assertTrue("$tag let go: on its way back up after 48 ms (${rising.top} between ${rest.top} and ${held.top})", rising.top < held.top)

            rule.mainClock.autoAdvance = true
            rule.waitForIdle()
            assertEquals("$tag at rest again: exactly where it was", rest, box(tag))
        }
        assertEquals("each was tapped once", 2, taps)
    }

    @Test
    fun aQuickTapIsStillFelt() {
        show()
        rule.waitForIdle()
        val rest = box("button")
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("button").performClick()
        rule.mainClock.advanceTimeBy(64)
        assertTrue("64 ms after a quick tap the button is down (${box("button").top} against ${rest.top})", box("button").top > rest.top + 1f)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertEquals(rest, box("button"))
        assertEquals(1, taps)
    }

    @Test
    fun aCardThatCannotBeTappedDoesNotMove() {
        show()
        rule.waitForIdle()
        val rest = rule.onNodeWithTag("still").fetchSemanticsNode().positionInRoot
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("still").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(250)
        assertEquals(rest, rule.onNodeWithTag("still").fetchSemanticsNode().positionInRoot)
        rule.onNodeWithTag("still").performTouchInput { up() }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertEquals(0, taps)
    }
}
