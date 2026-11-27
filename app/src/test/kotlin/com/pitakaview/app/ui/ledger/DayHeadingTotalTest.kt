package com.pitakaview.app.ui.ledger

import com.pitakaview.app.data.db.dao.RecentTransaction
import com.pitakaview.app.domain.TxPurpose
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * A day's heading shows the sum of its entries only when they can be added. Found in the review before
 * 1.0 (2026-11-27): a goal's page with 500.00 put in and 200.00 taken out on one day was headed 700.00,
 * and the loan, baon and search pages did the same.
 */
class DayHeadingTotalTest {
    private val monday = LocalDate.of(2026, 11, 23)
    private val tuesday = monday.plusDays(1)

    private fun row(id: Long, purpose: TxPurpose, centavos: Long, on: LocalDate) =
        RecentTransaction(id, purpose, centavos, on, note = null, categoryName = null, fromWalletName = "Cash", toWalletName = null)

    @Test
    fun aDayOfOneKindIsAddedUp() {
        val days = MonthLedgerBuilder.groupByDay(
            listOf(row(3, TxPurpose.EXPENSE, 25_000, tuesday), row(2, TxPurpose.EXPENSE, 18_000, tuesday), row(1, TxPurpose.EXPENSE, 5_000, monday)),
        )
        assertEquals(listOf(tuesday, monday), days.map { it.date })
        assertEquals(listOf<Long?>(43_000, 5_000), days.map { it.totalCentavos })
    }

    @Test
    fun moneyPutInAndTakenOutOnOneDayIsNotAddedIntoOneFigure() {
        val days = MonthLedgerBuilder.groupByDay(
            listOf(row(2, TxPurpose.SAVINGS_WITHDRAWAL, 20_000, tuesday), row(1, TxPurpose.SAVINGS_DEPOSIT, 50_000, tuesday)),
        )
        assertEquals("no figure, and certainly not 700.00", listOf<Long?>(null), days.map { it.totalCentavos })
        assertEquals("the entries themselves are all there", 2, days.single().rows.size)
    }

    @Test
    fun eachDayIsJudgedByItself() {
        val days = MonthLedgerBuilder.groupByDay(
            listOf(
                row(4, TxPurpose.LOAN_PAYMENT, 100_000, tuesday), row(3, TxPurpose.LOAN_PAYMENT, 50_000, tuesday),
                row(2, TxPurpose.LOAN_PAYMENT, 30_000, monday), row(1, TxPurpose.LOAN_START, 500_000, monday),
            ),
        )
        assertEquals(listOf<Long?>(150_000, null), days.map { it.totalCentavos })
    }
}
