package com.pitakaview.app.ui.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pitakaview.app.data.db.FitakaDatabase
import com.pitakaview.app.data.db.entity.TransactionEntity
import com.pitakaview.app.data.db.entity.WalletEntity
import com.pitakaview.app.data.prefs.SettingsRepository
import com.pitakaview.app.data.repo.LedgerRepository
import com.pitakaview.app.di.LocalAppContainer
import com.pitakaview.app.domain.EntryFormLogic
import com.pitakaview.app.domain.LoanDirection
import com.pitakaview.app.domain.LoanMath
import com.pitakaview.app.domain.MoveForm
import com.pitakaview.app.domain.MoveFormLogic
import com.pitakaview.app.domain.MoveProblem
import com.pitakaview.app.domain.MoveTarget
import com.pitakaview.app.domain.Peso
import com.pitakaview.app.domain.TxPurpose
import com.pitakaview.app.ui.OneAtATime
import com.pitakaview.app.ui.components.DateChoice
import com.pitakaview.app.ui.components.FitakaButton
import com.pitakaview.app.ui.components.FitakaButtonStyle
import com.pitakaview.app.ui.components.FitakaConfirmDialog
import com.pitakaview.app.ui.components.FitakaScreen
import com.pitakaview.app.ui.components.FitakaTextField
import com.pitakaview.app.ui.components.MoneyInput
import com.pitakaview.app.ui.components.ProblemText
import com.pitakaview.app.ui.components.RevealRequest
import com.pitakaview.app.ui.icons.FitakaIcons
import com.pitakaview.app.ui.theme.FitakaTheme
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The words on the screen for each kind of section entry. */
object MoveText {
    const val DEPOSIT_TAKEN_OUT =
        "This cannot be deleted: some of it has already been taken out of this goal. Lower or delete what was taken out first."

    fun title(purpose: TxPurpose, direction: LoanDirection?, isEdit: Boolean): String = when (purpose) {
        TxPurpose.LOAN_PAYMENT -> when {
            isEdit -> "Edit payment"
            direction == LoanDirection.OWED_TO_ME -> "Payment received"
            else -> "Add a payment"
        }
        TxPurpose.LOAN_START -> "Loan money"
        TxPurpose.SAVINGS_DEPOSIT -> if (isEdit) "Edit savings entry" else "Add to savings"
        TxPurpose.SAVINGS_WITHDRAWAL -> if (isEdit) "Edit savings entry" else "Take out of savings"
        TxPurpose.BAON_RECEIVED -> "Baon received"
        TxPurpose.BAON_GIVEN -> "Baon given"
        else -> "Entry"
    }

    /** What the one wallet on the form means for this kind of entry. */
    fun walletLabel(purpose: TxPurpose, direction: LoanDirection?): String = when (purpose) {
        TxPurpose.LOAN_PAYMENT -> if (direction == LoanDirection.OWED_TO_ME) "Received in" else "Paid from"
        TxPurpose.LOAN_START -> if (direction == LoanDirection.OWED_TO_ME) "Taken from" else "Received in"
        TxPurpose.SAVINGS_DEPOSIT -> "Taken from"
        TxPurpose.SAVINGS_WITHDRAWAL -> "Put into"
        TxPurpose.BAON_RECEIVED -> "Received in"
        else -> "Paid from"
    }
}

/**
 * Adds or changes one entry that belongs to a loan, a savings goal or a baon. A new entry is given its
 * [purpose] and the id it belongs to ([linkId]); an existing one is found by [transactionId] and brings both.
 */
