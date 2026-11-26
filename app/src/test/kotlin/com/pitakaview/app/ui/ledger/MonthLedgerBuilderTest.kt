package com.pitakaview.app.ui.ledger

import com.pitakaview.app.data.db.dao.PayReceipt
import com.pitakaview.app.data.db.dao.RecentTransaction
import com.pitakaview.app.data.db.entity.PayScheduleEntity
import com.pitakaview.app.domain.PayFrequency
import com.pitakaview.app.domain.TxPurpose
import com.pitakaview.app.domain.WaitingPayday
import com.pitakaview.app.ui.kita.PaydayRow
import com.pitakaview.app.ui.kita.PaydayRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/** What the Gastos and Kita tabs show is added up from one list of rows; these pin the adding up. */
class MonthLedgerBuilderTest {
    private val november = YearMonth.of(2026, 11)
    private fun nov(day: Int) = LocalDate.of(2026, 11, day)
    private fun spent(id: Long, amount: Long, on: LocalDate, category: String?, note: String? = null, wallet: String = "Cash") =
        RecentTransaction(id, TxPurpose.EXPENSE, amount, on, note, category, wallet, null)

    // Newest first, as the database returns them.
    private val rows = listOf(
        spent(5, 12_500, nov(25), "Food"),
        spent(4, 1_300, nov(25), "Transport"),
        spent(3, 180_000, nov(24), "Bills"),
        spent(2, 25_000, nov(22), "Food"),
        spent(1, 6_200, nov(22), null),
    )

    @Test
    fun totalsCategoriesAndDaysAllComeFromTheSameRows() {
        val ledger = MonthLedgerBuilder.build(november, isThisMonth = true, rows)

        assertTrue(ledger.loaded)
        assertEquals(225_000L, ledger.totalCentavos)
        assertEquals(5, ledger.entryCount)
        assertEquals(
            "biggest first; rows without a category are still counted",
            listOf(CategoryShare("Bills", 180_000), CategoryShare("Food", 37_500), CategoryShare("No category", 6_200), CategoryShare("Transport", 1_300)),
            ledger.categories,
        )
        assertEquals("the categories add up to the total", ledger.totalCentavos, ledger.categories.sumOf { it.totalCentavos })
        assertEquals(listOf(nov(25), nov(24), nov(22)), ledger.days.map { it.date })
        assertEquals(listOf(13_800L, 180_000L, 31_200L), ledger.days.map { it.totalCentavos })
        assertEquals("rows keep their order inside a day", listOf(5L, 4L), ledger.days.first().rows.map { it.id })
    }

    @Test
    fun anEmptyMonthIsLoadedButEmpty() {
        val ledger = MonthLedgerBuilder.build(YearMonth.of(2026, 10), isThisMonth = false, emptyList())
        assertTrue(ledger.loaded)
        assertFalse(ledger.isThisMonth)
        assertEquals(0L, ledger.totalCentavos)
        assertTrue(ledger.categories.isEmpty() && ledger.days.isEmpty())
        assertFalse("before the database answers, nothing is claimed", MonthLedger(november, true).loaded)
    }

    @Test
    fun percentIsRoundedAndNeverShowsZeroForSomething() {
        assertEquals("71%", MonthLedgerBuilder.percentText(25_000, 35_000))
        assertEquals("29%", MonthLedgerBuilder.percentText(10_000, 35_000))
        assertEquals("100%", MonthLedgerBuilder.percentText(500, 500))
        assertEquals("<1%", MonthLedgerBuilder.percentText(1, 100_000))
        assertEquals("", MonthLedgerBuilder.percentText(0, 100_000))
        assertEquals("", MonthLedgerBuilder.percentText(100, 0))
    }

    @Test
    fun textHelpersReadNaturally() {
        assertEquals("November 2026", monthTitle(november))
        assertEquals("1 entry", entriesText(1))
        assertEquals("0 entries", entriesText(0))
        assertEquals("Cash · lugaw", spent(1, 1, nov(1), "Food", note = "lugaw").ledgerSubtitle())
        assertEquals("Cash", spent(1, 1, nov(1), "Food").ledgerSubtitle())
        assertEquals("MariBank → GCash", RecentTransaction(1, TxPurpose.TRANSFER, 1, nov(1), null, null, "MariBank", "GCash").walletsText())
    }

    // ---- the paydays list on the Kita tab ----

    private fun schedule(id: Long, name: String, frequency: PayFrequency, firstDay: Int = 15) = PayScheduleEntity(
        id = id, name = name, expectedAmountCentavos = 1_500_000, walletId = 1, categoryId = 1,
        frequency = frequency, firstDay = firstDay, createdAt = Instant.EPOCH,
    )

    @Test
    fun eachScheduleShowsThePaydayItIsWaitingForSoonestFirst() {
        val today = nov(25) // five days before Nov 30
        val rows = PaydayRows.build(
            schedules = listOf(schedule(1, "Monthly on the 20th", PayFrequency.MONTHLY, firstDay = 20), schedule(2, "Sahod", PayFrequency.SEMI_MONTHLY)),
            receipts = listOf(PayReceipt(1, nov(20))),
            today = today,
            zone = ZoneOffset.UTC,
        )
        assertEquals(
            listOf(
                PaydayRow(2, "Sahod", 1_500_000, WaitingPayday(nov(30), canReceive = true)),
                PaydayRow(1, "Monthly on the 20th", 1_500_000, WaitingPayday(LocalDate.of(2026, 12, 20), canReceive = false)),
            ),
            rows,
        )
    }

    @Test
    fun aPaydaysStatusSaysWhatCanBeDoneAboutIt() {
        val today = nov(25)
        assertEquals("Due today", PaydayRows.status(WaitingPayday(today, true), today))
        assertEquals("Was due yesterday", PaydayRows.status(WaitingPayday(nov(24), true), today))
        assertEquals("Was due Nov 15", PaydayRows.status(WaitingPayday(nov(15), true), today))
        assertEquals("Due tomorrow", PaydayRows.status(WaitingPayday(nov(26), true), today))
        assertEquals("Due Nov 30", PaydayRows.status(WaitingPayday(nov(30), true), today))
        assertEquals("Next: Dec 15", PaydayRows.status(WaitingPayday(LocalDate.of(2026, 12, 15), false), today))
        assertEquals("No payday coming", PaydayRows.status(null, today))
    }
}
