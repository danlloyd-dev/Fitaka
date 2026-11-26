package com.pitakaview.app.ui.ipon

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
import com.pitakaview.app.data.db.dao.GoalProgress
import com.pitakaview.app.data.db.dao.RecentTransaction
import com.pitakaview.app.di.LocalAppContainer
import com.pitakaview.app.domain.Peso
import com.pitakaview.app.ui.components.EmptyState
import com.pitakaview.app.ui.components.PitakaButton
import com.pitakaview.app.ui.components.PitakaButtonStyle
import com.pitakaview.app.ui.components.PitakaCard
import com.pitakaview.app.ui.components.PitakaIconButton
import com.pitakaview.app.ui.components.PitakaScreen
import com.pitakaview.app.ui.components.SectionHeader
import com.pitakaview.app.ui.home.friendlyDate
import com.pitakaview.app.ui.icons.PitakaIcons
import com.pitakaview.app.ui.ledger.MonthLedgerBuilder
import com.pitakaview.app.ui.mascot.SleepyAlkansya
import com.pitakaview.app.ui.sections.DetailAction
import com.pitakaview.app.ui.sections.ItemDetailContent
import com.pitakaview.app.ui.sections.ItemDetailUi
import com.pitakaview.app.ui.sections.SectionItemCard
import com.pitakaview.app.ui.sections.SectionTotalsCard
import com.pitakaview.app.ui.theme.PitakaTheme
import com.pitakaview.app.ui.theme.Section
import com.pitakaview.app.ui.theme.tagline
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** One savings goal as the Ipon tab lists it. */
data class GoalRowUi(
    val id: Long,
    val name: String,
    val savedCentavos: Long,
    val lines: List<String>,
    /** How far to the target; null when the goal has none. */
    val fraction: Float?,
)

/** Everything the Ipon tab shows, from one query. */
data class IponUi(
    /** What is saved across the goals in use. Hidden goals are left out, as on Home. */
    val savedCentavos: Long = 0,
    val goals: List<GoalRowUi> = emptyList(),
    val hidden: List<GoalRowUi> = emptyList(),
    val loaded: Boolean = false,
)

object IponUiBuilder {
    fun build(goals: List<GoalProgress>, today: LocalDate): IponUi {
        val (hidden, inUse) = goals.partition { it.isArchived }
        return IponUi(
            savedCentavos = inUse.sumOf { it.savedCentavos },
            goals = inUse.map { row(it, today) },
            hidden = hidden.map { row(it, today) },
            loaded = true,
        )
    }

    fun fraction(goal: GoalProgress): Float? =
        goal.targetCentavos?.takeIf { it > 0 }?.let { (goal.savedCentavos.toFloat() / it).coerceIn(0f, 1f) }

    /** "of ₱45,000.00 · 12%" and "by Jun 30, 2027"; or "No target". */
    fun row(goal: GoalProgress, today: LocalDate): GoalRowUi {
        val target = goal.targetCentavos
        val lines = if (target == null) {
            listOf("No target")
        } else {
            val percent = MonthLedgerBuilder.percentText(goal.savedCentavos, target)
            listOfNotNull(
                "of ${Peso.format(target)}" + if (percent.isEmpty()) "" else " · $percent",
                goal.targetDate?.let { "by ${friendlyDate(it, today)}" },
            )
        }
        return GoalRowUi(goal.goalId, goal.name, goal.savedCentavos, lines, fraction(goal))
    }

    /** A goal's own screen. [walletName] is where it is kept, or null when money is only set aside. */
    fun detail(goal: GoalProgress, walletName: String?, entries: List<RecentTransaction>, today: LocalDate): ItemDetailUi {
        val target = goal.targetCentavos
        return ItemDetailUi(
            title = goal.name,
            subtitle = if (walletName != null) "Kept in $walletName" else "Set aside in your wallets",
            section = Section.IPON,
            amountCaption = "Saved",
            amountCentavos = goal.savedCentavos,
            lines = listOfNotNull(
                when {
                    target == null -> "No target: save as much as you like"
                    goal.savedCentavos >= target -> "Target of ${Peso.format(target)} reached"
                    else -> "${Peso.format(target - goal.savedCentavos)} to go of ${Peso.format(target)}"
                },
                goal.targetDate?.let { "Target date: ${friendlyDate(it, today)}" },
                if (goal.isArchived) "Hidden from Home and from new entries" else null,
            ),
            fraction = fraction(goal),
            days = MonthLedgerBuilder.groupByDay(entries),
        )
    }
}

