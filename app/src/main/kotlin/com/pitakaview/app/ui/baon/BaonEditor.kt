package com.pitakaview.app.ui.baon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pitakaview.app.data.db.FitakaDatabase
import com.pitakaview.app.data.db.entity.BaonEntity
import com.pitakaview.app.data.db.entity.WalletEntity
import com.pitakaview.app.di.LocalAppContainer
import com.pitakaview.app.domain.BaonDirection
import com.pitakaview.app.domain.BaonForm
import com.pitakaview.app.domain.BaonFormLogic
import com.pitakaview.app.domain.BaonPeriod
import com.pitakaview.app.domain.BaonProblem
import com.pitakaview.app.domain.Peso
import com.pitakaview.app.ui.OneAtATime
import com.pitakaview.app.ui.components.DateChoice
import com.pitakaview.app.ui.components.FitakaButton
import com.pitakaview.app.ui.components.FitakaButtonStyle
import com.pitakaview.app.ui.components.FitakaChip
import com.pitakaview.app.ui.components.FitakaConfirmDialog
import com.pitakaview.app.ui.components.FitakaScreen
import com.pitakaview.app.ui.components.FitakaTextField
import com.pitakaview.app.ui.components.MoneyInput
import com.pitakaview.app.ui.components.RevealRequest
import com.pitakaview.app.ui.components.SectionHeader
import com.pitakaview.app.ui.icons.FitakaIcons
import com.pitakaview.app.ui.sections.WalletChoice
import com.pitakaview.app.ui.theme.FitakaTheme
import java.time.Instant
import java.time.LocalDate
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

val BaonDirection.label: String
    get() = if (this == BaonDirection.RECEIVE) "I receive it" else "I give it"

val BaonPeriod.label: String
    get() = when (this) {
        BaonPeriod.DAILY -> "Every day"
        BaonPeriod.WEEKLY -> "Every week"
        BaonPeriod.MONTHLY -> "Every month"
    }

/** When a baon's amount starts again, in words. */
fun restartText(period: BaonPeriod, startDate: LocalDate): String = when (period) {
    BaonPeriod.DAILY -> "It starts again every day."
    BaonPeriod.WEEKLY -> "It starts again every ${startDate.dayOfWeek.getDisplayName(DateTextStyle.FULL, Locale.ENGLISH)}."
    BaonPeriod.MONTHLY -> "It starts again on day ${startDate.dayOfMonth} of each month."
}

