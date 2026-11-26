package com.pitakaview.app.ui.summary

import com.github.takahirom.roborazzi.captureRoboImage
import com.pitakaview.app.data.db.dao.CategoryTotal
import com.pitakaview.app.data.db.dao.MonthPurposeTotal
import com.pitakaview.app.domain.DarkModePref
import com.pitakaview.app.domain.ThemeId
import com.pitakaview.app.domain.TxPurpose
import com.pitakaview.app.ui.theme.PitakaTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

/** The summary screen with its chart, drawn for real, so the chart's look is judged from a picture. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h1500dp-hdpi")
class SummaryScreenshotTest {
    private val november = YearMonth.of(2026, 11)

    private fun month(month: String, income: Long, spending: Long, saved: Long = 0, baonIn: Long = 0) = listOfNotNull(
        MonthPurposeTotal(month, TxPurpose.INCOME, income),
        MonthPurposeTotal(month, TxPurpose.EXPENSE, spending),
        if (saved > 0) MonthPurposeTotal(month, TxPurpose.SAVINGS_DEPOSIT, saved) else null,
        if (baonIn > 0) MonthPurposeTotal(month, TxPurpose.BAON_RECEIVED, baonIn) else null,
    )

    private val totals = month("2026-06", 3_000_000, 2_150_000) + month("2026-07", 3_000_000, 2_780_000) +
        month("2026-08", 3_250_000, 1_990_000) + month("2026-09", 3_000_000, 3_400_000) +
        month("2026-10", 3_000_000, 2_460_000) + month("2026-11", 3_500_000, 2_638_500, saved = 600_000, baonIn = 50_000)

    private val categories = listOf(
        CategoryTotal(3, "Bills", "bills", 1_200_000), CategoryTotal(5, "Groceries", "cart", 650_050),
        CategoryTotal(1, "Food", "food", 437_500), CategoryTotal(2, "Transport", "transport", 181_300),
        CategoryTotal(9, "Fun", "fun", 99_650), CategoryTotal(7, "Health", "health", 45_000), CategoryTotal(10, "Other", "other", 25_000),
    )

    private fun shot(name: String, theme: ThemeId, mode: DarkModePref, summary: MonthSummary) =
        captureRoboImage("build/outputs/roborazzi/screen_$name.png") {
            PitakaTheme(theme, mode) { SummaryContent(summary, {}, {}, {}, {}) }
        }

    @Test
    fun summaryMangoLight() = shot("summary_mango_light", ThemeId.MANGO, DarkModePref.LIGHT, MonthSummaryBuilder.build(november, true, totals, categories))

    @Test
    fun summaryUbeDark() = shot("summary_ube_dark", ThemeId.UBE, DarkModePref.DARK, MonthSummaryBuilder.build(november, true, totals, categories))

    /**
     * The case the owner's phone showed on 2026-11-26: with only a few pesos logged, the chart's amount
     * lines fell on fractions and their labels were rounded, reading ₱0, ₱1, ₱3, ₱4 for evenly spaced lines.
     */
    @Test
    fun summaryWithSmallAmounts() = shot(
        "summary_small_amounts", ThemeId.MANGO, DarkModePref.DARK,
        MonthSummaryBuilder.build(
            november, true, listOf(MonthPurposeTotal("2026-11", TxPurpose.EXPENSE, 383)),
            listOf(CategoryTotal(2, "Transport", "transport", 246), CategoryTotal(1, "Food", "food", 137)),
        ),
    )

    @Test
    fun summaryWithNothingYet() = shot("summary_empty", ThemeId.MANGO, DarkModePref.LIGHT, MonthSummaryBuilder.build(november, true, emptyList(), emptyList()))
}
