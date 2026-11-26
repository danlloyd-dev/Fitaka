package com.pitakaview.app.ui.utang

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pitakaview.app.data.db.PitakaDatabase
import com.pitakaview.app.data.db.dao.LoanWithPaid
import com.pitakaview.app.data.db.dao.RecentTransaction
import com.pitakaview.app.data.db.entity.LoanEntity
import com.pitakaview.app.di.LocalAppContainer
import com.pitakaview.app.domain.LoanDirection
import com.pitakaview.app.domain.LoanMath
import com.pitakaview.app.domain.Peso
import com.pitakaview.app.ui.components.EmptyState
import com.pitakaview.app.ui.components.PitakaButton
import com.pitakaview.app.ui.components.PitakaButtonStyle
import com.pitakaview.app.ui.components.PitakaCard
import com.pitakaview.app.ui.components.PitakaIconButton
import com.pitakaview.app.ui.components.PitakaScreen
import com.pitakaview.app.ui.components.SectionHeader
import com.pitakaview.app.ui.icons.PitakaIcons
import com.pitakaview.app.ui.ledger.MonthLedgerBuilder
import com.pitakaview.app.ui.sections.DetailAction
import com.pitakaview.app.ui.sections.ItemDetailContent
import com.pitakaview.app.ui.sections.ItemDetailUi
import com.pitakaview.app.ui.sections.SectionItemCard
import com.pitakaview.app.ui.sections.SectionTotalsCard
import com.pitakaview.app.ui.sections.dateInText
import com.pitakaview.app.ui.theme.PitakaTheme
import com.pitakaview.app.ui.theme.Section
import com.pitakaview.app.ui.theme.tagline
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** One loan as the Utang tab lists it. */
data class LoanRowUi(
    val id: Long,
    val who: String,
    val leftCentavos: Long,
    val status: String,
    val fraction: Float,
)

/** Everything the Utang tab shows, from one query. */
data class UtangUi(
    val iOweCentavos: Long = 0,
    val owedToMeCentavos: Long = 0,
    val iOwe: List<LoanRowUi> = emptyList(),
    val owedToMe: List<LoanRowUi> = emptyList(),
    val settled: List<LoanRowUi> = emptyList(),
    val loaded: Boolean = false,
) {
    val isEmpty: Boolean get() = iOwe.isEmpty() && owedToMe.isEmpty() && settled.isEmpty()
}

object UtangUiBuilder {
    fun build(loans: List<LoanWithPaid>, today: LocalDate): UtangUi {
        val (settled, open) = loans.partition { LoanMath.isSettled(it.loan, it.paidCentavos) }
        // Soonest due first; loans with no date after those, oldest first.
        val byDue = open.sortedWith(compareBy<LoanWithPaid> { LoanMath.nextDue(it.loan, it.paidCentavos)?.date ?: LocalDate.MAX }.thenBy { it.loan.id })
        fun rows(direction: LoanDirection) = byDue.filter { it.loan.direction == direction }.map { row(it, today) }
        fun left(direction: LoanDirection) = open.filter { it.loan.direction == direction }.sumOf { LoanMath.remaining(it.loan, it.paidCentavos) }
        return UtangUi(
            iOweCentavos = left(LoanDirection.I_OWE),
            owedToMeCentavos = left(LoanDirection.OWED_TO_ME),
            iOwe = rows(LoanDirection.I_OWE),
            owedToMe = rows(LoanDirection.OWED_TO_ME),
            settled = settled.sortedByDescending { it.loan.id }.map { row(it, today) },
            loaded = true,
        )
    }

    fun row(row: LoanWithPaid, today: LocalDate) = LoanRowUi(
        id = row.loan.id,
        who = row.loan.counterparty,
        leftCentavos = LoanMath.remaining(row.loan, row.paidCentavos).coerceAtLeast(0),
        status = status(row.loan, row.paidCentavos, today),
        fraction = LoanMath.paidFraction(row.loan, row.paidCentavos),
    )

    /** "₱2,000.00 due Dec 5", "Overdue since Nov 20", "No due date", "Paid in full", "Marked settled". */
    fun status(loan: LoanEntity, paidCentavos: Long, today: LocalDate): String {
        if (LoanMath.remaining(loan, paidCentavos) <= 0) return "Paid in full"
        if (loan.closedAt != null) return "Marked settled"
        val due = LoanMath.nextDue(loan, paidCentavos) ?: return "No due date"
        val day = dateInText(due.date, today)
        val overdue = due.date < today
        return when {
            due.isInstallment && overdue -> "${Peso.format(due.amountCentavos)} overdue since $day"
            due.isInstallment -> "${Peso.format(due.amountCentavos)} due $day"
            overdue -> "Overdue since $day"
            else -> "Due $day"
        }
    }

