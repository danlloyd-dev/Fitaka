package com.pitakaview.app.ui.entry

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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pitakaview.app.data.db.entity.CategoryEntity
import com.pitakaview.app.data.db.entity.WalletEntity
import com.pitakaview.app.di.LocalAppContainer
import com.pitakaview.app.domain.EntryKind
import com.pitakaview.app.domain.EntryProblem
import com.pitakaview.app.domain.Peso
import com.pitakaview.app.ui.components.MoneyInput
import com.pitakaview.app.ui.components.PitakaButton
import com.pitakaview.app.ui.components.PitakaButtonStyle
import com.pitakaview.app.ui.components.PitakaCard
import com.pitakaview.app.ui.components.PitakaChip
import com.pitakaview.app.ui.components.PitakaScreen
import com.pitakaview.app.ui.components.PitakaSegmented
import com.pitakaview.app.ui.components.PitakaTextField
import com.pitakaview.app.ui.components.ProblemText
import com.pitakaview.app.ui.components.SectionHeader
import com.pitakaview.app.ui.home.friendlyDate
import com.pitakaview.app.ui.icons.PitakaIcons
import com.pitakaview.app.ui.theme.PitakaTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Everything the quick-entry screen draws, apart from the two text fields. */
data class EntryEditorUi(
    val isEdit: Boolean,
    val kind: EntryKind,
    val categories: List<CategoryEntity>,
    val wallets: List<WalletEntity>,
    val categoryId: Long?,
    val fromWalletId: Long?,
    val toWalletId: Long?,
    val date: LocalDate,
    val today: LocalDate,
    val problems: Set<EntryProblem> = emptySet(),
    val saveError: String? = null,
    val editable: Boolean = true,
    /** Set when this entry is the pay received for a payday. */
    val paydayName: String? = null,
    /** True when opened from a payday's "Received" button: the kind is not offered. */
    val kindLocked: Boolean = false,
)

