package com.pitakaview.app.ui.kita

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
import androidx.compose.foundation.text.input.InputTransformation
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
import com.pitakaview.app.data.db.entity.CategoryEntity
import com.pitakaview.app.data.db.entity.PayScheduleEntity
import com.pitakaview.app.data.db.entity.WalletEntity
import com.pitakaview.app.di.LocalAppContainer
import com.pitakaview.app.domain.CategoryKind
import com.pitakaview.app.domain.PayCalendar
import com.pitakaview.app.domain.PayFrequency
import com.pitakaview.app.domain.PayScheduleForm
import com.pitakaview.app.domain.PayScheduleFormLogic
import com.pitakaview.app.domain.PayScheduleProblem
import com.pitakaview.app.domain.Peso
import com.pitakaview.app.domain.WeekendRule
import com.pitakaview.app.ui.OneAtATime
import com.pitakaview.app.ui.components.FitakaButton
import com.pitakaview.app.ui.components.FitakaButtonStyle
import com.pitakaview.app.ui.components.FitakaChip
import com.pitakaview.app.ui.components.FitakaConfirmDialog
import com.pitakaview.app.ui.components.FitakaScreen
import com.pitakaview.app.ui.components.FitakaTextField
import com.pitakaview.app.ui.components.FitakaToggleRow
import com.pitakaview.app.ui.components.MoneyInput
import com.pitakaview.app.ui.components.ProblemText
import com.pitakaview.app.ui.components.RevealRequest
import com.pitakaview.app.ui.components.SectionHeader
import com.pitakaview.app.ui.home.friendlyDate
import com.pitakaview.app.ui.icons.FitakaIcons
import com.pitakaview.app.ui.settings.REMINDERS_OFF_HINT
import com.pitakaview.app.ui.theme.FitakaTheme
import java.time.DayOfWeek
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

internal val DayPattern = Regex("""^\d{0,2}$""")

/** A day of the month: up to two digits. Whether it is 1 to 31 is checked on Save. */
internal val DayInput = InputTransformation { if (!DayPattern.matches(asCharSequence())) revertAllChanges() }

val PayFrequency.label: String
    get() = when (this) {
        PayFrequency.SEMI_MONTHLY -> "Twice a month"
        PayFrequency.MONTHLY -> "Once a month"
        PayFrequency.WEEKLY -> "Every week"
    }

val WeekendRule.label: String
    get() = when (this) {
        WeekendRule.KEEP -> "Same date"
        WeekendRule.PREVIOUS_WEEKDAY -> "Friday before"
        WeekendRule.NEXT_WEEKDAY -> "Monday after"
    }

