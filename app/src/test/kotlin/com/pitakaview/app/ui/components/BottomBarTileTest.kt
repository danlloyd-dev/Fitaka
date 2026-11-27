package com.pitakaview.app.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.pitakaview.app.domain.DarkModePref
import com.pitakaview.app.domain.ThemeId
import com.pitakaview.app.ui.navigation.MainTabs
import com.pitakaview.app.ui.theme.FitakaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/**
 * The chosen tab's tile stands out of the top of the floating bar, across the bar's outline. On the
 * owner's phone (2026-11-26) the outline was drawn over the tile: a black line cut through it. The app
 * draws an outline after a part's contents, so the bar's outline landed on top of the tile inside it.
 *
 * This reads the drawn pixels: down a line inside the tile there is no ink, while the same outline is
 * there, in ink, between two tabs that are not chosen.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h300dp-xhdpi")
class BottomBarTileTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theBarsOutlineIsNotDrawnAcrossTheChosenTabsTile() {
        var chosen by mutableIntStateOf(0)
        rule.setContent {
            FitakaTheme(ThemeId.MANGO, DarkModePref.LIGHT) {
                Box(Modifier.fillMaxSize().background(FitakaTheme.colors.background), contentAlignment = Alignment.BottomCenter) {
                    FitakaBottomBar(MainTabs, chosen, onSelect = { chosen = it })
                }
            }
        }
        // Home sits on the bar's rounded corner, Ipon on its straight top edge: both must be clean.
        for ((index, label) in listOf(0 to "Home", 3 to "Ipon")) {
            chosen = index
            rule.waitForIdle()
            val pixels = rule.onRoot().captureToImage().toPixelMap()
            val tab = rule.onNode(hasText(label)).fetchSemanticsNode().boundsInRoot
            with(rule.density) {
                // The tile is 48 by 44, centred on its tab, from 16 above the row of dabs to 28 below its top.
                // The line read is 9 in from its left edge: clear of the tile's own outline where its corners
                // round (7 in grazed it), and clear of the icon in its middle.
                val x = (tab.center.x - 24.dp.toPx() + 9.dp.toPx()).roundToInt()
                val from = (tab.top - 16.dp.toPx() + 6.dp.toPx()).roundToInt()
                val to = (tab.top + 28.dp.toPx() - 6.dp.toPx()).roundToInt()
                val inked = (from..to).filter { y -> pixels[x, y].luminance() < 0.25f }
                assertTrue("$label's tile has ink across it at x=$x, y=${inked.firstOrNull()}..${inked.lastOrNull()} (${inked.size} px)", inked.isEmpty())

                // The bar's top outline is 8.5 above the row of dabs and 2.5 thick. Between two other tabs it is there.
                val other = rule.onNode(hasText("Baon")).fetchSemanticsNode().boundsInRoot
                val outline = pixels[other.right.roundToInt(), (other.top - 8.5.dp.toPx() + 1.25.dp.toPx()).roundToInt()]
                assertTrue("the bar's outline should be ink between Baon and Ipon, but is $outline", index == 3 || outline.luminance() < 0.1f)
            }
        }
    }
}
