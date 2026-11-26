package com.pitakaview.app.ui.gastos

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pitakaview.app.data.db.PitakaDatabase
import com.pitakaview.app.di.LocalAppContainer
import com.pitakaview.app.domain.TxPurpose
import com.pitakaview.app.ui.components.EmptyState
import com.pitakaview.app.ui.components.PitakaButton
import com.pitakaview.app.ui.components.PitakaButtonStyle
import com.pitakaview.app.ui.components.PitakaCard
import com.pitakaview.app.ui.components.PitakaIconButton
import com.pitakaview.app.ui.components.PitakaScreen
import com.pitakaview.app.ui.components.SectionHeader
import com.pitakaview.app.ui.icons.PitakaIcons
import com.pitakaview.app.ui.ledger.CategoryBreakdownCard
import com.pitakaview.app.ui.ledger.MonthLedger
import com.pitakaview.app.ui.ledger.MonthOffset
import com.pitakaview.app.ui.ledger.MonthSwitcher
import com.pitakaview.app.ui.ledger.MonthTotalCard
import com.pitakaview.app.ui.ledger.dayGroups
import com.pitakaview.app.ui.ledger.monthLedgerFlow
import com.pitakaview.app.ui.ledger.monthName
import com.pitakaview.app.ui.mascot.SleepyAlkansya
import com.pitakaview.app.ui.theme.Section
import com.pitakaview.app.ui.theme.tagline
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** The Gastos tab: one month of spending, from one database query. */
class GastosViewModel(db: PitakaDatabase, today: StateFlow<LocalDate>) : ViewModel() {
    val month = MonthOffset()

    val ledger: StateFlow<MonthLedger> = monthLedgerFlow(db.transactionDao(), TxPurpose.EXPENSE, today, month.flow)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthLedger(YearMonth.from(today.value), isThisMonth = true))
}

@Composable
fun GastosRoute(onAdd: () -> Unit, onOpenEntry: (Long) -> Unit, onSearch: () -> Unit) {
    val container = LocalAppContainer.current
    val vm = viewModel { GastosViewModel(container.database, container.dayClock.today) }
    val ledger by vm.ledger.collectAsStateWithLifecycle()
    val today by container.dayClock.today.collectAsStateWithLifecycle()
    GastosContent(ledger, today, vm.month::previous, vm.month::next, vm.month::reset, onAdd, onOpenEntry, onSearch)
}

@Composable
fun GastosContent(
    ledger: MonthLedger,
    today: LocalDate,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onThisMonth: () -> Unit,
    onAdd: () -> Unit,
    onOpenEntry: (Long) -> Unit,
    onSearch: () -> Unit,
) {
    val section = Section.GASTOS
    PitakaScreen(
        title = section.label,
        subtitle = section.tagline,
        actions = { PitakaIconButton(PitakaIcons.Search, "Search", onSearch) },
    ) {
        item(key = "month") { MonthSwitcher(ledger.month, ledger.isThisMonth, onPreviousMonth, onNextMonth, onThisMonth) }
        item(key = "total") { MonthTotalCard(section, "Spent in ${monthName(ledger.month)}", ledger) }
        item(key = "add") {
            PitakaButton("Add expense", onClick = onAdd, modifier = Modifier.fillMaxWidth(), style = PitakaButtonStyle.Primary, icon = PitakaIcons.Add)
        }
        if (ledger.loaded && ledger.days.isEmpty()) {
            item(key = "empty") {
                PitakaCard(Modifier.fillMaxWidth()) {
                    // A new entry is dated today, so the "tap to add" hint only fits the current month.
                    EmptyState(
                        "No spending in ${monthName(ledger.month)}",
                        if (ledger.isThisMonth) "Tap Add expense to log what you spend." else "Nothing was logged for this month.",
                        mascot = SleepyAlkansya,
                    )
                }
            }
        }
        if (ledger.categories.isNotEmpty()) {
            item(key = "categories-title") { SectionHeader("Where it went") }
            item(key = "categories") { CategoryBreakdownCard(ledger.categories, ledger.totalCentavos, section) }
            item(key = "entries-title") { SectionHeader("Entries") }
        }
        dayGroups(ledger.days, today, onOpenEntry)
    }
}
