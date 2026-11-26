package com.pitakaview.app.domain

import com.pitakaview.app.data.db.dao.BaonActivityRow
import com.pitakaview.app.data.db.entity.BaonEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class BaonMathTest {
    private fun d(text: String) = LocalDate.parse(text)

    @Test
    fun weeklyPeriodRestartsOnTheStartWeekday() {
        // 2026-11-02 is a Monday; Wednesday 2026-11-25 is in the week Mon 11-23 .. Sun 11-29.
        assertEquals(
            d("2026-11-23")..d("2026-11-29"),
            BaonMath.currentPeriod(BaonPeriod.WEEKLY, startDate = d("2026-11-02"), today = d("2026-11-25")),
        )
    }

    @Test
    fun monthlyPeriodClampsToShortMonths() {
        val start = d("2026-01-31")
        assertEquals(d("2026-01-31")..d("2026-02-27"), BaonMath.currentPeriod(BaonPeriod.MONTHLY, start, d("2026-02-10")))
        assertEquals(d("2026-02-28")..d("2026-03-30"), BaonMath.currentPeriod(BaonPeriod.MONTHLY, start, d("2026-03-05")))
    }

    @Test
    fun beforeTheStartDateTheFirstPeriodIsShown() {
        assertEquals(
            d("2026-11-02")..d("2026-11-08"),
            BaonMath.currentPeriod(BaonPeriod.WEEKLY, d("2026-11-02"), d("2026-10-30")),
        )
    }

    @Test
    fun receivedBaonLeftIsReceivedMinusSpentThisPeriod() {
        val baon = BaonEntity(
            id = 1, name = "My baon", direction = BaonDirection.RECEIVE, amountCentavos = 50_000,
            period = BaonPeriod.WEEKLY, startDate = d("2026-11-02"), createdAt = Instant.EPOCH,
        )
        val rows = listOf(
            BaonActivityRow(1, TxPurpose.BAON_RECEIVED, d("2026-11-16"), 50_000), // last week: ignored
            BaonActivityRow(1, TxPurpose.EXPENSE, d("2026-11-20"), 3_000), // last week: ignored
            BaonActivityRow(1, TxPurpose.BAON_RECEIVED, d("2026-11-23"), 50_000),
            BaonActivityRow(1, TxPurpose.EXPENSE, d("2026-11-24"), 12_000),
            BaonActivityRow(1, TxPurpose.EXPENSE, d("2026-11-25"), 8_000),
            BaonActivityRow(2, TxPurpose.EXPENSE, d("2026-11-25"), 99_999), // another baon: ignored
        )
        val summary = BaonMath.summarize(baon, rows, today = d("2026-11-25"))
        assertEquals(50_000L, summary.budgetCentavos)
        assertEquals(20_000L, summary.usedCentavos)
        assertEquals(30_000L, summary.leftCentavos)
    }

    @Test
    fun givenBaonLeftIsPlanMinusGivenThisPeriod() {
        val baon = BaonEntity(
            id = 3, name = "Junior", direction = BaonDirection.GIVE, amountCentavos = 200_000,
            period = BaonPeriod.MONTHLY, startDate = d("2026-11-01"), createdAt = Instant.EPOCH,
        )
        val rows = listOf(
            BaonActivityRow(3, TxPurpose.BAON_GIVEN, d("2026-10-31"), 50_000), // last month: ignored
            BaonActivityRow(3, TxPurpose.BAON_GIVEN, d("2026-11-07"), 50_000),
            BaonActivityRow(3, TxPurpose.BAON_GIVEN, d("2026-11-14"), 50_000),
        )
        val summary = BaonMath.summarize(baon, rows, today = d("2026-11-25"))
        assertEquals(200_000L, summary.budgetCentavos)
        assertEquals(100_000L, summary.usedCentavos)
        assertEquals(100_000L, summary.leftCentavos)
    }
}