/** Add or change a baon. Checks what was typed with [BaonFormLogic]. */
class BaonEditorViewModel(
    private val db: FitakaDatabase,
    today: LocalDate,
    baonId: Long?,
    private val now: () -> Instant = Instant::now,
) : ViewModel() {
    val isEdit = baonId != null
    val name = TextFieldState()
    val amount = TextFieldState()

    var direction by mutableStateOf(BaonDirection.RECEIVE)
        private set
    var period by mutableStateOf(BaonPeriod.WEEKLY)
        private set
    var startDate by mutableStateOf(today)
        private set
    var walletId by mutableStateOf<Long?>(null)
        private set

    /** Shown only after a Save attempt. */
    // Worked out again as the owner types and picks, once Save has been tried: a mistake put right
    // stops being shown at once (until 2026-11-27 it stayed until the next Save).
    private var tried by mutableStateOf(false)
    val problems: Set<BaonProblem> by derivedStateOf { if (tried) BaonFormLogic.problems(BaonForm(direction, name.text.toString(), amount.text.toString(), period, startDate, walletId)) else emptySet() }

    /** Goes up at each Save refused for a problem: the screen then brings the first one into view. */
    var refusedSaves by mutableIntStateOf(0)
        private set
    var loaded by mutableStateOf(false)
        private set
    var done by mutableStateOf(false)
        private set
    private val saving = OneAtATime()
    var existing by mutableStateOf<BaonEntity?>(null)
        private set
    var hasEntries by mutableStateOf(false)
        private set

    val wallets: StateFlow<List<WalletEntity>> = db.walletDao().observeActiveWithBalance()
        .map { rows -> rows.map { it.wallet } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val baon = baonId?.let { db.baonDao().observe(it).first() }
            if (baon != null) {
                existing = baon
                hasEntries = db.transactionDao().countForBaon(baon.id) > 0
                val form = BaonFormLogic.fromEntity(baon)
                name.setTextAndPlaceCursorAtEnd(form.name)
                amount.setTextAndPlaceCursorAtEnd(form.amountText)
                direction = form.direction
                period = form.period
                startDate = form.startDate
                walletId = form.walletId
            }
            loaded = true
        }
    }

    fun selectDirection(value: BaonDirection) {
        direction = value
    }

    fun selectPeriod(value: BaonPeriod) {
        period = value
    }

    fun selectStartDate(value: LocalDate) {
        startDate = value
    }

    fun selectWallet(id: Long?) {
        walletId = id
    }

    fun save() {
        val form = BaonForm(direction, name.text.toString(), amount.text.toString(), period, startDate, walletId)
        tried = true
        if (problems.isNotEmpty()) {
            refusedSaves++
            return
        }
        saving.launch(viewModelScope) {
            val baon = BaonFormLogic.toEntity(form, existing, now())
            if (existing == null) db.baonDao().insert(baon) else db.baonDao().update(baon)
            done = true
            true
        }
    }

    /** A stopped baon keeps its entries but leaves Home and the lists of baon in use. */
    fun toggleStopped() {
        val baon = existing ?: return
        viewModelScope.launch {
            db.baonDao().update(baon.copy(isActive = !baon.isActive))
            done = true
        }
    }

    /** Only offered when nothing is logged for the baon; the database refuses otherwise. */
    fun delete() {
        val baon = existing ?: return
        viewModelScope.launch {
            db.baonDao().delete(baon)
            done = true
        }
    }
}

/** Everything the baon screen draws, apart from its two text fields. */
data class BaonEditorUi(
    val isEdit: Boolean,
    val direction: BaonDirection,
    val period: BaonPeriod,
    val startDate: LocalDate,
    val wallets: List<WalletEntity>,
    val walletId: Long?,
    val today: LocalDate,
    val problems: Set<BaonProblem> = emptySet(),
    val stopped: Boolean = false,
    val canDelete: Boolean = false,
    /** Goes up at each Save refused for a problem (see FitakaScreen's reveal). */
    val refusedSaves: Int = 0,
)

