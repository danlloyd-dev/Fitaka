package com.pitakaview.app.ui.utang

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.pitakaview.app.data.db.PitakaDatabase
import com.pitakaview.app.data.db.entity.LoanEntity
import com.pitakaview.app.data.db.entity.WalletEntity
import com.pitakaview.app.data.repo.LedgerRepository
import com.pitakaview.app.di.LocalAppContainer
import com.pitakaview.app.domain.LoanDirection
import com.pitakaview.app.domain.LoanForm
import com.pitakaview.app.domain.LoanFormLogic
import com.pitakaview.app.domain.LoanMath
import com.pitakaview.app.domain.LoanPlan
import com.pitakaview.app.domain.LoanProblem
import com.pitakaview.app.domain.Peso
import com.pitakaview.app.ui.components.DateChoice
import com.pitakaview.app.ui.components.MoneyInput
import com.pitakaview.app.ui.components.OptionalDateChoice
import com.pitakaview.app.ui.components.PitakaButton
import com.pitakaview.app.ui.components.PitakaButtonStyle
import com.pitakaview.app.ui.components.PitakaChip
import com.pitakaview.app.ui.components.PitakaScreen
import com.pitakaview.app.ui.components.PitakaTextField
import com.pitakaview.app.ui.components.ProblemText
import com.pitakaview.app.ui.components.PitakaToggleRow
import com.pitakaview.app.ui.components.SectionHeader
import com.pitakaview.app.ui.settings.REMINDERS_OFF_HINT
import com.pitakaview.app.ui.icons.PitakaIcons
import com.pitakaview.app.ui.sections.WalletChoice
import com.pitakaview.app.ui.theme.PitakaTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

val LoanDirection.label: String
    get() = if (this == LoanDirection.I_OWE) "I borrowed" else "I lent"

val LoanPlan.label: String
    get() = when (this) {
        LoanPlan.NO_DATE -> "No due date"
        LoanPlan.ONE_DATE -> "One due date"
        LoanPlan.MONTHLY -> "Every month"
    }

/** Add or change a loan. Checks what was typed with [LoanFormLogic]. */
class LoanEditorViewModel(
    private val db: PitakaDatabase,
    private val ledger: LedgerRepository,
    today: LocalDate,
    loanId: Long?,
    private val now: () -> Instant = Instant::now,
) : ViewModel() {
    val isEdit = loanId != null
    val who = TextFieldState()
    val amount = TextFieldState()
    val total = TextFieldState()
    val installment = TextFieldState()
    val note = TextFieldState()

    var direction by mutableStateOf(LoanDirection.I_OWE)
        private set
    var startDate by mutableStateOf(today)
        private set
    var plan by mutableStateOf(LoanPlan.NO_DATE)
        private set
    var dueDate by mutableStateOf<LocalDate?>(null)
        private set
    var remind by mutableStateOf(true)
        private set

    /** Only for a new loan: the wallet the money moved through, or null with [moneyChosen] for "no wallet". */
    var walletId by mutableStateOf<Long?>(null)
        private set
    var moneyChosen by mutableStateOf(false)
        private set

    /** Shown only after a Save attempt. */
    var problems by mutableStateOf(emptySet<LoanProblem>())
        private set
    var moneyProblem by mutableStateOf(false)
        private set
    var loaded by mutableStateOf(false)
        private set
    var done by mutableStateOf(false)
        private set

    /** The loan being changed, with whether it is settled and whether anything is logged for it. */
    var existing by mutableStateOf<LoanEntity?>(null)
        private set
    var paidInFull by mutableStateOf(false)
        private set
    var hasEntries by mutableStateOf(false)
        private set

    val wallets: StateFlow<List<WalletEntity>> = db.walletDao().observeActiveWithBalance()
        .map { rows -> rows.map { it.wallet } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val row = loanId?.let { db.loanDao().observeWithPaid(it).first() }
            if (row != null) {
                existing = row.loan
                paidInFull = LoanMath.remaining(row.loan, row.paidCentavos) <= 0
                hasEntries = db.transactionDao().countForLoan(row.loan.id) > 0
                val form = LoanFormLogic.fromEntity(row.loan)
                who.setTextAndPlaceCursorAtEnd(form.who)
                amount.setTextAndPlaceCursorAtEnd(form.amountText)
                total.setTextAndPlaceCursorAtEnd(form.totalText)
                installment.setTextAndPlaceCursorAtEnd(form.installmentText)
                note.setTextAndPlaceCursorAtEnd(form.note)
                direction = form.direction
                startDate = form.startDate
                plan = form.plan
                dueDate = form.dueDate
                remind = form.remind
            }
            loaded = true
        }
    }

    private fun form() = LoanForm(
        direction = direction, who = who.text.toString(), amountText = amount.text.toString(), totalText = total.text.toString(),
        startDate = startDate, plan = plan, dueDate = dueDate, installmentText = installment.text.toString(),
        note = note.text.toString(), walletId = walletId, remind = remind,
    )

    fun selectDirection(value: LoanDirection) {
        direction = value
    }

    fun selectStartDate(value: LocalDate) {
        startDate = value
    }

    fun selectPlan(value: LoanPlan) {
        plan = value
    }

    fun selectDueDate(value: LocalDate?) {
        dueDate = value
    }

    fun selectRemind(value: Boolean) {
        remind = value
    }

    /** [id] null means "the money did not go through a wallet": a loan from before the app. */
    fun selectMoney(id: Long?) {
        walletId = id
        moneyChosen = true
        moneyProblem = false
    }

    fun save() {
        val form = form()
        problems = LoanFormLogic.problems(form)
        // A new loan must say where the money went, so a wallet is never left wrong by a default.
        moneyProblem = !isEdit && !moneyChosen
        if (problems.isNotEmpty() || moneyProblem) return
        viewModelScope.launch {
            val current = existing
            val loan = LoanFormLogic.toEntity(form, current, now())
            if (current == null) {
                ledger.addLoan(loan) { id -> LoanFormLogic.startRow(form, id, now()) }
            } else {
                db.loanDao().update(loan)
            }
            done = true
        }
    }

    /** Marks the loan settled although money is left on it (forgiven, written off), or undoes that. */
    fun toggleSettled() {
        val loan = existing ?: return
        viewModelScope.launch {
            db.loanDao().update(loan.copy(closedAt = if (loan.closedAt == null) now() else null))
            done = true
        }
    }

    /** Only offered when nothing is logged for the loan; the database refuses otherwise. */
    fun delete() {
        val loan = existing ?: return
        viewModelScope.launch {
            db.loanDao().delete(loan)
            done = true
        }
    }
}

