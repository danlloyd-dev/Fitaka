package com.pitakaview.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pitakaview.app.data.db.PitakaDatabase
import com.pitakaview.app.data.db.dao.BaonActivityRow
import com.pitakaview.app.data.db.dao.GoalProgress
import com.pitakaview.app.data.db.dao.LoanWithPaid
import com.pitakaview.app.data.db.dao.PayReceipt
import com.pitakaview.app.data.db.dao.PurposeTotal
import com.pitakaview.app.data.db.dao.RecentTransaction
import com.pitakaview.app.data.db.dao.WalletWithBalance
import com.pitakaview.app.data.db.entity.BaonEntity
import com.pitakaview.app.data.db.entity.PayScheduleEntity
import com.pitakaview.app.domain.PaydayTracker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Live dashboard: nine database queries plus the last-backup date, combined into one [HomeState].
 * Room re-runs a query only when a table it reads changes, and the flows stop 5 s after the screen goes away.
 *
 * Everything hangs off [today] (the app's DayClock): when the date changes, the month range and the
 * queries are rebuilt, so "this month" and "Coming up" never show yesterday's period.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    db: PitakaDatabase,
    today: StateFlow<LocalDate>,
    lastBackupAt: Flow<Instant?>,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val tx = db.transactionDao()

    val state: StateFlow<HomeState> = today.flatMapLatest { day ->
        val monthStart = day.withDayOfMonth(1)
        val monthEnd = day.with(TemporalAdjusters.lastDayOfMonth())
        val money = combine(
            db.walletDao().observeActiveWithBalance(),
            tx.observeTotalsByPurpose(monthStart, monthEnd),
            db.savingsGoalDao().observeProgress(),
            db.loanDao().observeOpen(),
        ) { wallets, totals, goals, loans -> Money(wallets, totals, goals, loans) }
        val plans = combine(
            db.baonDao().observeActive(),
            // Two months back covers the current period of any daily/weekly/monthly baon.
            tx.observeBaonActivity(since = day.minusDays(62)),
            db.payScheduleDao().observeActive(),
            tx.observeRecent(limit = 5),
            // Pay already received, so those paydays drop out of "Coming up".
            tx.observePayReceipts(since = day.minusDays(PaydayTracker.LOOK_BACK_DAYS)),
        ) { baons, activity, schedules, recent, receipts -> Plans(baons, activity, schedules, recent, receipts) }
        combine(money, plans, lastBackupAt) { m, p, backedUpAt ->
            HomeStateBuilder.build(
                day, m.wallets, m.totals, m.goals, m.loans, p.baons, p.activity, p.schedules, p.recent,
                lastBackupOn = backedUpAt?.atZone(zone)?.toLocalDate(),
                payReceipts = p.receipts,
                zone = zone,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    private data class Money(
        val wallets: List<WalletWithBalance>,
        val totals: List<PurposeTotal>,
        val goals: List<GoalProgress>,
        val loans: List<LoanWithPaid>,
    )

    private data class Plans(
        val baons: List<BaonEntity>,
        val activity: List<BaonActivityRow>,
        val schedules: List<PayScheduleEntity>,
        val recent: List<RecentTransaction>,
        val receipts: List<PayReceipt>,
    )
}
