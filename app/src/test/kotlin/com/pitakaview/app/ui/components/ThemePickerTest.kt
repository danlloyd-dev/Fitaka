package com.pitakaview.app.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.pitakaview.app.domain.DarkModePref
import com.pitakaview.app.domain.ThemeId
import com.pitakaview.app.ui.theme.PitakaDimens
import com.pitakaview.app.ui.theme.PitakaTheme
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
 * The theme picker shows the chosen theme by popping its swatch up, with no tick badge on it (the owner
 * asked for that on 2026-11-26). Read from the pixels: the chosen swatch's top edge is [SwatchLift] above
 * the others', and where the badge was there is no ink.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h200dp-xhdpi")
class ThemePickerTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val shown = listOf(ThemeId.MANGO, ThemeId.UBE, ThemeId.CALAMANSI)

    private fun swatch(theme: ThemeId) = rule.onNode(hasText(theme.label) and isSelectable())

    private fun PixelMap.inkAt(x: Int, y: Int) = this[x, y].luminance() < 0.2f

    @Test
    fun theChosenThemePopsUpAndCarriesNoTick() {
        var chosen by mutableStateOf(ThemeId.MANGO)
        rule.setContent {
            PitakaTheme(ThemeId.MANGO, DarkModePref.LIGHT) {
                Box(Modifier.background(PitakaTheme.colors.background).padding(16.dp)) { ThemePicker(chosen, { chosen = it }) }
            }
        }
        fun check() {
            rule.waitForIdle()
            val pixels = rule.onRoot().captureToImage().toPixelMap()
            with(rule.density) {
                // Down the middle of each swatch, how far from the top of its column the outline starts.
                val tops = shown.associateWith { theme ->
                    val box = swatch(theme).fetchSemanticsNode().boundsInRoot
                    val x = box.center.x.roundToInt()
                    (0..40.dp.toPx().roundToInt()).first { pixels.inkAt(x, box.top.roundToInt() + it) }
                }
                for (theme in shown) {
                    val expected = if (theme == chosen) 0f else SwatchLift.toPx()
                    assertEquals("${theme.label} with ${chosen.label} chosen: where its swatch starts ($tops)", expected, tops.getValue(theme).toFloat(), 2f)
                }
                // Where the badge was: 14 in from the swatch's right edge, 14 down from its top.
                val box = swatch(chosen).fetchSemanticsNode().boundsInRoot
                val x = (box.right - (PitakaDimens.shadow + 14.dp).toPx()).roundToInt()
                val y = (box.top + 14.dp.toPx()).roundToInt()
                val around = (-3..3).any { dx -> (-3..3).any { dy -> pixels.inkAt(x + dx, y + dy) } }
                assertTrue("${chosen.label}: there is still ink where the tick badge was", !around)
            }
            swatch(chosen).assertIsSelected()
        }
        check()
        chosen = ThemeId.UBE
        check()
        // Tapping another one makes it the chosen one, and it is the one that rises.
        swatch(ThemeId.CALAMANSI).performClick()
        assertEquals(ThemeId.CALAMANSI, chosen)
        check()
    }

    /**
     * The raised swatch is all that says which theme is on, and six swatches do not fit across a phone:
     * the last one, when chosen, is on the screen from the start, not off its right edge.
     */
    @Test
    fun aChosenThemeFarAlongTheRowIsInViewFromTheStart() {
        rule.setContent {
            PitakaTheme(ThemeId.ROSAS, DarkModePref.LIGHT) {
                Box(Modifier.background(PitakaTheme.colors.background).padding(16.dp)) { ThemePicker(ThemeId.ROSAS, {}) }
            }
        }
        rule.waitForIdle()
        // Where the swatch is laid out, not boundsInRoot: that is cut to what shows, so a swatch off the
        // screen's edge would report an empty box that looks as if it fits.
        val node = swatch(ThemeId.ROSAS).assertIsSelected().assertIsDisplayed().fetchSemanticsNode()
        val left = node.positionInRoot.x
        val right = left + node.size.width
        val screen = rule.onRoot().fetchSemanticsNode().size.width
        assertTrue("the chosen swatch is wholly on the screen: $left to $right of $screen", left >= 0f && right <= screen)
        // And the first theme, when chosen, leaves the row where it starts.
        assertTrue(ThemeId.entries.first() == ThemeId.MANGO && ThemeId.entries.last() == ThemeId.ROSAS)
    }
}
