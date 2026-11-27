package com.pitakaview.app.ui.baon

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
import com.pitakaview.app.data.db.FitakaDatabase
import com.pitakaview.app.data.db.dao.BaonActivityRow
import com.pitakaview.app.data.db.dao.RecentTransaction
import com.pitakaview.app.data.db.entity.BaonEntity
import com.pitakaview.app.di.LocalAppContainer
import com.pitakaview.app.domain.BaonDirection
import com.pitakaview.app.domain.BaonMath
import com.pitakaview.app.domain.BaonPeriod
import com.pitakaview.app.domain.BaonSummary
import com.pitakaview.app.domain.Peso
import com.pitakaview.app.ui.components.EmptyState
import com.pitakaview.app.ui.components.FitakaButton
import com.pitakaview.app.ui.components.FitakaButtonStyle
import com.pitakaview.app.ui.components.FitakaCard
import com.pitakaview.app.ui.components.FitakaIconButton
import com.pitakaview.app.ui.components.FitakaScreen
import com.pitakaview.app.ui.components.SectionHeader
import com.pitakaview.app.ui.icons.FitakaIcons
import com.pitakaview.app.ui.ledger.MonthLedgerBuilder
import com.pitakaview.app.ui.mascot.SleepyAlkansya
import com.pitakaview.app.ui.sections.DetailAction
import com.pitakaview.app.ui.sections.ItemDetailContent
import com.pitakaview.app.ui.sections.ItemDetailUi
import com.pitakaview.app.ui.sections.SectionItemCard
import com.pitakaview.app.ui.sections.SectionTotalsCard
import com.pitakaview.app.ui.theme.FitakaTheme
import com.pitakaview.app.ui.theme.Section
import com.pitakaview.app.ui.theme.tagline
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/** One baon as the Baon tab lists it, for its current period. */
data class BaonRowUi(
    val id: Long,
    val name: String,
    val direction: BaonDirection,
    /** What is left of a baon received; what has been given of a baon given. */
    val amountCentavos: Long,
    val amountCaption: String,
    val lines: List<String>,
    val fraction: Float?,
)

/** Everything the Baon tab shows, from two queries. */
data class BaonUi(
    /** Left this period across the baon received; null when there is none. */
    val leftCentavos: Long? = null,
    val receiving: List<BaonRowUi> = emptyList(),
    val giving: List<BaonRowUi> = emptyList(),
    val stopped: List<BaonRowUi> = emptyList(),
    val loaded: Boolean = false,
) {
    val isEmpty: Boolean get() = receiving.isEmpty() && giving.isEmpty() && stopped.isEmpty()
}

object BaonUiBuilder {
    /** How far back entries can matter: two months covers the current period of any daily, weekly or monthly baon. */
    const val LOOK_BACK_DAYS = 62L

    private val shortDate = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

    fun build(baons: List<BaonEntity>, activity: List<BaonActivityRow>, today: LocalDate): BaonUi {
        val (active, stopped) = baons.partition { it.isActive }
        val receiving = active.filter { it.direction == BaonDirection.RECEIVE }
        return BaonUi(
            leftCentavos = if (receiving.isEmpty()) null else receiving.sumOf { BaonMath.summarize(it, activity, today).leftCentavos },
            receiving = receiving.map { row(it, activity, today) },
            giving = active.filter { it.direction == BaonDirection.GIVE }.map { row(it, activity, today) },
            stopped = stopped.map { row(it, activity, today) },
            loaded = true,
        )
    }

    fun row(baon: BaonEntity, activity: List<BaonActivityRow>, today: LocalDate): BaonRowUi {
        val summary = BaonMath.summarize(baon, activity, today)
        val fraction = if (summary.budgetCentavos > 0) (summary.usedCentavos.toFloat() / summary.budgetCentavos).coerceIn(0f, 1f) else null
        return if (baon.direction == BaonDirection.RECEIVE) {
            BaonRowUi(
                baon.id, baon.name, baon.direction, summary.leftCentavos, "left",
                listOf("Received ${Peso.format(summary.budgetCentavos)}, spent ${Peso.format(summary.usedCentavos)}", periodText(baon.period, summary)),
                fraction,
            )
        } else {
            BaonRowUi(
                baon.id, baon.name, baon.direction, summary.usedCentavos, "given",
                listOf("of ${Peso.format(summary.budgetCentavos)} planned", periodText(baon.period, summary)),
                fraction,
            )
        }
    }

    /** "Today", or the days the current period covers: "Nov 23 – Nov 29". */
    fun periodText(period: BaonPeriod, summary: BaonSummary): String =
        if (period == BaonPeriod.DAILY) "Today" else "${shortDate.format(summary.periodStart)} – ${shortDate.format(summary.periodEnd)}"

    fun rhythm(baon: BaonEntity): String {
        val every = when (baon.period) {
            BaonPeriod.DAILY -> "every day"
            BaonPeriod.WEEKLY -> "every week"
            BaonPeriod.MONTHLY -> "every month"
        }
        return if (baon.direction == BaonDirection.RECEIVE) "You receive ${Peso.format(baon.amountCentavos)} $every" else "You give ${Peso.format(baon.amountCentavos)} $every"
    }