/** Everything the loan screen draws, apart from its text fields. */
data class LoanEditorUi(
    val isEdit: Boolean,
    val direction: LoanDirection,
    val startDate: LocalDate,
    val plan: LoanPlan,
    val dueDate: LocalDate?,
    val wallets: List<WalletEntity>,
    val walletId: Long?,
    val moneyChosen: Boolean,
    val today: LocalDate,
    val problems: Set<LoanProblem> = emptySet(),
    val moneyProblem: Boolean = false,
    /** For an existing loan: marked settled by the owner, paid in full, and whether it can be deleted. */
    val markedSettled: Boolean = false,
    val paidInFull: Boolean = false,
    val canDelete: Boolean = false,
    val remind: Boolean = true,
    /** Whether reminders as a whole are switched on in Settings. */
    val remindersOn: Boolean = true,
)

@Composable
fun LoanEditorRoute(loanId: Long?, onDone: () -> Unit) {
    val container = LocalAppContainer.current
    val today by container.dayClock.today.collectAsStateWithLifecycle()
    val vm = viewModel { LoanEditorViewModel(container.database, container.ledger, container.dayClock.today.value, loanId) }
    val wallets by vm.wallets.collectAsStateWithLifecycle()
    val remindersOn by container.settings.remindersOn.collectAsStateWithLifecycle(initialValue = true)
    LaunchedEffect(vm.done) { if (vm.done) onDone() }
    if (!vm.loaded) {
        Box(Modifier.fillMaxSize().background(PitakaTheme.colors.background))
        return
    }
    LoanEditorContent(
        ui = LoanEditorUi(
            isEdit = vm.isEdit, direction = vm.direction, startDate = vm.startDate, plan = vm.plan, dueDate = vm.dueDate,
            wallets = wallets, walletId = vm.walletId, moneyChosen = vm.moneyChosen, today = today,
            problems = vm.problems, moneyProblem = vm.moneyProblem,
            markedSettled = vm.existing?.closedAt != null, paidInFull = vm.paidInFull, canDelete = vm.isEdit && !vm.hasEntries,
            remind = vm.remind, remindersOn = remindersOn,
        ),
        who = vm.who, amount = vm.amount, total = vm.total, installment = vm.installment, note = vm.note,
        onDirection = vm::selectDirection, onStartDate = vm::selectStartDate, onPlan = vm::selectPlan, onDueDate = vm::selectDueDate,
        onMoney = vm::selectMoney, onSave = vm::save, onToggleSettled = vm::toggleSettled, onDelete = vm::delete, onBack = onDone,
        onRemind = vm::selectRemind,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoanEditorContent(
    ui: LoanEditorUi,
    who: TextFieldState,
    amount: TextFieldState,
    total: TextFieldState,
    installment: TextFieldState,
    note: TextFieldState,
    onDirection: (LoanDirection) -> Unit,
    onStartDate: (LocalDate) -> Unit,
    onPlan: (LoanPlan) -> Unit,
    onDueDate: (LocalDate?) -> Unit,
    onMoney: (Long?) -> Unit,
    onSave: () -> Unit,
    onToggleSettled: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    onRemind: (Boolean) -> Unit = {},
) {
    val c = PitakaTheme.colors
    val type = MaterialTheme.typography
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val chips = Arrangement.spacedBy(8.dp)
    val borrowed = ui.direction == LoanDirection.I_OWE
    val money = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    PitakaScreen(
        title = if (ui.isEdit) "Edit loan" else "Add a loan",
        onBack = onBack,
        pinnedAction = {
            if (confirmDelete) {
                // In the footer, so the question is always on screen.
                Column {
                    Text("Delete this loan?", style = type.titleSmall, color = c.ink)
                    Text("Nothing is logged for it, so no totals change. This cannot be undone.", style = type.bodySmall, color = c.ink)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PitakaButton("Yes, delete", onClick = onDelete, modifier = Modifier.weight(1f))
                        PitakaButton("Keep", onClick = { confirmDelete = false }, modifier = Modifier.weight(1f))
                    }
                }
            } else {
                PitakaButton("Save", onClick = onSave, modifier = Modifier.fillMaxWidth(), style = PitakaButtonStyle.Primary, icon = PitakaIcons.Check)
            }
        },
    ) {
        // Which way the money went cannot change later: the payments already logged depend on it.
        if (!ui.isEdit) {
            item(key = "direction") {
                FlowRow(horizontalArrangement = chips, verticalArrangement = chips) {
                    LoanDirection.entries.forEach { PitakaChip(it.label, selected = it == ui.direction, onClick = { onDirection(it) }) }
                }
            }
        }
        item(key = "who") {
            PitakaTextField(
                state = who, label = if (borrowed) "From whom" else "To whom", placeholder = "e.g. Tita Baby",
                isError = LoanProblem.WHO in ui.problems,
                supportingText = if (LoanProblem.WHO in ui.problems) "Say who the loan is with" else null,
            )
        }
        item(key = "amount") {
            PitakaTextField(
                state = amount, label = if (borrowed) "How much you borrowed" else "How much you lent", placeholder = "0.00", prefix = Peso.SIGN,
                isError = LoanProblem.AMOUNT in ui.problems,
                supportingText = if (LoanProblem.AMOUNT in ui.problems) "Enter an amount above ₱0.00" else null,
                inputTransformation = MoneyInput, keyboardOptions = money,
            )
        }
        item(key = "total") {
            PitakaTextField(
                state = total, label = "Total to pay back, with interest", placeholder = "Same as the amount", prefix = Peso.SIGN,
                isError = LoanProblem.TOTAL in ui.problems,
                supportingText = if (LoanProblem.TOTAL in ui.problems) "The total cannot be less than the amount" else "Leave empty if there is no interest.",
                inputTransformation = MoneyInput, keyboardOptions = money,
            )
        }
        item(key = "start") { DateChoice(ui.startDate, ui.today, onStartDate, title = "Date of the loan") }

        if (!ui.isEdit) {
            item(key = "money") {
                WalletChoice(
                    title = if (borrowed) "Where the money went" else "Where the money came from",
                    wallets = ui.wallets, selectedId = ui.walletId, onSelect = onMoney,
                    noneLabel = "No wallet", noneSelected = ui.moneyChosen,
                    problem = if (ui.moneyProblem) "Pick a wallet, or No wallet" else null,
                    hint = "Pick No wallet for a loan from before you used this app.",
                )
            }
        }

        item(key = "plan") {
            Column {
                SectionHeader("How it is paid back")
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = chips, verticalArrangement = chips) {
                    LoanPlan.entries.forEach { PitakaChip(it.label, selected = it == ui.plan, onClick = { onPlan(it) }) }
                }
            }
        }
        if (ui.plan == LoanPlan.MONTHLY) {
            item(key = "installment") {
                PitakaTextField(
                    state = installment, label = "How much each month", placeholder = "0.00", prefix = Peso.SIGN,
                    isError = LoanProblem.INSTALLMENT in ui.problems,
                    supportingText = if (LoanProblem.INSTALLMENT in ui.problems) "Enter the monthly amount" else null,
                    inputTransformation = MoneyInput, keyboardOptions = money,
                )
            }
        }
        if (ui.plan != LoanPlan.NO_DATE) {
            item(key = "due") {
                Column {
                    OptionalDateChoice(
                        title = if (ui.plan == LoanPlan.MONTHLY) "First payment on" else "Due on",
                        date = ui.dueDate, today = ui.today, onDate = onDueDate, noneLabel = "Not set",
                    )
                    if (LoanProblem.DUE_DATE in ui.problems) ProblemText("Pick the date")
                }
            }
            // Only a loan with a date has anything to be reminded of.
            item(key = "remind") {
                PitakaToggleRow(
                    title = "Remind me when it is due",
                    checked = ui.remind,
                    onCheckedChange = onRemind,
                    subtitle = if (ui.remindersOn) "The day before and on the day." else REMINDERS_OFF_HINT,
                )
            }
        }
        item(key = "note") { PitakaTextField(state = note, label = "Note", placeholder = "Optional") }

        if (ui.isEdit && !confirmDelete) {
            // Paid in full needs no marking; the button is for a loan forgiven or written off.
            if (!ui.paidInFull) {
                item(key = "settle") {
                    PitakaButton(
                        if (ui.markedSettled) "Open this loan again" else "Mark as settled",
                        onClick = onToggleSettled, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (ui.canDelete) {
                item(key = "delete") {
                    PitakaButton("Delete loan", onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
