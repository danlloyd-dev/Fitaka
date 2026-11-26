package com.pitakaview.app.ui.summary

import com.pitakaview.app.data.db.dao.CategoryTotal
import com.pitakaview.app.data.db.dao.MonthPurposeTotal
import com.pitakaview.app.domain.TxPurpose
import com.pitakaview.app.ui.ledger.CategoryShare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class MonthSummaryBuilderTest {
    private val november = YearMonth.of(2026, 11)

    private fun total(month: String, purpose: TxPurpose, centavos: Long) = MonthPurposeTotal(month, purpose, centavos)

    private val totals = listOf(
        total("2026-09", TxPurpose.INCOME, 3_000_000),
        total("2026-09", TxPurpose.EXPENSE, 3_400_000),
        total("2026-10", TxPurpose.INCOME, 3_000_000),
        total("2026-10", TxPurpose.EXPENSE, 2_460_000),
        total("2026-11", TxPurpose.INCOME, 3_500_000),
        total("2026-11", TxPurpose.BAON_RECEIVED, 50_000),
        total("2026-11", TxPurpose.EXPENSE, 2_600_000),
        total("2026-11", TxPurpose.BAON_GIVEN, 38_500),
        total("2026-11", TxPurpose.SAVINGS_DEPOSIT, 700_000),
        total("2026-11", TxPurpose.SAVINGS_WITHDRAWAL, 100_000),
        // Neither in nor out: these must not change what is left over.
        total("2026-11", TxPurpose.TRANSFER, 900_000),
        total("2026-11", TxPurpose.LOAN_START, 1_000_000),
        total("2026-11", TxPurpose.LOAN_PAYMENT, 200_000),
    )

    @Test
    fun aMonthIsWhatCameInMinusWhatWentOut() {
        val s = MonthSummaryBuilder.build(november, isThisMonth = true, totals, emptyList())
        assertTrue(s.loaded)
        assertEquals(3_500_000L, s.incomeCentavos)
        assertEquals(50_000L, s.baonReceivedCentavos)
        assertEquals(2_600_000L, s.spendingCentavos)
        assertEquals(38_500L, s.baonGivenCentavos)
        assertEquals("baon counts: it is money in and money out", 3_550_000L to 2_638_500L, s.inCentavos to s.outCentavos)
        assertEquals("moves, savings and loans do not make you richer or poorer", 911_500L, s.leftCentavos)
        assertEquals("put in minus taken out", 600_000L, s.setAsideCentavos)
    }

    @Test
    fun theChartHasSixMonthsOldestFirstWithEmptyMonthsAtZero() {
        val s = MonthSummaryBuilder.build(november, true, totals, emptyList())
        assertEquals(
            listOf("2026-06", "2026-07", "2026-08", "2026-09", "2026-10", "2026-11"),
            s.bars.map { it.month.toString() },
        )
        assertEquals(MonthBar(YearMonth.of(2026, 6), 0, 0), s.bars.first())
        assertEquals(MonthBar(YearMonth.of(2026, 10), 3_000_000, 2_460_000), s.bars[4])
        assertEquals(MonthBar(november, 3_550_000, 2_638_500), s.bars.last())
        assertTrue(s.hasChart)

        val october = MonthSummaryBuilder.build(YearMonth.of(2026, 10), false, totals, emptyList())
        assertEquals("another month ends its own six", YearMonth.of(2026, 10), october.bars.last().month)
        assertEquals(540_000L, october.leftCentavos)

        val nothing = MonthSummaryBuilder.build(november, true, emptyList(), emptyList())
        assertFalse("no bars worth drawing", nothing.hasChart)
        assertEquals(0L, nothing.leftCentavos)
        assertFalse("before the database answers, nothing is claimed", MonthSummary(november, true).loaded)
    }

    @Test
    fun theBiggestFiveCategoriesAreNamedAndTheRestAreAddedUp() {
        val categories = listOf(
            CategoryTotal(3, "Bills", "bills", 1_200_000), CategoryTotal(5, "Groceries", "cart", 650_000),
            CategoryTotal(1, "Food", "food", 437_500), CategoryTotal(2, "Transport", "transport", 181_300),
            CategoryTotal(9, "Fun", "fun", 99_650), CategoryTotal(7, "Health", "health", 45_000), CategoryTotal(10, "Other", "other", 25_000),
        )
        val top = MonthSummaryBuilder.build(november, true, totals, categories).topCategories
        assertEquals(listOf("Bills", "Groceries", "Food", "Transport", "Fun", "Everything else"), top.map { it.name })
        assertEquals(CategoryShare("Everything else", 70_000), top.last())
        assertEquals("nothing is dropped", categories.sumOf { it.totalCentavos }, top.sumOf { it.totalCentavos })

        val few = MonthSummaryBuilder.build(november, true, totals, categories.take(3)).topCategories
        assertEquals("no 'everything else' when there is nothing else", listOf("Bills", "Groceries", "Food"), few.map { it.name })
    }

    @Test
    fun moneyOutIsComparedWithTheMonthBefore() {
        fun comparison(out: Long, before: Long): String? = MonthSummaryBuilder.comparison(
            MonthSummaryBuilder.build(
                november, true,
                listOf(total("2026-10", TxPurpose.EXPENSE, before), total("2026-11", TxPurpose.EXPENSE, out)).filter { it.totalCentavos > 0 },
                emptyList(),
            ),
        )
        assertEquals("₱1,785.00 more went out than in October", comparison(2_638_500, 2_460_000))
        assertEquals("₱460.00 less went out than in October", comparison(2_414_000, 2_460_000))
        assertEquals("The same went out as in October", comparison(2_460_000, 2_460_000))
        assertNull("nothing last month to compare with", comparison(2_460_000, 0))
    }

    @Test
    fun chartAmountsAreShortenedForTheAxis() {
        assertEquals("₱0", MonthSummaryBuilder.shortPeso(0.0))
        assertEquals("₱850", MonthSummaryBuilder.shortPeso(850.0))
        assertEquals("₱1.5k", MonthSummaryBuilder.shortPeso(1_500.0))
        assertEquals("₱20k", MonthSummaryBuilder.shortPeso(20_000.0))
        assertEquals("₱36k", MonthSummaryBuilder.shortPeso(36_000.0))
        assertEquals("₱1.2M", MonthSummaryBuilder.shortPeso(1_200_000.0))
        assertEquals("−₱500", MonthSummaryBuilder.shortPeso(-500.0))
    }

    /**
     * Found on the owner's phone (2026-11-26): with ₱3.83 logged the chart's lines sat on ₱1.28, ₱2.55 and
     * ₱3.83 and were labelled ₱1, ₱3, ₱4. The app now chooses the lines, on round amounts.
     */
    @Test
    fun theChartsAmountLinesSitOnRoundAmountsAndAreLabelledExactly() {
        fun labels(maxPesos: Double) = MonthSummaryBuilder.axisLines(maxPesos).map { MonthSummaryBuilder.shortPeso(it) }
        assertEquals("the phone's case", listOf("₱0", "₱1", "₱2", "₱3", "₱4"), labels(3.83))
        assertEquals(listOf("₱0", "₱10k", "₱20k", "₱30k", "₱40k"), labels(35_500.0))
        assertEquals(listOf("₱0", "₱500", "₱1k"), labels(850.0))
        assertEquals(listOf("₱0", "₱2k", "₱4k", "₱6k", "₱8k"), labels(7_200.0))
        assertEquals("a bar that ends on a line does not get an empty step above it", listOf("₱0", "₱10k", "₱20k", "₱30k", "₱40k"), labels(40_000.0))
        assertEquals("below a peso the labels keep their centavos", listOf("₱0", "₱0.20", "₱0.40", "₱0.60"), labels(0.50))
        assertEquals(listOf("₱0", "₱1"), labels(0.0))

        // Every label stands exactly on its line: no two lines share a label, and the top covers the tallest bar.
        for (max in listOf(0.37, 3.83, 12.0, 99.99, 850.0, 4_999.0, 35_500.0, 1_250_000.0)) {
            val lines = MonthSummaryBuilder.axisLines(max)
            assertEquals("labels for $max are all different", lines.size, lines.map { MonthSummaryBuilder.shortPeso(it) }.distinct().size)
            assertTrue("the top line covers $max", lines.last() >= max)
            assertTrue("about four lines for $max, got ${lines.size - 1}", lines.size - 1 in 2..5)
        }
    }

    @Test
    fun smallAxisAmountsAreNeverRoundedToAPesoTheyDoNotStandFor() {
        assertEquals("₱1.28", MonthSummaryBuilder.shortPeso(1.2767))
        assertEquals("₱2.55", MonthSummaryBuilder.shortPeso(2.5533))
        assertEquals("₱283.33", MonthSummaryBuilder.shortPeso(283.333))
        assertEquals("₱3", MonthSummaryBuilder.shortPeso(3.0))
    }

    @Test
    fun theChartIsAlsoSaidInWords() {
        val words = describe(listOf(MonthBar(YearMonth.of(2026, 10), 3_000_000, 2_460_000), MonthBar(november, 3_550_000, 2_638_500)))
        assertEquals("October: ₱30,000.00 in, ₱24,600.00 out. November: ₱35,500.00 in, ₱26,385.00 out", words)
    }
}
