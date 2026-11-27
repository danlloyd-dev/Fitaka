package com.pitakaview.app.ui.summary

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pitakaview.app.data.db.FitakaDatabase
import com.pitakaview.app.di.LocalAppContainer
import com.pitakaview.app.domain.TxPurpose
import com.pitakaview.app.ui.components.AmountText
import com.pitakaview.app.ui.components.EmptyState
import com.pitakaview.app.ui.components.FitakaCard
import com.pitakaview.app.ui.components.FitakaDivider
import com.pitakaview.app.ui.components.FitakaListItem
import com.pitakaview.app.ui.components.FitakaScreen
import com.pitakaview.app.ui.components.SectionBadge
import com.pitakaview.app.ui.components.SectionHeader
import com.pitakaview.app.ui.icons.icon
import com.pitakaview.app.ui.ledger.CategoryBreakdownCard
import com.pitakaview.app.ui.ledger.MonthOffset
import com.pitakaview.app.ui.ledger.MonthSwitcher
import com.pitakaview.app.ui.ledger.monthName
import com.pitakaview.app.ui.motion.AnimatedAmountText
import com.pitakaview.app.ui.theme.FitakaTheme
import com.pitakaview.app.ui.theme.Section
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

/**
 * The monthly summary: two database queries per month shown (six months of sums for the chart and the
 * numbers, and this month's spending by category). It follows the app's clock like the other month screens.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SummaryViewModel(db: FitakaDatabase, today: StateFlow<LocalDate>) : ViewModel() {
    val month = MonthOffset()

    val summary: StateFlow<MonthSummary> =
        combine(today, month.flow) { day, offset -> YearMonth.from(day).let { it to it.plusMonths(offset.toLong()) } }
            .distinctUntilChanged()
            .flatMapLatest { (thisMonth, shown) ->
                val first = shown.minusMonths(MonthSummaryBuilder.MONTHS - 1L)
                combine(
                    db.transactionDao().observeMonthlyTotals(first.atDay(1), shown.atEndOfMonth()),
                    db.transactionDao().observeCategoryTotals(TxPurpose.EXPENSE, shown.atDay(1), shown.atEndOfMonth()),
                ) { totals, categories -> MonthSummaryBuilder.build(shown, shown == thisMonth, totals, categories) }
                    // The month's name changes at once; its numbers follow when the database answers.
                    .onStart { emit(MonthSummary(shown, shown == thisMonth)) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthSummary(YearMonth.from(today.value), isThisMonth = true))
}

@Composable
fun SummaryRoute(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm = viewModel { SummaryViewModel(container.database, container.dayClock.today) }
    val summary by vm.summary.collectAsStateWithLifecycle()
    SummaryContent(summary, vm.month::previous, vm.month::next, vm.month::reset, onBack)
}

@Composable
fun SummaryContent(
    summary: MonthSummary,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onThisMonth: () -> Unit,
    onBack: () -> Unit,
) {
    val c = FitakaTheme.colors
    val type = MaterialTheme.typography
    val month = monthName(summary.month)
    FitakaScreen(title = "Summary", subtitle = "Month by month", onBack = onBack) {
        item(key = "month") { MonthSwitcher(summary.month, summary.isThisMonth, onPreviousMonth, onNextMonth, onThisMonth) }

        item(key = "left") {
            FitakaCard(Modifier.fillMaxWidth()) {
                Text("Left over in $month", style = type.titleSmall, color = c.ink)
                AnimatedAmountText(summary.leftCentavos, style = type.displaySmall)
                Text("What came in, minus what went out.", style = type.bodySmall, color = c.inkMuted)
                MonthSummaryBuilder.comparison(summary)?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = type.bodyMedium, color = c.ink)
                    // Early in a month "less than last month" is only because the month is young.
                    if (summary.isThisMonth) Text("So far this month.", style = type.bodySmall, color = c.inkMuted)
                }
            }
        }

        item(key = "numbers") {
            FitakaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                SummaryRow(Section.KITA, "Kita", "Income", summary.incomeCentavos)
                if (summary.baonReceivedCentavos != 0L) {
                    FitakaDivider()
                    SummaryRow(Section.BAON, "Baon received", "Counts as money in", summary.baonReceivedCentavos)
                }
                FitakaDivider()
                SummaryRow(Section.GASTOS, "Gastos", "Spending", summary.spendingCentavos)
                if (summary.baonGivenCentavos != 0L) {
                    FitakaDivider()
                    SummaryRow(Section.BAON, "Baon given", "Counts as money out", summary.baonGivenCentavos)
                }
                FitakaDivider()
                SummaryRow(Section.IPON, "Ipon", "Set aside this month", summary.setAsideCentavos)
            }
        }

        item(key = "chart") {
            FitakaCard(Modifier.fillMaxWidth()) {
                Text("The last 6 months", style = type.titleSmall, color = c.ink)
                Spacer(Modifier.height(6.dp))
                if (summary.hasChart) {
                    ChartLegend()
                    Spacer(Modifier.height(8.dp))
                    MonthBarsChart(summary.bars)
                } else if (summary.loaded) {
                    EmptyState("Nothing to chart yet", "Bars appear here once entries are logged.")
                }
            }
        }

        if (summary.topCategories.isNotEmpty()) {
            item(key = "categories-title") { SectionHeader("Where it went") }
            item(key = "categories") { CategoryBreakdownCard(summary.topCategories, summary.spendingCentavos, Section.GASTOS) }
        }
    }
}

@Composable
private fun SummaryRow(section: Section, title: String, subtitle: String, centavos: Long) {
    FitakaListItem(
        title = title,
        subtitle = subtitle,
        leading = { SectionBadge(section, section.icon) },
        trailing = { AmountText(centavos) },
    )
}
