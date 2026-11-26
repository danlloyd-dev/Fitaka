package com.pitakaview.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pitakaview.app.data.db.PitakaDatabase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Live dashboard: eight database queries combined into one [HomeState]. Room re-runs a query only when a
 * table it reads changes, and the flows stop 5 s after the screen goes away.
 */
class HomeViewModel(db: PitakaDatabase, today: LocalDate = LocalDate.now()) : ViewModel() {

    private val tx = db.transactionDao()
    private val monthStart = today.withDayOfMonth(1)
    private val monthEnd = today.with(TemporalAdjusters.lastDayOfMonth())

    private val money = combine(
        db.walletDao().observeActiveWithBalance(),
        tx.observeTotalsByPurpose(monthStart, monthEnd),
        db.savingsGoalDao().observeProgress(),
        db.loanDao().observeOpen(),
    ) { wallets, totals, goals, loans -> Money(wallets, totals, goals, loans) }

    private val plans = combine(
        db.baonDao().observeActive(),
        // Two months back covers the current period of any daily/weekly/monthly baon.
        tx.observeBaonActivity(since = today.minusDays(62)),
        db.payScheduleDao().observeActive(),
        tx.observeRecent(limit = 5),
    ) { baons, activity, schedules, recent -> Plans(baons, activity, schedules, recent) }

    val state: StateFlow<HomeState> = combine(money, plans) { m, p ->
        HomeStateBuilder.build(today, m.wallets, m.totals, m.goals, m.loans, p.baons, p.activity, p.schedules, p.recent)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    private data class Money(
        val wallets: List<com.pitakaview.app.data.db.dao.WalletWithBalance>,
        val totals: List<com.pitakaview.app.data.db.dao.PurposeTotal>,
        val goals: List<com.pitakaview.app.data.db.dao.GoalProgress>,
        val loans: List<com.pitakaview.app.data.db.dao.LoanWithPaid>,
    )

    private data class Plans(
        val baons: List<com.pitakaview.app.data.db.entity.BaonEntity>,
        val activity: List<com.pitakaview.app.data.db.dao.BaonActivityRow>,
        val schedules: List<com.pitakaview.app.data.db.entity.PayScheduleEntity>,
        val recent: List<com.pitakaview.app.data.db.dao.RecentTransaction>,
    )
}