@Composable
fun EntryEditorRoute(transactionId: Long?, startKind: EntryKind, payScheduleId: Long?, onDone: () -> Unit, onOpenWallets: () -> Unit) {
    val container = LocalAppContainer.current
    val today by container.dayClock.today.collectAsStateWithLifecycle()
    val vm = viewModel {
        EntryEditorViewModel(
            container.database, container.ledger, container.settings, container.dayClock.today.value, transactionId, startKind, payScheduleId,
        )
    }
    val wallets by vm.wallets.collectAsStateWithLifecycle()
    val expense by vm.expenseCategories.collectAsStateWithLifecycle()
    val income by vm.incomeCategories.collectAsStateWithLifecycle()
    LaunchedEffect(vm.done) { if (vm.done) onDone() }

    if (!vm.loaded) {
        Box(Modifier.fillMaxSize().background(PitakaTheme.colors.background))
        return
    }
    EntryEditorContent(
        ui = EntryEditorUi(
            isEdit = vm.isEdit, kind = vm.kind,
            categories = if (vm.kind == EntryKind.INCOME) income else expense,
            wallets = wallets, categoryId = vm.categoryId, fromWalletId = vm.fromWalletId, toWalletId = vm.toWalletId,
            date = vm.date, today = today, problems = vm.problems, saveError = vm.saveError, editable = vm.editable,
            paydayName = vm.paydayName, kindLocked = vm.kindLocked,
        ),
        amount = vm.amount, note = vm.note,
        onKind = vm::selectKind, onCategory = vm::selectCategory,
        onFromWallet = vm::selectFromWallet, onToWallet = vm::selectToWallet, onDate = vm::selectDate,
        onSave = vm::save, onDelete = vm::delete, onBack = onDone, onOpenWallets = onOpenWallets,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryEditorContent(
    ui: EntryEditorUi,
    amount: TextFieldState,
    note: TextFieldState,
    onKind: (EntryKind) -> Unit,
    onCategory: (Long) -> Unit,
    onFromWallet: (Long) -> Unit,
    onToWallet: (Long) -> Unit,
    onDate: (LocalDate) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    onOpenWallets: () -> Unit,
) {
    val c = PitakaTheme.colors
    val type = MaterialTheme.typography
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    val amountFocus = remember { FocusRequester() }

    PitakaScreen(
        title = if (ui.isEdit) "Edit entry" else "Add entry",
        subtitle = ui.paydayName?.let { "Pay received · $it" },
        onBack = onBack,
        // Save stays above the keyboard: type the amount, tap a category, tap Save.
        pinnedAction = if (!ui.editable) {
            null
        } else {
            {
                if (confirmDelete) {
                    // The confirmation lives in the pinned bar, so it is always on screen. Inside the list it
                    // opened below the bar on a phone and could not be reached.
                    Column {
                        Text("Delete this entry?", style = type.titleSmall, color = c.ink)
                        Text("It is removed from your totals. This cannot be undone.", style = type.bodySmall, color = c.ink)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PitakaButton("Yes, delete", onClick = onDelete, modifier = Modifier.weight(1f))
                            PitakaButton("Keep", onClick = { confirmDelete = false }, modifier = Modifier.weight(1f))
                        }
                    }
                } else {
                    Column {
                        if (ui.saveError != null) ProblemText(ui.saveError)
                        PitakaButton("Save", onClick = onSave, modifier = Modifier.fillMaxWidth(), style = PitakaButtonStyle.Primary, icon = PitakaIcons.Check)
                    }
                }
            }
        },
    ) {
        if (!ui.editable) {
            item(key = "elsewhere") {
                PitakaCard(Modifier.fillMaxWidth()) {
                    Text("This entry belongs to another section", style = type.titleSmall, color = c.ink)
                    Text("Loans, baon and savings entries are changed from their own tabs.", style = type.bodyMedium, color = c.inkMuted)
                }
            }
            return@PitakaScreen
        }

        if (!ui.kindLocked) {
            item(key = "kind") {
                PitakaSegmented(EntryKind.entries.map { it.label }, ui.kind.ordinal, onSelect = { onKind(EntryKind.entries[it]) })
            }
        }

        item(key = "amount") {
            PitakaTextField(
                state = amount,
                label = "Amount",
                modifier = Modifier.focusRequester(amountFocus),
                placeholder = "0.00",
                prefix = Peso.SIGN,
                isError = EntryProblem.AMOUNT in ui.problems,
                supportingText = if (EntryProblem.AMOUNT in ui.problems) "Enter an amount above ₱0.00" else null,
                inputTransformation = MoneyInput,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            // A new entry starts with the keyboard on the amount, so the first thing you do is type it.
            if (!ui.isEdit) LaunchedEffect(Unit) { amountFocus.requestFocus() }
        }

        if (ui.kind != EntryKind.TRANSFER) {
            item(key = "category") {
                Column {
                    SectionHeader("Category")
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ui.categories.forEach { PitakaChip(it.name, selected = it.id == ui.categoryId, onClick = { onCategory(it.id) }) }
                    }
                    if (EntryProblem.CATEGORY in ui.problems) ProblemText("Pick a category")
                }
            }
        }

        when (ui.kind) {
            EntryKind.EXPENSE -> item(key = "wallet") {
                WalletChoice("Paid from", ui.wallets, ui.fromWalletId, onFromWallet, EntryProblem.WALLET in ui.problems)
            }
            EntryKind.INCOME -> item(key = "wallet") {
                WalletChoice("Received in", ui.wallets, ui.toWalletId, onToWallet, EntryProblem.WALLET in ui.problems)
            }
            EntryKind.TRANSFER -> if (ui.wallets.size < 2) {
                item(key = "need-two") {
                    PitakaCard(Modifier.fillMaxWidth()) {
                        Text("You need two wallets to move money between them.", style = type.bodyMedium, color = c.ink)
                        Spacer(Modifier.height(10.dp))
                        PitakaButton("Add a wallet", onClick = onOpenWallets, icon = PitakaIcons.Add)
                    }
                }
            } else {
                item(key = "from") {
                    WalletChoice("From", ui.wallets, ui.fromWalletId, onFromWallet, EntryProblem.WALLET in ui.problems)
                }
                item(key = "to") {
                    Column {
                        WalletChoice("To", ui.wallets, ui.toWalletId, onToWallet, problem = false)
                        if (EntryProblem.SAME_WALLET in ui.problems) ProblemText("Pick two different wallets")
                    }
                }
            }
        }

        item(key = "date") {
            Column {
                SectionHeader("Date")
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val yesterday = ui.today.minusDays(1)
                    PitakaChip("Today", selected = ui.date == ui.today, onClick = { onDate(ui.today) })
                    PitakaChip("Yesterday", selected = ui.date == yesterday, onClick = { onDate(yesterday) })
                    val other = ui.date != ui.today && ui.date != yesterday
                    PitakaChip(if (other) friendlyDate(ui.date, ui.today) else "Other date", selected = other, onClick = { pickDate = true })
                }
            }
        }

        item(key = "note") {
            PitakaTextField(state = note, label = "Note", placeholder = "Optional")
        }

        if (ui.isEdit && !confirmDelete) {
            item(key = "delete") {
                PitakaButton("Delete entry", onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    if (pickDate) PickDateDialog(ui.date, onPick = onDate, onDismiss = { pickDate = false })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WalletChoice(title: String, wallets: List<WalletEntity>, selectedId: Long?, onSelect: (Long) -> Unit, problem: Boolean) {
    Column {
        SectionHeader(title)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            wallets.forEach { PitakaChip(it.name, selected = it.id == selectedId, onClick = { onSelect(it.id) }) }
        }
        if (problem) ProblemText("Pick a wallet")
    }
}

/** The stock Material date picker, themed by PitakaTheme's colours and font. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickDateDialog(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}