/** Add or change a payday. Checks what was typed with [PayScheduleFormLogic]. */
class PayScheduleEditorViewModel(
    private val db: FitakaDatabase,
    scheduleId: Long?,
    private val now: () -> Instant = Instant::now,
) : ViewModel() {
    val isEdit = scheduleId != null
    val name = TextFieldState()
    val amount = TextFieldState()
    val firstDay = TextFieldState("15")
    val secondDay = TextFieldState("31")

    var frequency by mutableStateOf(PayFrequency.SEMI_MONTHLY)
        private set
    var dayOfWeek by mutableStateOf(DayOfWeek.FRIDAY)
        private set
    var weekendRule by mutableStateOf(WeekendRule.KEEP)
        private set
    var walletId by mutableStateOf<Long?>(null)
        private set
    var categoryId by mutableStateOf<Long?>(null)
        private set
    var remind by mutableStateOf(true)
        private set

    /** Shown only after a Save attempt. */
    // Worked out again as the owner types and picks, once Save has been tried: a mistake put right
    // stops being shown at once (until 2026-11-27 it stayed until the next Save).
    private var tried by mutableStateOf(false)
    val problems: Set<PayScheduleProblem> by derivedStateOf { if (tried) PayScheduleFormLogic.problems(form()) else emptySet() }

    /** Goes up at each Save refused for a problem: the screen then brings the first one into view. */
    var refusedSaves by mutableIntStateOf(0)
        private set
    var loaded by mutableStateOf(false)
        private set
    var done by mutableStateOf(false)
        private set
    private val saving = OneAtATime()

    private var existing: PayScheduleEntity? = null

    val wallets: StateFlow<List<WalletEntity>> = db.walletDao().observeActiveWithBalance()
        .map { rows -> rows.map { it.wallet } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = db.categoryDao().observeActive(CategoryKind.INCOME)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val row = scheduleId?.let { db.payScheduleDao().get(it) }
            if (row != null) {
                existing = row
                val form = PayScheduleFormLogic.fromEntity(row)
                name.setTextAndPlaceCursorAtEnd(form.name)
                amount.setTextAndPlaceCursorAtEnd(form.amountText)
                firstDay.setTextAndPlaceCursorAtEnd(form.firstDayText)
                secondDay.setTextAndPlaceCursorAtEnd(form.secondDayText)
                frequency = form.frequency
                dayOfWeek = form.dayOfWeek
                weekendRule = form.weekendRule
                walletId = form.walletId
                categoryId = form.categoryId
                remind = form.remind
            } else {
                // Start on the likeliest choices: the first wallet, and Salary if that category is still there.
                walletId = db.walletDao().observeActiveWithBalance().first().firstOrNull()?.wallet?.id
                val income = db.categoryDao().observeActive(CategoryKind.INCOME).first()
                categoryId = (income.firstOrNull { it.name == "Salary" } ?: income.firstOrNull())?.id
            }
            loaded = true
        }
    }

    /** What is on screen right now. Read during composition too, for the "next paydays" preview. */
    fun form() = PayScheduleForm(
        name = name.text.toString(), amountText = amount.text.toString(), frequency = frequency,
        firstDayText = firstDay.text.toString(), secondDayText = secondDay.text.toString(),
        dayOfWeek = dayOfWeek, weekendRule = weekendRule, walletId = walletId, categoryId = categoryId, remind = remind,
    )

    fun selectFrequency(value: PayFrequency) {
        frequency = value
    }

    fun selectDayOfWeek(value: DayOfWeek) {
        dayOfWeek = value
    }

    fun selectWeekendRule(value: WeekendRule) {
        weekendRule = value
    }

    fun selectWallet(id: Long) {
        walletId = id
    }

    fun selectCategory(id: Long) {
        categoryId = id
    }

    fun selectRemind(value: Boolean) {
        remind = value
    }

    fun save() {
        val form = form()
        tried = true
        if (problems.isNotEmpty()) {
            refusedSaves++
            return
        }
        saving.launch(viewModelScope) {
            val row = PayScheduleFormLogic.toEntity(form, existing, now())
            if (existing == null) db.payScheduleDao().insert(row) else db.payScheduleDao().update(row)
            done = true
            true
        }
    }

    /** Income already logged for this payday stays; it only stops being linked to it. */
    fun remove() {
        val row = existing ?: return
        viewModelScope.launch {
            db.payScheduleDao().delete(row)
            done = true
        }
    }
}

/** Everything the payday screen draws, apart from its four text fields. */
data class PayScheduleEditorUi(
    val isEdit: Boolean,
    val frequency: PayFrequency,
    val dayOfWeek: DayOfWeek,
    val weekendRule: WeekendRule,
    val wallets: List<WalletEntity>,
    val categories: List<CategoryEntity>,
    val walletId: Long?,
    val categoryId: Long?,
    val today: LocalDate,
    /** The next paydays the form describes; empty while its days are not valid. */
    val nextPaydays: List<LocalDate> = emptyList(),
    val problems: Set<PayScheduleProblem> = emptySet(),
    val remind: Boolean = true,
    /** Whether reminders as a whole are switched on in Settings. */
    val remindersOn: Boolean = true,
    /** Goes up at each Save refused for a problem (see FitakaScreen's reveal). */
    val refusedSaves: Int = 0,
)