class MoveEditorViewModel(
    private val db: FitakaDatabase,
    private val ledger: LedgerRepository,
    private val settings: SettingsRepository,
    today: LocalDate,
    transactionId: Long?,
    purpose: TxPurpose?,
    linkId: Long?,
    private val now: () -> Instant = Instant::now,
) : ViewModel() {

    val isEdit = transactionId != null
    val amount = TextFieldState()
    val note = TextFieldState()

    var walletId by mutableStateOf<Long?>(null)
        private set
    var date by mutableStateOf(today)
        private set
    // Worked out again as the owner types and picks, once Save has been tried: a mistake put right
    // stops being shown at once (until 2026-11-27 it stayed until the next Save).
    private var tried by mutableStateOf(false)
    val problems: Set<MoveProblem> by derivedStateOf { if (tried) target?.let { MoveFormLogic.problems(MoveForm(amount.text.toString(), walletId, date, note.text.toString()), it.purpose, mostCentavos, leastCentavos) }.orEmpty() else emptySet() }

    /** Goes up at each Save refused for a problem: the screen then brings the first one into view. */
    var refusedSaves by mutableIntStateOf(0)
        private set
    var saveError by mutableStateOf<String?>(null)
        private set
    var loaded by mutableStateOf(false)
        private set
    var done by mutableStateOf(false)
        private set
    private val saving = OneAtATime()

    /** What the entry belongs to; null until loaded. */
    var target by mutableStateOf<MoveTarget?>(null)
        private set

    /** The loan's other side, the goal's name or the baon's name. */
    var targetName by mutableStateOf("")
        private set

    /** One line of help under the wallet, e.g. where a deposit ends up. */
    var info by mutableStateOf<String?>(null)
        private set

    /** The most that can be taken out of a goal: what is saved in it. */
    var mostCentavos by mutableStateOf<Long?>(null)
        private set

    /** The least an old deposit can be lowered to: what has not been taken out of its goal since. */
    var leastCentavos by mutableStateOf<Long?>(null)
        private set

    /** False for an old deposit whose money has since been taken out of its goal. */
    var canDelete by mutableStateOf(true)
        private set

    private var existing: TransactionEntity? = null

    val wallets: StateFlow<List<WalletEntity>> = db.walletDao().observeActiveWithBalance()
        .map { rows -> rows.map { it.wallet } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val tx = transactionId?.let { db.transactionDao().get(it) }
            val kind = tx?.purpose ?: purpose
            if (kind == null || kind !in MoveFormLogic.PURPOSES || (transactionId != null && tx == null)) {
                done = true // nothing this screen can show
                return@launch
            }
            existing = tx
            val active = db.walletDao().observeActiveWithBalance().first().map { it.wallet }
            var suggested: Long? = null
            var usualWallet: Long? = null
            when (kind) {
                TxPurpose.LOAN_START, TxPurpose.LOAN_PAYMENT -> {
                    val row = (tx?.loanId ?: linkId)?.let { db.loanDao().observeWithPaid(it).first() }
                    if (row == null) {
                        done = true
                        return@launch
                    }
                    target = MoveTarget(kind, loanId = row.loan.id, loanDirection = row.loan.direction)
                    targetName = row.loan.counterparty
                    // A new payment starts at what is due next, or at all that is left.
                    suggested = LoanMath.nextDue(row.loan, row.paidCentavos)?.amountCentavos
                        ?: LoanMath.remaining(row.loan, row.paidCentavos).takeIf { it > 0 }
                }
                TxPurpose.SAVINGS_DEPOSIT, TxPurpose.SAVINGS_WITHDRAWAL -> {
                    val goal = (tx?.goalId ?: linkId)?.let { db.savingsGoalDao().observeProgressOf(it).first() }
                    if (goal == null) {
                        done = true
                        return@launch
                    }
                    target = MoveTarget(kind, goalId = goal.goalId, goalWalletId = goal.walletId)
                    targetName = goal.name
                    val kept = goal.walletId?.let { id -> db.walletDao().get(id)?.name }
                    // A goal below zero (made so before the ledger refused it) counts as nothing saved.
                    val saved = maxOf(goal.savedCentavos, 0)
                    if (kind == TxPurpose.SAVINGS_DEPOSIT) {
                        info = if (kept != null) "It moves into $kept, where this goal is kept." else "It stays in that wallet, set aside for this goal."
                        if (tx != null) {
                            // Lowering or removing an old deposit gives back money that may have been taken out since.
                            leastCentavos = (tx.amountCentavos - saved).takeIf { it > 0 }
                            canDelete = saved >= tx.amountCentavos
                        }
                    } else {
                        // Changing an old withdrawal may use the money it already took out.
                        mostCentavos = saved + (tx?.amountCentavos ?: 0)
                        info = "Saved in this goal: ${Peso.format(goal.savedCentavos)}"
                    }
                }
                else -> {
                    val baon = (tx?.baonId ?: linkId)?.let { db.baonDao().observe(it).first() }
                    if (baon == null) {
                        done = true
                        return@launch
                    }
                    target = MoveTarget(kind, baonId = baon.id)
                    targetName = baon.name
                    suggested = baon.amountCentavos
                    usualWallet = baon.walletId
                }
            }
            if (tx != null) {
                val form = MoveFormLogic.fromTransaction(tx)
                amount.setTextAndPlaceCursorAtEnd(form.amountText)
                note.setTextAndPlaceCursorAtEnd(form.note)
                walletId = form.walletId
                date = form.date
            } else {
                suggested?.let { amount.setTextAndPlaceCursorAtEnd(EntryFormLogic.plainAmount(it)) }
                val ids = active.map { it.id }
                walletId = usualWallet?.takeIf { it in ids } ?: settings.lastWalletId.first()?.takeIf { it in ids } ?: ids.firstOrNull()
            }
            loaded = true
        }
    }

    fun selectWallet(id: Long?) {
        walletId = id
    }

    fun selectDate(value: LocalDate) {
        date = value
    }

    fun save() {
        val to = target ?: return
        val form = MoveForm(amount.text.toString(), walletId, date, note.text.toString())
        tried = true
        if (problems.isNotEmpty()) {
            refusedSaves++
            return
        }
        saving.launch(viewModelScope) {
            val tx = MoveFormLogic.toTransaction(form, to, existing, now())
            try {
                if (existing == null) ledger.add(tx) else ledger.update(tx)
            } catch (e: IllegalArgumentException) {
                // The ledger refused the row. Say so instead of closing as if it had been saved.
                saveError = "This entry could not be saved: ${e.message}"
                return@launch false
            }
            form.walletId?.let { settings.setLastWalletId(it) }
            done = true
            true
        }
    }

    fun delete() {
        val tx = existing ?: return
        if (!canDelete) return
        viewModelScope.launch {
            try {
                ledger.delete(tx)
            } catch (e: IllegalArgumentException) {
                // The ledger refused (the goal changed since this screen opened). Say so; nothing was removed.
                saveError = "This entry could not be deleted: ${e.message}"
                return@launch
            }
            done = true
        }
    }
}

