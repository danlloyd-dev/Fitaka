package com.pitakaview.app.ui.summary

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.pitakaview.app.domain.DarkModePref
import com.pitakaview.app.domain.ThemeId
import com.pitakaview.app.ui.theme.PitakaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

/**
 * Tapping a month on the chart. The chart is drawn for real: it only knows where its bars are once it
 * has been drawn, and it reports the tapped month while drawing, so the test draws it the way a phone
 * does between touches.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h850dp-xhdpi")
class ChartTapTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val bars = listOf(
        MonthBar(YearMonth.of(2026, 6), 3_000_000, 2_150_000), MonthBar(YearMonth.of(2026, 7), 3_000_000, 2_780_000),
        MonthBar(YearMonth.of(2026, 8), 3_250_000, 1_990_000), MonthBar(YearMonth.of(2026, 9), 3_000_000, 3_400_000),
        MonthBar(YearMonth.of(2026, 10), 3_000_000, 2_460_050), MonthBar(YearMonth.of(2026, 11), 3_500_000, 2_638_500),
    )

    private fun shows(text: String) = rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    private fun draw() {
        rule.onRoot().captureToImage()
        rule.waitForIdle()
    }

    /** Taps the chart at [across] of its width (0 is its left edge, 1 its right), halfway up. */
    private fun tapAt(across: Float) {
        rule.onNodeWithContentDescription(describe(bars)).performTouchInput { click(Offset(width * across, height / 2f)) }
        rule.waitForIdle()
        draw()
    }

    @Test
    fun tappingAMonthWritesItsAmountsUnderTheChartAndTappingAgainClearsThem() {
        rule.setContent {
            PitakaTheme(ThemeId.MANGO, DarkModePref.LIGHT) {
                Box(Modifier.fillMaxWidth().background(PitakaTheme.colors.card).padding(16.dp)) { MonthBarsChart(bars) }
            }
        }
        assertTrue(shows(TAP_HINT))
        draw()

        // The far right of the chart is the newest month.
        tapAt(0.95f)
        rule.waitUntil(10_000) { shows("November 2026: ₱35,000.00 in, ₱26,385.00 out") }
        draw()
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/screen_chart_month_tapped.png")

        // Another month replaces it.
        tapAt(0.22f)
        rule.waitUntil(10_000) { shows("June 2026: ₱30,000.00 in, ₱21,500.00 out") }

        // The same place again puts the hint back.
        tapAt(0.22f)
        rule.waitUntil(10_000) { shows(TAP_HINT) }
    }
}
