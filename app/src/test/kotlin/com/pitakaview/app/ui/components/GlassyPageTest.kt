package com.pitakaview.app.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.pitakaview.app.domain.DarkModePref
import com.pitakaview.app.domain.ThemeId
import com.pitakaview.app.domain.UiStyle
import com.pitakaview.app.ui.theme.FitakaTheme
import com.pitakaview.app.ui.theme.fitakaColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The Glassy style's page: soft patches of colour behind the cards, and no other style has them. The
 * colour must run on under the top bar: the first version stopped dead at the bar's lower edge, because
 * the bar laid its own tint over the patch behind it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h800dp-xhdpi")
class GlassyPageTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun step(a: Color, b: Color) = maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue))

    @Test
    fun theColourRunsOnUnderTheTopBarAndOnlyGlassyHasIt() {
        var style by mutableStateOf(UiStyle.GLASSY)
        rule.setContent {
            FitakaTheme(ThemeId.MANGO, DarkModePref.LIGHT, style = style) {
                FitakaScreen(title = "Home") { item { Text("One line") } }
            }
        }
        val page = fitakaColors(ThemeId.MANGO, dark = false).background
        for (chosen in UiStyle.entries) {
            style = chosen
            rule.waitForIdle()
            val pixels = rule.onRoot().captureToImage().toPixelMap()
            with(rule.density) {
                // Down the left edge, from the top of the screen to well below the bar (the bar is about 64
                // high), where the first patch is strongest. Nothing is drawn there but page and bar.
                val x = 3.dp.toPx().roundToInt()
                val rows = 0..160.dp.toPx().roundToInt()
                val column = rows.map { pixels[x, it] }
                val sharpest = column.zipWithNext { a, b -> step(a, b) }.max()
                val tinted = column.maxOf { step(it, page) }
                if (chosen == UiStyle.GLASSY) {
                    assertTrue("Glassy: the page should carry colour by the top left corner, but differs from the plain page by only $tinted", tinted > 0.08f)
                    assertTrue("Glassy: the colour steps by $sharpest between two rows; it should fade evenly from the bar into the page", sharpest < 0.02f)
                } else {
                    assertEquals("${chosen.label}: the page is its plain colour", 0f, tinted, 0.01f)
                }
            }
        }
    }
}
