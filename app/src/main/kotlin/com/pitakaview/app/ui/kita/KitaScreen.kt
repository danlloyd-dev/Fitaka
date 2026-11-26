package com.pitakaview.app.ui.kita

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pitakaview.app.data.db.PitakaDatabase
import com.pitakaview.app.data.db.dao.PayReceipt
import com.pitakaview.app.data.db.entity.PayScheduleEntity
import com.pitakaview.app.di.LocalAppContainer
import com.pitakaview.app.domain.PaydayTracker
import com.pitakaview.app.domain.TxPurpose
import com.pitakaview.app.domain.WaitingPayday
import com.pitakaview.app.ui.components.AmountText
import com.pitakaview.app.ui.components.EmptyState
import com.pitakaview.app.ui.components.PitakaButton
import com.pitakaview.app.ui.components.PitakaButtonStyle
import com.pitakaview.app.ui.components.PitakaCard
import com.pitakaview.app.ui.components.PitakaDivider
import com.pitakaview.app.ui.components.PitakaIconButton
import com.pitakaview.app.ui.components.PitakaListItem
import com.pitakaview.app.ui.components.PitakaScreen
import com.pitakaview.app.ui.components.SectionBadge
import com.pitakaview.app.ui.components.SectionHeader
import com.pitakaview.app.ui.home.friendlyDate
import com.pitakaview.app.ui.icons.PitakaIcons
import com.pitakaview.app.ui.icons.icon
import com.pitakaview.app.ui.ledger.CategoryBreakdownCard
import com.pitakaview.app.ui.ledger.MonthLedger
import com.pitakaview.app.ui.ledger.MonthOffset
import com.pitakaview.app.ui.ledger.MonthSwitcher
import com.pitakaview.app.ui.ledger.MonthTotalCard
import com.pitakaview.app.ui.ledger.dayGroups
import com.pitakaview.app.ui.ledger.monthLedgerFlow
import com.pitakaview.app.ui.ledger.monthName
import com.pitakaview.app.ui.theme.Section
import com.pitakaview.app.ui.theme.tagline
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** One pay schedule as the Kita tab shows it: the payday it is waiting for, if any. */
data class PaydayRow(val scheduleId: Long, val name: String, val expectedCentavos: Long, val next: WaitingPayday?)

object PaydayRows {
    /** One row per schedule, the soonest payday first. */
    fun build(schedules: List<PayScheduleEntity>, receipts: List<PayReceipt>, today: LocalDate, zone: ZoneId): List<PaydayRow> {
        val receivedOn = receipts.groupBy({ it.payScheduleId }, { it.occurredOn })
        return schedules.filter { it.isActive }
            .map { schedule ->
                val setUpOn = schedule.createdAt.atZone(zone).toLocalDate()
                val next = PaydayTracker.waiting(schedule.rule(), receivedOn[schedule.id].orEmpty(), today, setUpOn, count = 1).firstOrNull()
                PaydayRow(schedule.id, schedule.name, schedule.expectedAmountCentavos, next)
            }
            .sortedWith(compareBy<PaydayRow> { it.next?.date ?: LocalDate.MAX }.thenBy { it.scheduleId })
    }

    /** "Due today", "Was due Nov 15", "Due tomorrow" (it can already be marked received), "Next: Dec 15". */
    fun status(next: WaitingPayday?, today: LocalDate): String {
        if (next == null) return "No payday coming"
        val day = friendlyDate(next.date, today).let { if (it in DAY_WORDS) it.lowercase() else it }
        return when {
            next.date < today -> "Was due $day"
            next.canReceive -> "Due $day"
            else -> "Next: $day"
        }
    }

    private val DAY_WORDS = setOf("Today", "Yesterday", "Tomorrow")
}

/** The Kita tab: one month of income, and the paydays being waited for. */
@OptIn(ExperimentalCoroutinesApi::class)
class KitaViewModel(db: PitakaDatabase, today: StateFlow<LocalDate>, zone: ZoneId = ZoneId.systemDefault()) : ViewModel() {
    val month = MonthOffset()