@Composable
fun BaonEditorRoute(baonId: Long?, onDone: () -> Unit) {
    val container = LocalAppContainer.current
    val today by container.dayClock.today.collectAsStateWithLifecycle()
    val vm = viewModel { BaonEditorViewModel(container.database, container.dayClock.today.value, baonId) }
    val wallets by vm.wallets.collectAsStateWithLifecycle()
    LaunchedEffect(vm.done) { if (vm.done) onDone() }
    if (!vm.loaded) {
        Box(Modifier.fillMaxSize().background(FitakaTheme.colors.background))
        return
    }
    BaonEditorContent(
        ui = BaonEditorUi(
            refusedSaves = vm.refusedSaves,
            isEdit = vm.isEdit, direction = vm.direction, period = vm.period, startDate = vm.startDate,
            wallets = wallets, walletId = vm.walletId, today = today, problems = vm.problems,
            stopped = vm.existing?.isActive == false, canDelete = vm.isEdit && !vm.hasEntries,
        ),
        name = vm.name, amount = vm.amount,
        onDirection = vm::selectDirection, onPeriod = vm::selectPeriod, onStartDate = vm::selectStartDate, onWallet = vm::selectWallet,
        onSave = vm::save, onToggleStopped = vm::toggleStopped, onDelete = vm::delete, onBack = onDone,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BaonEditorContent(
    ui: BaonEditorUi,
    name: TextFieldState,
    amount: TextFieldState,
    onDirection: (BaonDirection) -> Unit,
    onPeriod: (BaonPeriod) -> Unit,
    onStartDate: (LocalDate) -> Unit,
    onWallet: (Long?) -> Unit,
    onSave: () -> Unit,
    onToggleStopped: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    val c = FitakaTheme.colors
    val type = MaterialTheme.typography
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val chips = Arrangement.spacedBy(8.dp)
    val receive = ui.direction == BaonDirection.RECEIVE

    // A question about something that cannot be undone is asked over the whole screen, not in the footer.
    if (confirmDelete) {
        FitakaConfirmDialog(
            title = "Delete this baon?",
            lines = listOf("Nothing is logged for it, so no totals change. This cannot be undone."),
            confirmLabel = "Yes, delete",
            dismissLabel = "Keep",
            onConfirm = onDelete,
            onDismiss = { confirmDelete = false },
        )
    }

    FitakaScreen(
        title = if (ui.isEdit) "Edit baon" else "Add a baon",
        reveal = RevealRequest.firstOf(
            ui.refusedSaves,
            "name".takeIf { BaonProblem.NAME in ui.problems },
            "amount".takeIf { BaonProblem.AMOUNT in ui.problems },
        ),
        onBack = onBack,
        pinnedAction = {
            FitakaButton("Save", onClick = onSave, modifier = Modifier.fillMaxWidth(), style = FitakaButtonStyle.Primary, icon = FitakaIcons.Check)
        },
    ) {
        // Received or given cannot change later: the entries already logged depend on it.
        if (!ui.isEdit) {
            item(key = "direction") {
                FlowRow(horizontalArrangement = chips, verticalArrangement = chips) {
                    BaonDirection.entries.forEach { FitakaChip(it.label, selected = it == ui.direction, onClick = { onDirection(it) }) }
                }
            }
        }
        item(key = "name") {
            FitakaTextField(
                state = name, label = if (receive) "Name" else "Who you give it to", placeholder = if (receive) "e.g. My baon" else "e.g. Bunso",
                isError = BaonProblem.NAME in ui.problems,
                supportingText = if (BaonProblem.NAME in ui.problems) "Give the baon a name" else null,
            )
        }
        item(key = "period") {
            Column {
                SectionHeader("How often")
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = chips, verticalArrangement = chips) {
                    BaonPeriod.entries.forEach { FitakaChip(it.label, selected = it == ui.period, onClick = { onPeriod(it) }) }
                }
            }
        }
        item(key = "amount") {
            FitakaTextField(
                state = amount, label = "How much each time", placeholder = "0.00", prefix = Peso.SIGN,
                isError = BaonProblem.AMOUNT in ui.problems,
                supportingText = if (BaonProblem.AMOUNT in ui.problems) "Enter an amount above ₱0.00" else null,
                inputTransformation = MoneyInput,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }
        item(key = "start") {
            Column {
                DateChoice(ui.startDate, ui.today, onStartDate, title = "Starts on")
                Text(
                    restartText(ui.period, ui.startDate),
                    style = type.bodySmall, color = c.inkMuted, modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                )
            }
        }
        item(key = "wallet") {
            WalletChoice(
                title = "Usual wallet", wallets = ui.wallets, selectedId = ui.walletId, onSelect = onWallet,
                noneLabel = "No usual wallet", noneSelected = true,
                hint = "Offered first when you log this baon.",
            )
        }
        if (ui.isEdit) {
            item(key = "stop") {
                Column {
                    FitakaButton(if (ui.stopped) "Start this baon again" else "Stop this baon", onClick = onToggleStopped, modifier = Modifier.fillMaxWidth())
                    Text(
                        "A stopped baon keeps its entries but is left out of Home.",
                        style = type.bodySmall, color = c.inkMuted, modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                    )
                }
            }
            if (ui.canDelete) {
                item(key = "delete") {
                    FitakaButton("Delete baon", onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