    /** A baon's own screen. Its numbers are worked out from the same entries it lists. */
    fun detail(baon: BaonEntity, entries: List<RecentTransaction>, today: LocalDate): ItemDetailUi {
        val activity = entries.map { BaonActivityRow(baon.id, it.purpose, it.occurredOn, it.amountCentavos) }
        val row = row(baon, activity, today)
        return ItemDetailUi(
            title = baon.name,
            subtitle = rhythm(baon),
            section = Section.BAON,
            amountCaption = if (baon.direction == BaonDirection.RECEIVE) "Left this period" else "Given this period",
            amountCentavos = row.amountCentavos,
            lines = row.lines + listOfNotNull(if (baon.isActive) null else "Stopped"),
            fraction = row.fraction,
            days = MonthLedgerBuilder.groupByDay(entries),
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BaonViewModel(db: FitakaDatabase, today: StateFlow<LocalDate>) : ViewModel() {
    val ui: StateFlow<BaonUi> = today.flatMapLatest { day ->
        combine(
            db.baonDao().observeAll(),
            db.transactionDao().observeBaonActivity(since = day.minusDays(BaonUiBuilder.LOOK_BACK_DAYS)),
        ) { baons, activity -> BaonUiBuilder.build(baons, activity, day) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BaonUi())
}

@Composable
fun BaonRoute(
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    onReceived: (Long) -> Unit,
    onSpend: (Long) -> Unit,
    onGive: (Long) -> Unit,
    onSearch: () -> Unit,
) {
    val container = LocalAppContainer.current
    val vm = viewModel { BaonViewModel(container.database, container.dayClock.today) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    BaonContent(ui, onAdd, onOpen, onReceived, onSpend, onGive, onSearch)
}

@Composable
fun BaonContent(
    ui: BaonUi,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    onReceived: (Long) -> Unit,
    onSpend: (Long) -> Unit,
    onGive: (Long) -> Unit,
    onSearch: () -> Unit,
) {
    val section = Section.BAON
    FitakaScreen(
        title = section.label,
        subtitle = section.tagline,
        actions = { FitakaIconButton(FitakaIcons.Search, "Search", onSearch) },
    ) {
        if (ui.leftCentavos != null) {
            item(key = "totals") { SectionTotalsCard(section, listOf("Baon left" to ui.leftCentavos)) }
        }
        item(key = "add") {
            FitakaButton("Add a baon", onClick = onAdd, modifier = Modifier.fillMaxWidth(), style = FitakaButtonStyle.Primary, icon = FitakaIcons.Add)
        }
        if (ui.loaded && ui.isEmpty) {
            item(key = "empty") {
                FitakaCard(Modifier.fillMaxWidth()) { EmptyState("No baon set up", "Add an allowance you receive or give, and see what is left of it.", mascot = SleepyAlkansya) }
            }
        }
        for ((title, rows) in listOf("You receive" to ui.receiving, "You give" to ui.giving, "Stopped" to ui.stopped)) {
            if (rows.isEmpty()) continue
            item(key = "title-$title") { SectionHeader(title) }
            items(rows, key = { "baon-${it.id}" }) { baon ->
                SectionItemCard(
                    section, title = baon.name, amount = Peso.format(baon.amountCentavos), amountCaption = baon.amountCaption,
                    lines = baon.lines, fraction = baon.fraction, onClick = { onOpen(baon.id) },
                    // A stopped baon is only looked at, so it carries no buttons.
                    actions = when {
                        title == "Stopped" -> null
                        baon.direction == BaonDirection.RECEIVE -> {
                            {
                                FitakaButton("Received", onClick = { onReceived(baon.id) }, modifier = Modifier.weight(1f))
                                FitakaButton("Spend", onClick = { onSpend(baon.id) }, modifier = Modifier.weight(1f))
                            }
                        }
                        else -> {
                            { FitakaButton("Give", onClick = { onGive(baon.id) }, modifier = Modifier.weight(1f)) }
                        }
                    },
                )
            }
        }
    }
}

// ---------- one baon ----------

sealed interface BaonDetailState {
    data object Gone : BaonDetailState
    data class Shown(val ui: ItemDetailUi, val direction: BaonDirection, val active: Boolean) : BaonDetailState
}

class BaonDetailViewModel(db: FitakaDatabase, baonId: Long, today: StateFlow<LocalDate>) : ViewModel() {
    /** Null while loading; [BaonDetailState.Gone] once the baon no longer exists. */
    val state: StateFlow<BaonDetailState?> = combine(
        db.baonDao().observe(baonId),
        db.transactionDao().observeForBaon(baonId),
        today,
    ) { baon, entries, day ->
        if (baon == null) BaonDetailState.Gone else BaonDetailState.Shown(BaonUiBuilder.detail(baon, entries, day), baon.direction, baon.isActive)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun BaonDetailRoute(
    baonId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onReceived: () -> Unit,
    onSpend: () -> Unit,
    onGive: () -> Unit,
    onOpenEntry: (Long) -> Unit,
) {
    val container = LocalAppContainer.current
    val vm = viewModel { BaonDetailViewModel(container.database, baonId, container.dayClock.today) }
    val state by vm.state.collectAsStateWithLifecycle()
    val today by container.dayClock.today.collectAsStateWithLifecycle()
    when (val shown = state) {
        null -> Box(Modifier.fillMaxSize().background(FitakaTheme.colors.background))
        BaonDetailState.Gone -> LaunchedEffect(Unit) { onBack() }
        is BaonDetailState.Shown -> ItemDetailContent(
            ui = shown.ui,
            today = today,
            actions = if (shown.direction == BaonDirection.RECEIVE) {
                listOf(
                    DetailAction("Baon received", onReceived, primary = true, enabled = shown.active),
                    DetailAction("Spend from this baon", onSpend, enabled = shown.active),
                )
            } else {
                listOf(DetailAction("Give baon", onGive, primary = true, enabled = shown.active))
            },
            editLabel = "Edit baon",
            onEdit = onEdit,
            onOpenEntry = onOpenEntry,
            onBack = onBack,
        )
    }
}