    /** A loan's own screen. */
    fun detail(row: LoanWithPaid, entries: List<RecentTransaction>, today: LocalDate): ItemDetailUi {
        val loan = row.loan
        val owe = loan.direction == LoanDirection.I_OWE
        return ItemDetailUi(
            title = loan.counterparty,
            subtitle = if (owe) "You owe" else "Owes you",
            section = Section.UTANG,
            amountCaption = if (owe) "Left to pay" else "Left to collect",
            amountCentavos = LoanMath.remaining(loan, row.paidCentavos).coerceAtLeast(0),
            lines = listOfNotNull(
                "${if (owe) "Paid" else "Collected"} ${Peso.format(row.paidCentavos)} of ${Peso.format(loan.totalPayableCentavos)}",
                status(loan, row.paidCentavos, today),
                loan.note,
            ),
            fraction = LoanMath.paidFraction(loan, row.paidCentavos),
            days = MonthLedgerBuilder.groupByDay(entries),
        )
    }
}

class UtangViewModel(db: PitakaDatabase, today: StateFlow<LocalDate>) : ViewModel() {
    val ui: StateFlow<UtangUi> = combine(db.loanDao().observeAllWithPaid(), today) { loans, day -> UtangUiBuilder.build(loans, day) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UtangUi())
}

@Composable
fun UtangRoute(onAdd: () -> Unit, onOpen: (Long) -> Unit, onSearch: () -> Unit) {
    val container = LocalAppContainer.current
    val vm = viewModel { UtangViewModel(container.database, container.dayClock.today) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    UtangContent(ui, onAdd, onOpen, onSearch)
}

@Composable
fun UtangContent(ui: UtangUi, onAdd: () -> Unit, onOpen: (Long) -> Unit, onSearch: () -> Unit) {
    val section = Section.UTANG
    PitakaScreen(
        title = section.label,
        subtitle = section.tagline,
        actions = { PitakaIconButton(PitakaIcons.Search, "Search", onSearch) },
    ) {
        item(key = "totals") { SectionTotalsCard(section, listOf("You owe" to ui.iOweCentavos, "Owed to you" to ui.owedToMeCentavos)) }
        item(key = "add") {
            PitakaButton("Add a loan", onClick = onAdd, modifier = Modifier.fillMaxWidth(), style = PitakaButtonStyle.Primary, icon = PitakaIcons.Add)
        }
        if (ui.loaded && ui.isEmpty) {
            item(key = "empty") {
                PitakaCard(Modifier.fillMaxWidth()) { EmptyState("No loans", "Add money you borrowed or lent, and its payments are tracked here.") }
            }
        }
        for ((title, rows) in listOf("You owe" to ui.iOwe, "Owed to you" to ui.owedToMe, "Settled" to ui.settled)) {
            if (rows.isEmpty()) continue
            item(key = "title-$title") { SectionHeader(title) }
            items(rows, key = { "loan-${it.id}" }) { loan ->
                SectionItemCard(
                    section, title = loan.who, amount = Peso.format(loan.leftCentavos), amountCaption = "left",
                    lines = listOf(loan.status), fraction = loan.fraction, onClick = { onOpen(loan.id) },
                )
            }
        }
    }
}

// ---------- one loan ----------

class LoanDetailViewModel(db: PitakaDatabase, loanId: Long, today: StateFlow<LocalDate>) : ViewModel() {
    /** Null while loading; [Gone] once the loan no longer exists. */
    val state: StateFlow<LoanDetailState?> = combine(
        db.loanDao().observeWithPaid(loanId),
        db.transactionDao().observeForLoan(loanId),
        today,
    ) { row, entries, day ->
        if (row == null) {
            LoanDetailState.Gone
        } else {
            LoanDetailState.Shown(UtangUiBuilder.detail(row, entries, day), row.loan.direction, LoanMath.isSettled(row.loan, row.paidCentavos))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

sealed interface LoanDetailState {
    data object Gone : LoanDetailState
    data class Shown(val ui: ItemDetailUi, val direction: LoanDirection, val settled: Boolean) : LoanDetailState
}

@Composable
fun LoanDetailRoute(loanId: Long, onBack: () -> Unit, onEdit: () -> Unit, onAddPayment: () -> Unit, onOpenEntry: (Long) -> Unit) {
    val container = LocalAppContainer.current
    val vm = viewModel { LoanDetailViewModel(container.database, loanId, container.dayClock.today) }
    val state by vm.state.collectAsStateWithLifecycle()
    val today by container.dayClock.today.collectAsStateWithLifecycle()
    when (val shown = state) {
        null -> Box(Modifier.fillMaxSize().background(PitakaTheme.colors.background))
        LoanDetailState.Gone -> LaunchedEffect(Unit) { onBack() }
        is LoanDetailState.Shown -> ItemDetailContent(
            ui = shown.ui,
            today = today,
            actions = listOf(
                DetailAction(
                    if (shown.direction == LoanDirection.I_OWE) "Add a payment" else "Payment received",
                    onAddPayment, primary = true, enabled = !shown.settled,
                ),
            ),
            editLabel = "Edit loan",
            onEdit = onEdit,
            onOpenEntry = onOpenEntry,
            onBack = onBack,
        )
    }
}