class IponViewModel(db: PitakaDatabase, today: StateFlow<LocalDate>) : ViewModel() {
    val ui: StateFlow<IponUi> = combine(db.savingsGoalDao().observeAllProgress(), today) { goals, day -> IponUiBuilder.build(goals, day) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IponUi())
}

@Composable
fun IponRoute(onAdd: () -> Unit, onOpen: (Long) -> Unit, onSearch: () -> Unit) {
    val container = LocalAppContainer.current
    val vm = viewModel { IponViewModel(container.database, container.dayClock.today) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    IponContent(ui, onAdd, onOpen, onSearch)
}

@Composable
fun IponContent(ui: IponUi, onAdd: () -> Unit, onOpen: (Long) -> Unit, onSearch: () -> Unit) {
    val section = Section.IPON
    PitakaScreen(
        title = section.label,
        subtitle = section.tagline,
        actions = { PitakaIconButton(PitakaIcons.Search, "Search", onSearch) },
    ) {
        item(key = "totals") { SectionTotalsCard(section, listOf("Saved" to ui.savedCentavos)) }
        item(key = "add") {
            PitakaButton("Add a goal", onClick = onAdd, modifier = Modifier.fillMaxWidth(), style = PitakaButtonStyle.Primary, icon = PitakaIcons.Add)
        }
        if (ui.loaded && ui.goals.isEmpty() && ui.hidden.isEmpty()) {
            item(key = "empty") {
                PitakaCard(Modifier.fillMaxWidth()) { EmptyState("No savings goals", "Add one, with or without a target, and put money toward it.", mascot = SleepyAlkansya) }
            }
        }
        for ((title, rows) in listOf("Goals" to ui.goals, "Hidden" to ui.hidden)) {
            if (rows.isEmpty()) continue
            item(key = "title-$title") { SectionHeader(title) }
            items(rows, key = { "goal-${it.id}" }) { goal ->
                SectionItemCard(
                    section, title = goal.name, amount = Peso.format(goal.savedCentavos), amountCaption = "saved",
                    lines = goal.lines, fraction = goal.fraction, onClick = { onOpen(goal.id) },
                )
            }
        }
    }
}

// ---------- one goal ----------

sealed interface GoalDetailState {
    data object Gone : GoalDetailState
    data class Shown(val ui: ItemDetailUi, val canTakeOut: Boolean) : GoalDetailState
}

class GoalDetailViewModel(db: PitakaDatabase, goalId: Long, today: StateFlow<LocalDate>) : ViewModel() {
    /** Null while loading; [GoalDetailState.Gone] once the goal no longer exists. */
    val state: StateFlow<GoalDetailState?> = combine(
        db.savingsGoalDao().observeProgressOf(goalId),
        db.transactionDao().observeForGoal(goalId),
        today,
    ) { goal, entries, day ->
        if (goal == null) {
            GoalDetailState.Gone
        } else {
            val walletName = goal.walletId?.let { db.walletDao().get(it)?.name }
            GoalDetailState.Shown(IponUiBuilder.detail(goal, walletName, entries, day), canTakeOut = goal.savedCentavos > 0)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun GoalDetailRoute(goalId: Long, onBack: () -> Unit, onEdit: () -> Unit, onAdd: () -> Unit, onTakeOut: () -> Unit, onOpenEntry: (Long) -> Unit) {
    val container = LocalAppContainer.current
    val vm = viewModel { GoalDetailViewModel(container.database, goalId, container.dayClock.today) }
    val state by vm.state.collectAsStateWithLifecycle()
    val today by container.dayClock.today.collectAsStateWithLifecycle()
    when (val shown = state) {
        null -> Box(Modifier.fillMaxSize().background(PitakaTheme.colors.background))
        GoalDetailState.Gone -> LaunchedEffect(Unit) { onBack() }
        is GoalDetailState.Shown -> ItemDetailContent(
            ui = shown.ui,
            today = today,
            actions = listOf(
                DetailAction("Add to savings", onAdd, primary = true),
                DetailAction("Take out", onTakeOut, enabled = shown.canTakeOut),
            ),
            editLabel = "Edit goal",
            onEdit = onEdit,
            onOpenEntry = onOpenEntry,
            onBack = onBack,
        )
    }
}
