package com.pitakaview.app.ui.home

import com.pitakaview.app.data.db.dao.BaonActivityRow
import com.pitakaview.app.data.db.dao.GoalProgress
import com.pitakaview.app.data.db.dao.LoanWithPaid
import com.pitakaview.app.data.db.dao.PayReceipt
import com.pitakaview.app.data.db.dao.PurposeTotal
import com.pitakaview.app.data.db.dao.RecentTransaction
import com.pitakaview.app.data.db.dao.WalletWithBalance
import com.pitakaview.app.data.db.entity.BaonEntity
import com.pitakaview.app.data.db.entity.LoanEntity
import com.pitakaview.app.data.db.entity.PayScheduleEntity
import com.pitakaview.app.data.db.entity.WalletEntity
import com.pitakaview.app.domain.BaonDirection
import com.pitakaview.app.domain.BaonPeriod
import com.pitakaview.app.domain.LoanDirection
import com.pitakaview.app.domain.PayFrequency
import com.pitakaview.app.domain.TxPurpose
import com.pitakaview.app.domain.WalletType
import com.pitakaview.app.ui.theme.Section
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class HomeStateBuilderTest {
    private val t0 = Instant.EPOCH
    private val today = LocalDate.of(2026, 11, 25) // Wednesday

    private fun loan(id: Long, dir: LoanDirection, who: String, total: Long, due: LocalDate?, paid: Long) = LoanWithPaid(
        LoanEntity(id = id, direction = dir, counterparty = who, principalCentavos = total, totalPayableCentavos = total,
            startDate = LocalDate.of(2026, 10, 1), dueDate = due, createdAt = t0),
        paidCentavos = paid,
    )

    private fun build(
        loans: List<LoanWithPaid> = emptyList(),
        baons: List<BaonEntity> = emptyList(),
        activity: List<BaonActivityRow> = emptyList(),
        schedules: List<PayScheduleEntity> = emptyList(),
    ) = HomeStateBuilder.build(
        today = today,
        wallets = listOf(
            WalletWithBalance(WalletEntity(id = 1, name = "Cash", type = WalletType.CASH, createdAt = t0), 85_000),
            WalletWithBalance(WalletEntity(id = 2, name = "GCash", type = WalletType.E_WALLET, createdAt = t0), 275_000),
        ),
        monthTotals = listOf(PurposeTotal(TxPurpose.INCOME, 2_000_000), PurposeTotal(TxPurpose.EXPENSE, 40_000)),
        goals = listOf(GoalProgress(1, "Emergency", 5_000_000, null, null, 500_000), GoalProgress(2, "Laptop", 4_000_000, null, null, 100_000)),
        openLoans = loans,
        baons = baons,
        baonActivity = activity,
        paySchedules = schedules,
        recent = emptyList(),
    )

    @Test
    fun totalsComeStraightFromTheirSections() {
        val s = build()
        assertEquals(360_000L, s.totalCentavos)
        assertEquals(2_000_000L, s.incomeCentavos)
        assertEquals(40_000L, s.spendingCentavos)
        assertEquals(600_000L, s.savedCentavos)
        assertNull("no baon set up", s.baonLeftCentavos)
    }

    @Test
    fun upcomingListsPaydaysAndLoansInTheNextTwoWeeksSoonestFirst() {
        val salary = PayScheduleEntity(id = 1, name = "Sahod", expectedAmountCentavos = 1_500_000, walletId = 1, categoryId = 1,
            frequency = PayFrequency.SEMI_MONTHLY, createdAt = t0)
        val s = build(
            schedules = listOf(salary),
            loans = listOf(
                loan(1, LoanDirection.I_OWE, "Tita Baby", 1_100_000, LocalDate.of(2026, 12, 1), 200_000),
                loan(2, LoanDirection.OWED_TO_ME, "Kuya Jun", 300_000, LocalDate.of(2026, 11, 20), 100_000), // overdue
                loan(3, LoanDirection.I_OWE, "Bank", 5_000_000, LocalDate.of(2026, 12, 31), 0), // beyond 2 weeks
                loan(4, LoanDirection.I_OWE, "Paid off", 100_000, LocalDate.of(2026, 11, 28), 100_000), // nothing left
            ),
        )
        // Katapusan Mon 2026-11-30 is in range; kinsenas 2026-12-15 is past the 2026-12-09 horizon.
        assertEquals(
            listOf(
                Triple(LocalDate.of(2026, 11, 20), "Kuya Jun", "Owes you · overdue"),
                Triple(LocalDate.of(2026, 11, 30), "Sahod", "Payday"),
                Triple(LocalDate.of(2026, 12, 1), "Tita Baby", "You pay"),
            ),
            s.upcoming.map { Triple(it.date, it.title, it.detail) },
        )
        assertEquals(listOf(Section.UTANG, Section.KITA, Section.UTANG), s.upcoming.map { it.section })
        assertEquals(900_000L + 5_000_000L, s.iOweCentavos)
        assertEquals(200_000L, s.owedToMeCentavos)
    }

    @Test
    fun backupReminderShowsOnlyWhenThereIsSomethingToLoseAndNoRecentBackup() {
        val entry = RecentTransaction(1, TxPurpose.EXPENSE, 100, today, null, "Food", "Cash", null)
        fun nudge(recent: List<RecentTransaction>, lastBackupOn: LocalDate?) = HomeStateBuilder.build(
            today = today, wallets = emptyList(), monthTotals = emptyList(), goals = emptyList(), openLoans = emptyList(),
            baons = emptyList(), baonActivity = emptyList(), paySchedules = emptyList(), recent = recent,
            lastBackupOn = lastBackupOn,
        ).backupNudge

        assertNull("an empty app is never nagged", nudge(emptyList(), null))
        assertEquals(BackupNudge.NEVER_BACKED_UP, nudge(listOf(entry), null))
        assertEquals(BackupNudge.BACKUP_IS_OLD, nudge(listOf(entry), today.minusDays(31)))
        assertNull("30 days is still fine", nudge(listOf(entry), today.minusDays(30)))
        assertNull(nudge(listOf(entry), today))
    }

    private fun paydaysComingUp(on: LocalDate, vararg receivedOn: LocalDate): List<Pair<LocalDate, String>> {
        val salary = PayScheduleEntity(id = 1, name = "Sahod", expectedAmountCentavos = 1_500_000, walletId = 1, categoryId = 1,
            frequency = PayFrequency.SEMI_MONTHLY, createdAt = t0)
        return HomeStateBuilder.build(
            today = on, wallets = emptyList(), monthTotals = emptyList(), goals = emptyList(), openLoans = emptyList(),
            baons = emptyList(), baonActivity = emptyList(), paySchedules = listOf(salary), recent = emptyList(),
            payReceipts = receivedOn.map { PayReceipt(payScheduleId = 1, occurredOn = it) },
        ).upcoming.map { it.date to it.detail }
    }

    @Test
    fun aPaydayMarkedReceivedDropsOutOfComingUp() {
        val nov30 = LocalDate.of(2026, 11, 30)
        assertEquals(listOf(nov30 to "Payday"), paydaysComingUp(today))
        // Received today, five days early: Nov 30 is done, and Dec 15 is beyond the two-week horizon.
        assertEquals(emptyList<Pair<LocalDate, String>>(), paydaysComingUp(today, today))
        // Pay received for the payday before does not count for this one.
        assertEquals(listOf(nov30 to "Payday"), paydaysComingUp(today, LocalDate.of(2026, 11, 15)))
        // Pay for another schedule does not count either.
        val other = HomeStateBuilder.build(
            today = today, wallets = emptyList(), monthTotals = emptyList(), goals = emptyList(), openLoans = emptyList(),
            baons = emptyList(), baonActivity = emptyList(), recent = emptyList(),
            paySchedules = listOf(PayScheduleEntity(id = 1, name = "Sahod", expectedAmountCentavos = 1, walletId = 1, categoryId = 1,
                frequency = PayFrequency.SEMI_MONTHLY, createdAt = t0)),
            payReceipts = listOf(PayReceipt(payScheduleId = 2, occurredOn = today)),
        )
        assertEquals(listOf(nov30), other.upcoming.map { it.date })
    }

    @Test
    fun aPaydayThatPassedWithoutBeingReceivedStaysListedAsDue() {
        val nov17 = LocalDate.of(2026, 11, 17)
        assertEquals(
            listOf(LocalDate.of(2026, 11, 15) to "Payday · due", LocalDate.of(2026, 11, 30) to "Payday"),
            paydaysComingUp(nov17),
        )
        assertEquals(listOf(LocalDate.of(2026, 11, 30) to "Payday"), paydaysComingUp(nov17, LocalDate.of(2026, 11, 16)))
    }

    @Test
    fun baonLeftAddsUpReceivedBaonThisPeriod() {
        val baon = BaonEntity(id = 7, name = "My baon", direction = BaonDirection.RECEIVE, amountCentavos = 50_000,
            period = BaonPeriod.WEEKLY, startDate = LocalDate.of(2026, 11, 2), createdAt = t0)
        val s = build(
            baons = listOf(baon),
            activity = listOf(
                BaonActivityRow(7, TxPurpose.BAON_RECEIVED, LocalDate.of(2026, 11, 23), 50_000),
                BaonActivityRow(7, TxPurpose.EXPENSE, LocalDate.of(2026, 11, 24), 12_000),
            ),
        )
        assertEquals(38_000L, s.baonLeftCentavos)
    }
}
