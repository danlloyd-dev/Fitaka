package com.pitakaview.app.ui.home

import com.pitakaview.app.data.db.dao.BaonActivityRow
import com.pitakaview.app.data.db.dao.GoalProgress
import com.pitakaview.app.data.db.dao.LoanWithPaid
import com.pitakaview.app.data.db.dao.PurposeTotal
import com.pitakaview.app.data.db.dao.RecentTransaction
import com.pitakaview.app.data.db.dao.WalletWithBalance
import com.pitakaview.app.data.db.dao.remainingCentavos
import com.pitakaview.app.data.db.entity.BaonEntity
import com.pitakaview.app.data.db.entity.PayScheduleEntity
import com.pitakaview.app.domain.BaonDirection
import com.pitakaview.app.domain.BaonMath
import com.pitakaview.app.domain.LoanDirection
import com.pitakaview.app.domain.PayCalendar
import com.pitakaview.app.domain.TxPurpose
import com.pitakaview.app.ui.theme.Section
import java.time.LocalDate

/** Something due soon: a payday or a loan due date. */
data class UpcomingItem(
    val date: LocalDate,
    val title: String,
    val detail: String,
    val section: Section,
    val amountCentavos: Long?,
)

/** Why Home is asking for a backup. */
enum class BackupNudge { NEVER_BACKED_UP, BACKUP_IS_OLD }

/** Everything the Home dashboard shows. Each tile matches its section's own screen. */
data class HomeState(
    val wallets: List<WalletWithBalance> = emptyList(),
    val incomeCentavos: Long = 0,
    val spendingCentavos: Long = 0,
    val savedCentavos: Long = 0,
    val iOweCentavos: Long = 0,
    val owedToMeCentavos: Long = 0,
    /** Left this period across baon you receive; null when you have none. */
    val baonLeftCentavos: Long? = null,
    val upcoming: List<UpcomingItem> = emptyList(),
    val recent: List<RecentTransaction> = emptyList(),
    /** Set when there are entries worth protecting and no recent backup. */
    val backupNudge: BackupNudge? = null,
) {
    /** "Your whole wallet": every active wallet added up. */
    val totalCentavos: Long get() = wallets.sumOf { it.balanceCentavos }
}

object HomeStateBuilder {
    const val UPCOMING_DAYS = 14L
    const val UPCOMING_MAX = 5
    const val BACKUP_EVERY_DAYS = 30L

    fun build(
        today: LocalDate,
        wallets: List<WalletWithBalance>,
        monthTotals: List<PurposeTotal>,
        goals: List<GoalProgress>,
        openLoans: List<LoanWithPaid>,
        baons: List<BaonEntity>,
        baonActivity: List<BaonActivityRow>,
        paySchedules: List<PayScheduleEntity>,
        recent: List<RecentTransaction>,
        lastBackupOn: LocalDate? = null,
    ): HomeState {
        val totals = monthTotals.associate { it.purpose to it.totalCentavos }
        val horizon = today.plusDays(UPCOMING_DAYS)

        val paydays = paySchedules.filter { it.isActive }.flatMap { schedule ->
            PayCalendar.upcoming(schedule.rule(), from = today, count = 2)
                .filter { it <= horizon }
                .map { UpcomingItem(it, schedule.name, "Payday", Section.KITA, schedule.expectedAmountCentavos) }
        }
        // Overdue loans stay listed (date in the past) until paid.
        val loanDues = openLoans.mapNotNull { row ->
            val due = row.loan.dueDate ?: return@mapNotNull null
            if (row.remainingCentavos <= 0 || due > horizon) return@mapNotNull null
            val who = if (row.loan.direction == LoanDirection.I_OWE) "You pay" else "Owes you"
            UpcomingItem(due, row.loan.counterparty, if (due < today) "$who · overdue" else who, Section.UTANG, row.remainingCentavos)
        }

        val receiving = baons.filter { it.isActive && it.direction == BaonDirection.RECEIVE }
        return HomeState(
            wallets = wallets,
            incomeCentavos = totals[TxPurpose.INCOME] ?: 0,
            spendingCentavos = totals[TxPurpose.EXPENSE] ?: 0,
            savedCentavos = goals.sumOf { it.savedCentavos },
            iOweCentavos = openLoans.filter { it.loan.direction == LoanDirection.I_OWE }.sumOf { maxOf(0, it.remainingCentavos) },
            owedToMeCentavos = openLoans.filter { it.loan.direction == LoanDirection.OWED_TO_ME }.sumOf { maxOf(0, it.remainingCentavos) },
            baonLeftCentavos = if (receiving.isEmpty()) null else receiving.sumOf { BaonMath.summarize(it, baonActivity, today).leftCentavos },
            upcoming = (paydays + loanDues).sortedBy { it.date }.take(UPCOMING_MAX),
            recent = recent,
            // An empty app has nothing to lose, so it is never nagged.
            backupNudge = when {
                recent.isEmpty() -> null
                lastBackupOn == null -> BackupNudge.NEVER_BACKED_UP
                lastBackupOn < today.minusDays(BACKUP_EVERY_DAYS) -> BackupNudge.BACKUP_IS_OLD
                else -> null
            },
        )
    }
}