/** Everything the section-entry screen draws, apart from its two text fields. */
data class MoveEditorUi(
    val title: String,
    val subtitle: String,
    val walletLabel: String,
    val wallets: List<WalletEntity>,
    val walletId: Long?,
    val date: LocalDate,
    val today: LocalDate,
    val isEdit: Boolean,
    val info: String? = null,
    val mostCentavos: Long? = null,
    val leastCentavos: Long? = null,
    val canDelete: Boolean = true,
    val problems: Set<MoveProblem> = emptySet(),
    val saveError: String? = null,
    /** Goes up at each Save refused for a problem (see FitakaScreen's reveal). */
    val refusedSaves: Int = 0,
)

@Composable
fun MoveEditorRoute(transactionId: Long?, purpose: TxPurpose?, linkId: Long?, onDone: () -> Unit) {
    val container = LocalAppContainer.current
    val today by container.dayClock.today.collectAsStateWithLifecycle()
    val vm = viewModel {
        MoveEditorViewModel(container.database, container.ledger, container.settings, container.dayClock.today.value, transactionId, purpose, linkId)
    }
    val wallets by vm.wallets.collectAsStateWithLifecycle()
    LaunchedEffect(vm.done) { if (vm.done) onDone() }
    val target = vm.target
    if (!vm.loaded || target == null) {
        Box(Modifier.fillMaxSize().background(FitakaTheme.colors.background))
        return
    }
    MoveEditorContent(
        ui = MoveEditorUi(
            refusedSaves = vm.refusedSaves,
            title = MoveText.title(target.purpose, target.loanDirection, vm.isEdit),
            subtitle = vm.targetName,
            walletLabel = MoveText.walletLabel(target.purpose, target.loanDirection),
            wallets = wallets, walletId = vm.walletId, date = vm.date, today = today, isEdit = vm.isEdit,
            info = vm.info, mostCentavos = vm.mostCentavos, leastCentavos = vm.leastCentavos, canDelete = vm.canDelete,
            problems = vm.problems, saveError = vm.saveError,
        ),
        amount = vm.amount, note = vm.note,
        onWallet = vm::selectWallet, onDate = vm::selectDate, onSave = vm::save, onDelete = vm::delete, onBack = onDone,
    )
}