@Composable
fun PayScheduleEditorRoute(scheduleId: Long?, onDone: () -> Unit) {
    val container = LocalAppContainer.current
    val vm = viewModel { PayScheduleEditorViewModel(container.database, scheduleId) }
    val wallets by vm.wallets.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val today by container.dayClock.today.collectAsStateWithLifecycle()
    val remindersOn by container.settings.remindersOn.collectAsStateWithLifecycle(initialValue = true)
    LaunchedEffect(vm.done) { if (vm.done) onDone() }
    if (!vm.loaded) {
        Box(Modifier.fillMaxSize().background(FitakaTheme.colors.background))
        return
    }
    val rule = PayScheduleFormLogic.rule(vm.form())
    PayScheduleEditorContent(
        ui = PayScheduleEditorUi(
            refusedSaves = vm.refusedSaves,
            isEdit = vm.isEdit, frequency = vm.frequency, dayOfWeek = vm.dayOfWeek, weekendRule = vm.weekendRule,
            wallets = wallets, categories = categories, walletId = vm.walletId, categoryId = vm.categoryId, today = today,
            nextPaydays = if (rule == null) emptyList() else PayCalendar.upcoming(rule, from = today, count = 3),
            problems = vm.problems, remind = vm.remind, remindersOn = remindersOn,
        ),
        name = vm.name, amount = vm.amount, firstDay = vm.firstDay, secondDay = vm.secondDay,
        onFrequency = vm::selectFrequency, onDayOfWeek = vm::selectDayOfWeek, onWeekendRule = vm::selectWeekendRule,
        onWallet = vm::selectWallet, onCategory = vm::selectCategory,
        onSave = vm::save, onRemove = vm::remove, onBack = onDone, onRemind = vm::selectRemind,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PayScheduleEditorContent(
    ui: PayScheduleEditorUi,
    name: TextFieldState,
    amount: TextFieldState,
    firstDay: TextFieldState,
    secondDay: TextFieldState,
    onFrequency: (PayFrequency) -> Unit,
    onDayOfWeek: (DayOfWeek) -> Unit,
    onWeekendRule: (WeekendRule) -> Unit,
    onWallet: (Long) -> Unit,
    onCategory: (Long) -> Unit,
    onSave: () -> Unit,
    onRemove: () -> Unit,
    onBack: () -> Unit,
    onRemind: (Boolean) -> Unit = {},
) {
    val c = FitakaTheme.colors
    val type = MaterialTheme.typography
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    val chips = Arrangement.spacedBy(8.dp)

    // A question about something that cannot be undone is asked over the whole screen, not in the footer.
    if (confirmRemove) {
        FitakaConfirmDialog(
            title = "Remove this payday?",
            lines = listOf("Income you already logged for it stays in your totals."),
            confirmLabel = "Yes, remove",
            dismissLabel = "Keep",
            onConfirm = onRemove,
            onDismiss = { confirmRemove = false },
        )
    }

    FitakaScreen(
        title = if (ui.isEdit) "Edit payday" else "Add a payday",
        reveal = RevealRequest.firstOf(
            ui.refusedSaves,
            "name".takeIf { PayScheduleProblem.NAME in ui.problems },
            "amount".takeIf { PayScheduleProblem.AMOUNT in ui.problems },
            "first-day".takeIf { PayScheduleProblem.FIRST_DAY in ui.problems },
            "second-day".takeIf { PayScheduleProblem.SECOND_DAY in ui.problems || PayScheduleProblem.SAME_DAYS in ui.problems },
            "wallet".takeIf { PayScheduleProblem.WALLET in ui.problems },
            "category".takeIf { PayScheduleProblem.CATEGORY in ui.problems },
        ),
        onBack = onBack,
        pinnedAction = {
            FitakaButton("Save", onClick = onSave, modifier = Modifier.fillMaxWidth(), style = FitakaButtonStyle.Primary, icon = FitakaIcons.Check)
        },
    ) {
        item(key = "name") {
            FitakaTextField(
                state = name, label = "Name", placeholder = "e.g. Sahod",
                isError = PayScheduleProblem.NAME in ui.problems,
                supportingText = if (PayScheduleProblem.NAME in ui.problems) "Give this payday a name" else null,
            )
        }
        item(key = "amount") {
            FitakaTextField(
                state = amount, label = "How much you expect", placeholder = "0.00", prefix = Peso.SIGN,
                isError = PayScheduleProblem.AMOUNT in ui.problems,
                supportingText = if (PayScheduleProblem.AMOUNT in ui.problems) "Enter an amount above ₱0.00" else "You can change the amount each time you receive it.",
                inputTransformation = MoneyInput,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }
        item(key = "frequency") {
            Column {
                SectionHeader("How often")
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = chips, verticalArrangement = chips) {
                    PayFrequency.entries.forEach { FitakaChip(it.label, selected = it == ui.frequency, onClick = { onFrequency(it) }) }
                }
            }
        }

        when (ui.frequency) {
            PayFrequency.SEMI_MONTHLY -> {
                item(key = "first-day") { DayField(firstDay, "First payday (day of the month)", PayScheduleProblem.FIRST_DAY in ui.problems, hint = null) }
                item(key = "second-day") {
                    Column {
                        DayField(secondDay, "Second payday (day of the month)", PayScheduleProblem.SECOND_DAY in ui.problems, hint = LAST_DAY_HINT)
                        if (PayScheduleProblem.SAME_DAYS in ui.problems) ProblemText("The two paydays must be different days")
                    }
                }
            }
            PayFrequency.MONTHLY -> item(key = "first-day") {
                DayField(firstDay, "Payday (day of the month)", PayScheduleProblem.FIRST_DAY in ui.problems, hint = LAST_DAY_HINT)
            }
            PayFrequency.WEEKLY -> item(key = "weekday") {
                Column {
                    SectionHeader("Which day")
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = chips, verticalArrangement = chips) {
                        DayOfWeek.entries.forEach {
                            FitakaChip(it.getDisplayName(DateTextStyle.SHORT, Locale.ENGLISH), selected = it == ui.dayOfWeek, onClick = { onDayOfWeek(it) })
                        }
                    }
                }
            }
        }

        // A weekly payday is a weekday the owner picked, so there is no weekend to move it off.
        if (ui.frequency != PayFrequency.WEEKLY) {
            item(key = "weekend") {
                Column {
                    SectionHeader("If payday falls on a weekend")
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = chips, verticalArrangement = chips) {
                        WeekendRule.entries.forEach { FitakaChip(it.label, selected = it == ui.weekendRule, onClick = { onWeekendRule(it) }) }
                    }
                }
            }
        }

        if (ui.nextPaydays.isNotEmpty()) {
            item(key = "preview") {
                Text(
                    "Next paydays: " + ui.nextPaydays.joinToString(", ") { friendlyDate(it, ui.today) },
                    style = type.bodyMedium, color = c.ink, modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }

        item(key = "wallet") {
            Column {
                SectionHeader("Received in")
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = chips, verticalArrangement = chips) {
                    ui.wallets.forEach { FitakaChip(it.name, selected = it.id == ui.walletId, onClick = { onWallet(it.id) }) }
                }
                if (PayScheduleProblem.WALLET in ui.problems) ProblemText("Pick a wallet")
            }
        }
        item(key = "category") {
            Column {
                SectionHeader("Category")
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = chips, verticalArrangement = chips) {
                    ui.categories.forEach { FitakaChip(it.name, selected = it.id == ui.categoryId, onClick = { onCategory(it.id) }) }
                }
                if (PayScheduleProblem.CATEGORY in ui.problems) ProblemText("Pick a category")
            }
        }

        item(key = "remind") {
            FitakaToggleRow(
                title = "Remind me on payday",
                checked = ui.remind,
                onCheckedChange = onRemind,
                subtitle = if (ui.remindersOn) "A notification that morning." else REMINDERS_OFF_HINT,
            )
        }

        if (ui.isEdit) {
            item(key = "remove") {
                FitakaButton("Remove payday", onClick = { confirmRemove = true }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

internal const val LAST_DAY_HINT = "31 means the last day of the month."

@Composable
internal fun DayField(state: TextFieldState, label: String, isError: Boolean, hint: String?) {
    FitakaTextField(
        state = state, label = label, placeholder = "1 to 31",
        isError = isError,
        supportingText = if (isError) "Type a day from 1 to 31" else hint,
        inputTransformation = DayInput,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}
