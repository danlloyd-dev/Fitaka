package com.pitakaview.app.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.pitakaview.app.domain.AppearanceSettings
import com.pitakaview.app.domain.DarkModePref
import com.pitakaview.app.domain.ThemeId
import com.pitakaview.app.domain.UiStyle
import com.pitakaview.app.ui.theme.FitakaDimens
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
 * The four styles in Settings, read from the pixels of a primary button and a plain button on a blue page:
 *
 * - Plain: flat colour, a solid shadow.
 * - Glossy: the paler band across the primary button.
 * - Glassy: the plain button lets the page show through; the primary button keeps its colour.
 * - Sketch: flat colour, an outline that is not the straight one, and a shadow of strokes with gaps.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h300dp-xhdpi")
class StyleTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val page = Color(0xFF2D9CDB)
    private val colors = fitakaColors(ThemeId.MANGO, dark = false)

    private fun close(a: Color, b: Color) = abs(a.red - b.red) < 0.06f && abs(a.green - b.green) < 0.06f && abs(a.blue - b.blue) < 0.06f

    private fun dark(c: Color) = c.luminance() < 0.2f

    @Test
    fun glossyIsTheDefault() = assertEquals(UiStyle.GLOSSY, AppearanceSettings().style)

    @Test
    fun eachStyleFinishesThePartsItsOwnWay() {
        var style by mutableStateOf(UiStyle.PLAIN)
        rule.setContent {
            FitakaTheme(ThemeId.MANGO, DarkModePref.LIGHT, style = style) {
                Column(Modifier.fillMaxSize().background(page).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    FitakaButton("Next", onClick = {}, modifier = Modifier.fillMaxWidth().testTag("primary"), style = FitakaButtonStyle.Primary)
                    FitakaButton("View", onClick = {}, modifier = Modifier.fillMaxWidth().testTag("plain"))
                }
            }
        }
        val pictures = mutableMapOf<UiStyle, PixelMap>()
        lateinit var primary: Rect
        for (chosen in UiStyle.entries) {
            style = chosen
            rule.waitForIdle()
            val pixels = rule.onRoot().captureToImage().toPixelMap()
            pictures[chosen] = pixels
            primary = rule.onNodeWithTag("primary").fetchSemanticsNode().boundsInRoot
            val plain = rule.onNodeWithTag("plain").fetchSemanticsNode().boundsInRoot
            with(rule.density) {
                // 30 in from the left is clear of the word in the middle. 10 down is where the gloss's band
                // would be; 44 down is under it.
                fun at(box: Rect, down: Int) = pixels[(box.left + 30.dp.toPx()).roundToInt(), (box.top + down.dp.toPx()).roundToInt()]
                // The outline: somewhere in the first 5 across the left edge, half way down, there is ink.
                fun outlined(box: Rect) = (0..5.dp.toPx().roundToInt()).any { dark(pixels[box.left.roundToInt() + it, (box.top + 28.dp.toPx()).roundToInt()]) }
                assertTrue("${chosen.label}: the primary button's outline is missing", outlined(primary))
                assertTrue("${chosen.label}: the plain button's outline is missing", outlined(plain))

                // The primary button keeps its colour in every style: glass would wash it out.
                val body = at(primary, 44)
                val band = at(primary, 10)
                assertTrue("${chosen.label}: the primary button should be the primary colour, is $body", close(body, colors.primary))
                if (chosen == UiStyle.GLOSSY) {
                    assertTrue("Glossy: the band should be paler than the fill", band.luminance() > body.luminance() + 0.08f)
                } else {
                    assertTrue("${chosen.label}: no band, but $band is not $body", close(band, body))
                }

                // The plain button is the card colour, except in Glassy, where the blue page shows through it.
                val pane = at(plain, 44)
                if (chosen == UiStyle.GLASSY) {
                    assertTrue("Glassy: the page should show through the plain button, but it is $pane", pane.red < colors.card.red - 0.15f)
                } else {
                    assertTrue("${chosen.label}: the plain button should be the card colour, is $pane", close(pane, colors.card))
                }

                // The shadow under the primary button, along the middle of its strip: solid everywhere but
                // in Sketch, where it is strokes with the page between them. A tappable part's bounds end
                // at its own edge, so the strip is the few rows just below them.
                val y = (primary.bottom + (FitakaDimens.shadow / 2).toPx()).roundToInt()
                val along = ((primary.left + 30.dp.toPx()).roundToInt()..(primary.right - 30.dp.toPx()).roundToInt())
                val inked = along.count { dark(pixels[it, y]) }.toFloat() / along.count()
                if (chosen == UiStyle.SKETCH) {
                    assertTrue("Sketch: the shadow should be strokes with gaps, but $inked of it is ink", inked in 0.15f..0.85f)
                } else {
                    assertTrue("${chosen.label}: the shadow should be solid, but only $inked of it is ink", inked > 0.95f)
                }
            }
        }

        // Sketch's outline is not Plain's: down the button's straight left edge, the two pictures differ.
        val a = pictures.getValue(UiStyle.PLAIN)
        val b = pictures.getValue(UiStyle.SKETCH)
        with(rule.density) {
            val rows = (primary.top + 16.dp.toPx()).roundToInt()..(primary.top + 40.dp.toPx()).roundToInt()
            val columns = primary.left.roundToInt()..(primary.left + 6.dp.toPx()).roundToInt()
            val differing = rows.sumOf { y -> columns.count { x -> !close(a[x, y], b[x, y]) } }
            assertTrue("Sketch should draw its own outline, but only $differing pixels of the left edge differ from Plain", differing > 20)
        }
    }
}
