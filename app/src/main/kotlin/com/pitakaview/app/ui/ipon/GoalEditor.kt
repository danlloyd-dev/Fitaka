package com.pitakaview.app.ui.ipon

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
import com.pitakaview.app.data.db.FitakaDatabase
import com.pitakaview.app.data.db.entity.SavingsGoalEntity
import com.pitakaview.app.data.db.entity.WalletEntity
import com.pitakaview.app.di.LocalAppContainer
import com.pitakaview.app.domain.GoalForm
import com.pitakaview.app.domain.GoalFormLogic
import com.pitakaview.app.domain.GoalProblem
import com.pitakaview.app.domain.Peso
import com.pitakaview.app.ui.OneAtATime
import com.pitakaview.app.ui.components.FitakaButton
import com.pitakaview.app.ui.components.FitakaButtonStyle
import com.pitakaview.app.ui.components.FitakaConfirmDialog
import com.pitakaview.app.ui.components.FitakaScreen
import com.pitakaview.app.ui.components.FitakaTextField
import com.pitakaview.app.ui.components.MoneyInput
import com.pitakaview.app.ui.components.OptionalDateChoice
import com.pitakaview.app.ui.components.SectionHeader
import com.pitakaview.app.ui.icons.FitakaIcons
import com.pitakaview.app.ui.sections.WalletChoice
import com.pitakaview.app.ui.theme.FitakaTheme
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Add or change a savings goal. Checks what was typed with [GoalFormLogic]. */
class GoalEditorViewModel(
    private val db: FitakaDatabase,
    goalId: Long?,
    private val now: () -> Instant = Instant::now,
) : ViewModel() {
    val isEdit = goalId != null
    val name = TextFieldState()
    val target = TextFieldState()

    var targetDate by mutableStateOf<LocalDate?>(null)
        private set
    var walletId by mutableStateOf<Long?>(null)
        private set

    /** Shown only after a Save attempt. */
    var problems by mutableStateOf(emptySet<GoalProblem>())
        private set
    var loaded by mutableStateOf(false)
        private set
    var done by mutableStateOf(false)
        private set
    private val saving = OneAtATime()
    var existing by mutableStateOf<SavingsGoalEntity?>(null)
        private set
    var hasEntries by mutableStateOf(false)
        private set

    /** The name of the wallet the goal is kept in, also when that wallet is hidden. */
    var keptIn by mutableStateOf<String?>(null)
        private set

    val wallets: StateFlow<List<WalletEntity>> = db.walletDao().observeActiveWithBalance()
        .map { rows -> rows.map { it.wallet } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val goal = goalId?.let { db.savingsGoalDao().get(it) }
            if (goal != null) {
                existing = goal
                hasEntries = db.transactionDao().countForGoal(goal.id) > 0
                keptIn = goal.walletId?.let { db.walletDao().get(it)?.name }
                val form = GoalFormLogic.fromEntity(goal)
                name.setTextAndPlaceCursorAtEnd(form.name)
                target.setTextAndPlaceCursorAtEnd(form.targetText)
                targetDate = form.targetDate
                walletId = form.walletId
            }
            loaded = true
        }
    }

    fun selectTargetDate(value: LocalDate?) {
        targetDate = value
    }

    fun selectWallet(id: Long?) {
        walletId = id
    }

    fun save() {
        val form = GoalForm(name.text.toString(), target.text.toString(), targetDate, walletId)
        problems = GoalFormLogic.problems(form)
        if (problems.isNotEmpty()) return
        saving.launch(viewModelScope) {
            val goal = GoalFormLogic.toEntity(form, existing, now(), walletFixed = hasEntries)
            if (existing == null) db.savingsGoalDao().insert(goal) else db.savingsGoalDao().update(goal)
            done = true
            true
        }
    }

    /** A hidden goal keeps its entries but leaves Home and the list of goals in use. */
    fun toggleHidden() {
        val goal = existing ?: return
        viewModelScope.launch {
            db.savingsGoalDao().update(goal.copy(isArchived = !goal.isArchived))
            done = true
        }
    }

    /** Only offered when nothing is logged for the goal; the database refuses otherwise. */
    fun delete() {
        val goal = existing ?: return
        viewModelScope.launch {
            db.savingsGoalDao().delete(goal)
            done = true
        }
    }
}

/** Everything the goal screen draws, apart from its two text fields. */
data class GoalEditorUi(
    val isEdit: Boolean,
    val targetDate: LocalDate?,
    val wallets: List<WalletEntity>,
    val walletId: Long?,
    val today: LocalDate,
    val problems: Set<GoalProblem> = emptySet(),
    val hidden: Boolean = false,
    val canDelete: Boolean = false,
    /** Something is logged for the goal: its wallet is shown, not offered ([keptIn] is its name, null for No wallet). */
    val walletFixed: Boolean = false,
    val keptIn: String? = null,
)

