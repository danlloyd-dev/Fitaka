package com.pitakaview.app.ui.ledger

import com.pitakaview.app.data.db.dao.RecentTransaction
import com.pitakaview.app.data.db.dao.TransactionDao
import com.pitakaview.app.domain.TxPurpose
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth

/** One day's entries with their sum. */
data class DayGroup(val date: LocalDate, val totalCentavos: Long, val rows: List<RecentTransaction>)

/** How much of the month went to (or came from) one category. */
data class CategoryShare(val name: String, val totalCentavos: Long, val iconKey: String? = null)

/** One month of one section (Gastos or Kita): its total, its categories and its entries by day. */
data class MonthLedger(
    val month: YearMonth,
    val isThisMonth: Boolean,
    val totalCentavos: Long = 0,
    val entryCount: Int = 0,
    val categories: List<CategoryShare> = emptyList(),
    val days: List<DayGroup> = emptyList(),
    /** False until the database has answered, so an empty month is not announced too early. */
    val loaded: Boolean = false,
)

object MonthLedgerBuilder {
    const val NO_CATEGORY = "No category"

    /** [rows] are one purpose's entries for [month], newest first. Everything shown is added up from them: one query per screen. */
    fun build(month: YearMonth, isThisMonth: Boolean, rows: List<RecentTransaction>) = MonthLedger(
        month = month,
        isThisMonth = isThisMonth,
        totalCentavos = rows.sumOf { it.amountCentavos },
        entryCount = rows.size,
        categories = rows.groupBy { it.categoryName ?: NO_CATEGORY }
            .map { (name, list) -> CategoryShare(name, list.sumOf { it.amountCentavos }, list.first().categoryIconKey) }
            .sortedWith(compareByDescending<CategoryShare> { it.totalCentavos }.thenBy { it.name }),
        days = groupByDay(rows),
        loaded = true,
    )

    /** Keeps the rows' order, so newest-first rows give newest-first days. */
    fun groupByDay(rows: List<RecentTransaction>): List<DayGroup> =
        rows.groupBy { it.occurredOn }.map { (date, list) -> DayGroup(date, list.sumOf { it.amountCentavos }, list) }

    /** "42%", "<1%" for a sliver, "" when there is no total to compare with. */
    fun percentText(partCentavos: Long, totalCentavos: Long): String {
        if (totalCentavos <= 0 || partCentavos <= 0) return ""
        val percent = Math.round(100.0 * partCentavos / totalCentavos)
        return if (percent == 0L) "<1%" else "$percent%"
    }
}

/** Which month a section screen shows, counted from the current month: 0 is this month, -1 the one before. */
class MonthOffset {
    val flow = MutableStateFlow(0)
    fun previous() = flow.update { it - 1 }
    fun next() = flow.update { it + 1 }
    fun reset() = flow.update { 0 }
}

/**
 * The live ledger for the chosen month. It hangs off [today] (the app's DayClock), so "this month"
 * moves on by itself when the month changes while the app is open.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun monthLedgerFlow(tx: TransactionDao, purpose: TxPurpose, today: Flow<LocalDate>, monthOffset: Flow<Int>): Flow<MonthLedger> =
    combine(today, monthOffset) { day, offset -> YearMonth.from(day).let { it to it.plusMonths(offset.toLong()) } }
        .distinctUntilChanged()
        .flatMapLatest { (thisMonth, month) ->
            tx.observeEntries(purpose, month.atDay(1), month.atEndOfMonth())
                .map { MonthLedgerBuilder.build(month, month == thisMonth, it) }
                // The month's name changes at once; its numbers follow when the database answers.
                .onStart { emit(MonthLedger(month, month == thisMonth)) }
        }