@Composable
fun MoveEditorContent(
    ui: MoveEditorUi,
    amount: TextFieldState,
    note: TextFieldState,
    onWallet: (Long?) -> Unit,
    onDate: (LocalDate) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    val c = FitakaTheme.colors
    val type = MaterialTheme.typography
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val amountFocus = remember { FocusRequester() }

    // A question about something that cannot be undone is asked over the whole screen, not in the footer.
    if (confirmDelete) {
        FitakaConfirmDialog(
            title = "Delete this entry?",
            lines = listOf("It is removed from your totals. This cannot be undone."),
            confirmLabel = "Yes, delete",
            dismissLabel = "Keep",
            onConfirm = onDelete,
            onDismiss = { confirmDelete = false },
        )
    }

    FitakaScreen(
        title = ui.title,
        subtitle = ui.subtitle,
        reveal = RevealRequest.firstOf(
            ui.refusedSaves,
            "amount".takeIf { ui.problems.any { it != MoveProblem.WALLET } },
            "wallet".takeIf { MoveProblem.WALLET in ui.problems },
        ),
        onBack = onBack,
        pinnedAction = {
            Column {
                if (ui.saveError != null) ProblemText(ui.saveError)
                FitakaButton("Save", onClick = onSave, modifier = Modifier.fillMaxWidth(), style = FitakaButtonStyle.Primary, icon = FitakaIcons.Check)
            }
        },
    ) {
        item(key = "amount") {
            val tooMuch = MoveProblem.MORE_THAN_SAVED in ui.problems
            val tooLittle = MoveProblem.LESS_THAN_TAKEN_OUT in ui.problems
            FitakaTextField(
                state = amount, label = "Amount", modifier = Modifier.focusRequester(amountFocus), placeholder = "0.00", prefix = Peso.SIGN,
                isError = MoveProblem.AMOUNT in ui.problems || tooMuch || tooLittle,
                supportingText = when {
                    MoveProblem.AMOUNT in ui.problems -> "Enter an amount above ₱0.00"
                    tooMuch -> "Only ${Peso.format(ui.mostCentavos ?: 0)} is saved in this goal"
                    tooLittle -> "At least ${Peso.format(ui.leastCentavos ?: 0)}: the rest has already been taken out of this goal"
                    else -> null
                },
                inputTransformation = MoneyInput,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            // A new entry starts with the keyboard on the amount.
            if (!ui.isEdit) LaunchedEffect(Unit) { amountFocus.requestFocus() }
        }
        item(key = "wallet") {
            WalletChoice(
                ui.walletLabel, ui.wallets, ui.walletId, onWallet,
                problem = if (MoveProblem.WALLET in ui.problems) "Pick a wallet" else null,
                hint = ui.info,
            )
        }
        item(key = "date") { DateChoice(ui.date, ui.today, onDate) }
        item(key = "note") { FitakaTextField(state = note, label = "Note", placeholder = "Optional") }
        if (ui.isEdit) {
            item(key = "delete") {
                if (ui.canDelete) {
                    FitakaButton("Delete entry", onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth())
                } else {
                    Text(MoveText.DEPOSIT_TAKEN_OUT, style = type.bodySmall, color = c.inkMuted)
                }
            }
        }
    }
}