@Composable
fun GoalEditorRoute(goalId: Long?, onDone: () -> Unit) {
    val container = LocalAppContainer.current
    val today by container.dayClock.today.collectAsStateWithLifecycle()
    val vm = viewModel { GoalEditorViewModel(container.database, goalId) }
    val wallets by vm.wallets.collectAsStateWithLifecycle()
    LaunchedEffect(vm.done) { if (vm.done) onDone() }
    if (!vm.loaded) {
        Box(Modifier.fillMaxSize().background(FitakaTheme.colors.background))
        return
    }
    GoalEditorContent(
        ui = GoalEditorUi(
            isEdit = vm.isEdit, targetDate = vm.targetDate, wallets = wallets, walletId = vm.walletId, today = today,
            problems = vm.problems, hidden = vm.existing?.isArchived == true, canDelete = vm.isEdit && !vm.hasEntries,
            walletFixed = vm.isEdit && vm.hasEntries, keptIn = vm.keptIn,
        ),
        name = vm.name, target = vm.target,
        onTargetDate = vm::selectTargetDate, onWallet = vm::selectWallet,
        onSave = vm::save, onToggleHidden = vm::toggleHidden, onDelete = vm::delete, onBack = onDone,
    )
}

@Composable
fun GoalEditorContent(
    ui: GoalEditorUi,
    name: TextFieldState,
    target: TextFieldState,
    onTargetDate: (LocalDate?) -> Unit,
    onWallet: (Long?) -> Unit,
    onSave: () -> Unit,
    onToggleHidden: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    val c = FitakaTheme.colors
    val type = MaterialTheme.typography
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    // A question about something that cannot be undone is asked over the whole screen, not in the footer.
    if (confirmDelete) {
        FitakaConfirmDialog(
            title = "Delete this goal?",
            lines = listOf("Nothing is logged for it, so no totals change. This cannot be undone."),
            confirmLabel = "Yes, delete",
            dismissLabel = "Keep",
            onConfirm = onDelete,
            onDismiss = { confirmDelete = false },
        )
    }

    FitakaScreen(
        title = if (ui.isEdit) "Edit goal" else "Add a goal",
        onBack = onBack,
        pinnedAction = {
            FitakaButton("Save", onClick = onSave, modifier = Modifier.fillMaxWidth(), style = FitakaButtonStyle.Primary, icon = FitakaIcons.Check)
        },
    ) {
        item(key = "name") {
            FitakaTextField(
                state = name, label = "What you are saving for", placeholder = "e.g. Emergency fund",
                isError = GoalProblem.NAME in ui.problems,
                supportingText = if (GoalProblem.NAME in ui.problems) "Give the goal a name" else null,
            )
        }
        item(key = "target") {
            FitakaTextField(
                state = target, label = "Target amount", placeholder = "No target", prefix = Peso.SIGN,
                isError = GoalProblem.TARGET in ui.problems,
                supportingText = if (GoalProblem.TARGET in ui.problems) "Enter an amount above ₱0.00, or leave it empty" else "Leave empty to save with no target.",
                inputTransformation = MoneyInput,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }
        item(key = "date") { OptionalDateChoice("Target date", ui.targetDate, ui.today, onTargetDate) }
        item(key = "wallet") {
            if (ui.walletFixed) {
                // Every entry of the goal went through this wallet; changing it would send "Take out" elsewhere.
                Column {
                    SectionHeader("Where the savings are kept")
                    Spacer(Modifier.height(8.dp))
                    Text(ui.keptIn ?: GoalText.NO_WALLET, style = type.bodyLarge, color = c.ink)
                    Spacer(Modifier.height(4.dp))
                    Text(GoalText.WALLET_FIXED, style = type.bodySmall, color = c.inkMuted)
                }
            } else {
                WalletChoice(
                    title = "Where the savings are kept",
                    wallets = ui.wallets, selectedId = ui.walletId, onSelect = onWallet,
                    noneLabel = "No wallet", noneSelected = true,
                    hint = "With No wallet, money is only set aside inside the wallet it is already in.",
                )
            }
        }
        if (ui.isEdit) {
            item(key = "hide") {
                Column {
                    FitakaButton(if (ui.hidden) "Show goal again" else "Hide goal", onClick = onToggleHidden, modifier = Modifier.fillMaxWidth())
                    Text(
                        "A hidden goal keeps its entries but is left out of Home.",
                        style = type.bodySmall, color = c.inkMuted, modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                    )
                }
            }
            if (ui.canDelete) {
                item(key = "delete") {
                    FitakaButton("Delete goal", onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** Words on the goal screen. */
object GoalText {
    const val NO_WALLET = "No wallet: set aside inside the wallet each amount came from"
    const val WALLET_FIXED = "This stays as it is once something is saved for this goal, so every entry agrees on where the money is."
}