    val ledger: StateFlow<MonthLedger> = monthLedgerFlow(db.transactionDao(), TxPurpose.INCOME, today, month.flow)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthLedger(YearMonth.from(today.value), isThisMonth = true))

    /** Null until the database has answered, so "no paydays" is not announced too early. */
    val paydays: StateFlow<List<PaydayRow>?> = today.flatMapLatest { day ->
        combine(
            db.payScheduleDao().observeActive(),
            db.transactionDao().observePayReceipts(since = day.minusDays(PaydayTracker.LOOK_BACK_DAYS)),
        ) { schedules, receipts -> PaydayRows.build(schedules, receipts, day, zone) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun KitaRoute(
    onAdd: () -> Unit,
    onOpenEntry: (Long) -> Unit,
    onSearch: () -> Unit,
    onAddPayday: () -> Unit,
    onOpenPayday: (Long) -> Unit,
    onReceived: (Long) -> Unit,
) {
    val container = LocalAppContainer.current
    val vm = viewModel { KitaViewModel(container.database, container.dayClock.today) }
    val ledger by vm.ledger.collectAsStateWithLifecycle()
    val paydays by vm.paydays.collectAsStateWithLifecycle()
    val today by container.dayClock.today.collectAsStateWithLifecycle()
    KitaContent(
        ledger, paydays, today, vm.month::previous, vm.month::next, vm.month::reset,
        onAdd, onOpenEntry, onSearch, onAddPayday, onOpenPayday, onReceived,
    )
}

@Composable
fun KitaContent(
    ledger: MonthLedger,
    paydays: List<PaydayRow>?,
    today: LocalDate,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onThisMonth: () -> Unit,
    onAdd: () -> Unit,
    onOpenEntry: (Long) -> Unit,
    onSearch: () -> Unit,
    onAddPayday: () -> Unit,
    onOpenPayday: (Long) -> Unit,
    onReceived: (Long) -> Unit,
) {
    val section = Section.KITA
    PitakaScreen(
        title = section.label,
        subtitle = section.tagline,
        actions = { PitakaIconButton(PitakaIcons.Search, "Search", onSearch) },
    ) {
        item(key = "month") { MonthSwitcher(ledger.month, ledger.isThisMonth, onPreviousMonth, onNextMonth, onThisMonth) }
        item(key = "total") { MonthTotalCard(section, "Income in ${monthName(ledger.month)}", ledger) }
        item(key = "add") {
            PitakaButton("Add income", onClick = onAdd, modifier = Modifier.fillMaxWidth(), style = PitakaButtonStyle.Primary, icon = PitakaIcons.Add)
        }

        item(key = "paydays-title") { SectionHeader("Paydays") }
        if (paydays != null) {
            item(key = "paydays") { PaydaysCard(paydays, today, onOpenPayday, onReceived) }
        }
        item(key = "add-payday") {
            PitakaButton("Add a payday", onClick = onAddPayday, modifier = Modifier.fillMaxWidth(), icon = PitakaIcons.Add)
        }

        if (ledger.loaded && ledger.days.isEmpty()) {
            item(key = "empty") {
                PitakaCard(Modifier.fillMaxWidth()) {
                    // A new entry is dated today, so the "tap to add" hint only fits the current month.
                    EmptyState(
                        "No income in ${monthName(ledger.month)}",
                        if (ledger.isThisMonth) "Tap Add income, or Received on a payday." else "Nothing was logged for this month.",
                    )
                }
            }
        }
        // One source of income needs no breakdown.
        if (ledger.categories.size > 1) {
            item(key = "categories-title") { SectionHeader("Where it came from") }
            item(key = "categories") { CategoryBreakdownCard(ledger.categories, ledger.totalCentavos, section) }
        }
        if (ledger.days.isNotEmpty()) {
            item(key = "entries-title") { SectionHeader("Entries") }
        }
        dayGroups(ledger.days, today, onOpenEntry)
    }
}

@Composable
private fun PaydaysCard(rows: List<PaydayRow>, today: LocalDate, onOpenPayday: (Long) -> Unit, onReceived: (Long) -> Unit) {
    PitakaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = if (rows.isEmpty()) 16.dp else 4.dp)) {
        if (rows.isEmpty()) {
            EmptyState("No paydays set up", "Add your salary days to see what is coming and mark it received.")
        }
        rows.forEachIndexed { i, row ->
            key(row.scheduleId) {
                if (i > 0) PitakaDivider()
                PitakaListItem(
                    title = row.name,
                    subtitle = PaydayRows.status(row.next, today),
                    leading = { SectionBadge(Section.KITA, Section.KITA.icon) },
                    trailing = { AmountText(row.expectedCentavos) },
                    onClick = { onOpenPayday(row.scheduleId) },
                )
                if (row.next?.canReceive == true) {
                    PitakaButton(
                        "Received",
                        onClick = { onReceived(row.scheduleId) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        icon = PitakaIcons.Check,
                    )
                }
            }
        }
    }
}
