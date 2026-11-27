package com.pitakaview.app.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.pitakaview.app.domain.DarkModePref
import com.pitakaview.app.domain.ThemeId
import com.pitakaview.app.ui.navigation.MainTabs
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
 * The bottom tab bar sits on the bottom of the screen, from one side to the other. The owner, 2026-11-27:
 * "dont make the whole container float. stick it to the bottom". Until then it was a card standing 16 in
 * from each side and 14 up from the bottom, with the page showing all round it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h850dp-xhdpi")
class BottomBarDockedTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    /** A page with cards on it and the bar at the bottom, as the app lays them out. */
    private fun page(theme: ThemeId, mode: DarkModePref, colours: (card: Color, background: Color) -> Unit = { _, _ -> }) {
        var chosen by mutableIntStateOf(3)
        rule.setContent {
            FitakaTheme(theme, mode) {
                val c = FitakaTheme.colors
                colours(c.card, c.background)
                Box(Modifier.fillMaxSize().background(c.background)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        repeat(6) { FitakaCard(Modifier.fillMaxWidth().height(110.dp)) { Text("A card on the page", color = c.ink) } }
                    }
                    FitakaBottomBar(MainTabs, chosen, onSelect = { chosen = it }, modifier = Modifier.align(Alignment.BottomCenter))
                }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun theBarRunsFromSideToSideAndDownToTheBottomEdge() {
        var card = Color.Unspecified
        var background = Color.Unspecified
        page(ThemeId.MANGO, DarkModePref.LIGHT) { c, b -> card = c; background = b }
        assertTrue("the test needs the bar's colour to differ from the page's", card.toArgb() != background.toArgb())
        val pixels = rule.onRoot().captureToImage().toPixelMap()
        val label = rule.onNode(hasText("Kita")).fetchSemanticsNode().boundsInRoot
        val y = label.center.y.roundToInt()
        val bottom = pixels.height - 1
        // Beside the labels, at each side, and along the very bottom row: the bar's own colour, not the page.
        for ((where, x, row) in listOf(Triple("left edge, level with the labels", 0, y), Triple("right edge", pixels.width - 1, y),
            Triple("bottom left corner", 0, bottom), Triple("bottom right corner", pixels.width - 1, bottom), Triple("bottom middle", pixels.width / 2, bottom))) {
            assertEquals("$where: the bar's colour", card.toArgb(), pixels[x, row].toArgb())
        }
    }

    @Test
    fun anInkLineRunsAlongTheBarsTopFromSideToSide() {
        page(ThemeId.MANGO, DarkModePref.LIGHT)
        val pixels = rule.onRoot().captureToImage().toPixelMap()
        val baon = rule.onNode(hasText("Baon")).fetchSemanticsNode().boundsInRoot
        with(rule.density) {
            // A tab's bounds start at its row of dabs; the line is the bar's top 2.5, 8.5 above that row.
            val lineY = (baon.top - 8.5.dp.toPx() + 1.25.dp.toPx()).roundToInt()
            for (x in listOf(0, pixels.width / 2 - 1, pixels.width - 1)) {
                assertTrue("ink at x=$x on the bar's top line", pixels[x, lineY].luminance() < 0.1f)
            }
        }
    }

    @Test
    fun drawnMangoLight() {
        page(ThemeId.MANGO, DarkModePref.LIGHT)
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/bar_docked_mango_light.png")
    }

    @Test
    fun drawnUbeDark() {
        page(ThemeId.UBE, DarkModePref.DARK)
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/bar_docked_ube_dark.png")
    }
}
