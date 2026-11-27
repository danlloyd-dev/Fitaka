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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.pitakaview.app.data.db.entity.BaonEntity
import com.pitakaview.app.data.db.entity.CategoryEntity
import com.pitakaview.app.data.db.entity.WalletEntity
import com.pitakaview.app.di.LocalAppContainer
import com.pitakaview.app.domain.EntryKind
import com.pitakaview.app.domain.EntryProblem
import com.pitakaview.app.domain.Peso
import com.pitakaview.app.ui.components.DateChoice
import com.pitakaview.app.ui.components.FitakaButton
import com.pitakaview.app.ui.components.FitakaButtonStyle
import com.pitakaview.app.ui.components.FitakaCard
import com.pitakaview.app.ui.components.FitakaChip
import com.pitakaview.app.ui.components.FitakaConfirmDialog
import com.pitakaview.app.ui.components.FitakaScreen
import com.pitakaview.app.ui.components.FitakaSegmented
import com.pitakaview.app.ui.components.FitakaTextField
import com.pitakaview.app.ui.components.MoneyInput
import com.pitakaview.app.ui.components.ProblemText
import com.pitakaview.app.ui.components.SectionHeader
import com.pitakaview.app.ui.icons.FitakaIcons
import com.pitakaview.app.ui.sections.MoveEditorRoute
import com.pitakaview.app.ui.theme.FitakaTheme
import java.time.LocalDate

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
    /** The baon spending can be charged to, and the one picked (null: not from baon). */
    val baons: List<BaonEntity> = emptyList(),
    val baonId: Long? = null,
    /** Set when this entry is the pay received for a payday. */
    val paydayName: String? = null,
    /** True when opened from a payday's "Received" button: the kind is not offered. */
    val kindLocked: Boolean = false,
    /** Null unless this entry is half of a move between the owner's own wallets; then whether it stays one. */
    val ownMove: Boolean? = null,
)

@Composable
fun EntryEditorRoute(
    transactionId: Long?,
    startKind: EntryKind,
    payScheduleId: Long?,
    baonId: Long?,
    onDone: () -> Unit,
    onOpenWallets: () -> Unit,
) {
    val container = LocalAppContainer.current
    val today by container.dayClock.today.collectAsStateWithLifecycle()
    val vm = viewModel {
        EntryEditorViewModel(
            container.database, container.ledger, container.settings, container.dayClock.today.value, transactionId, startKind, payScheduleId, baonId,
        )
    }
    val wallets by vm.wallets.collectAsStateWithLifecycle()
    val expense by vm.expenseCategories.collectAsStateWithLifecycle()
    val income by vm.incomeCategories.collectAsStateWithLifecycle()
    val baons by vm.baons.collectAsStateWithLifecycle()
    LaunchedEffect(vm.done) { if (vm.done) onDone() }

    if (!vm.loaded) {
        Box(Modifier.fillMaxSize().background(FitakaTheme.colors.background))
        return
    }
    // A loan payment, a savings entry or a baon entry has its own, simpler form.
    if (!vm.editable) {
        MoveEditorRoute(transactionId = transactionId, purpose = null, linkId = null, onDone = onDone)
        return
    }
    EntryEditorContent(
        ui = EntryEditorUi(
            isEdit = vm.isEdit, kind = vm.kind,
            categories = if (vm.kind == EntryKind.INCOME) income else expense,
            wallets = wallets, categoryId = vm.categoryId, fromWalletId = vm.fromWalletId, toWalletId = vm.toWalletId,
            date = vm.date, today = today, problems = vm.problems, saveError = vm.saveError,
            baons = baons, baonId = vm.baonId, paydayName = vm.paydayName,
            // A half is money out or money in and stays that; what can change is whether it was a move.
            kindLocked = vm.kindLocked || vm.ownMove != null, ownMove = vm.ownMove,
        ),
        amount = vm.amount, note = vm.note,
        onKind = vm::selectKind, onCategory = vm::selectCategory,
        onFromWallet = vm::selectFromWallet, onToWallet = vm::selectToWallet, onDate = vm::selectDate, onBaon = vm::selectBaon,
        onSave = vm::save, onDelete = vm::delete, onBack = onDone, onOpenWallets = onOpenWallets,
        onOwnMove = vm::selectOwnMove,
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
    onBaon: (Long?) -> Unit = {},
    onOwnMove: (Boolean) -> Unit = {},
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
        title = if (ui.isEdit) "Edit entry" else "Add entry",
        // What the entry is tied to, said at the top where it cannot scroll under the keyboard.
        subtitle = ui.paydayName?.let { "Pay received · $it" }
            ?: ui.baons.firstOrNull { it.id == ui.baonId && ui.kind == EntryKind.EXPENSE }?.let { "From baon · ${it.name}" },
        onBack = onBack,
        // Save stays above the keyboard: type the amount, tap a category, tap Save.
        pinnedAction = {
            Column {
                if (ui.saveError != null) ProblemText(ui.saveError)
                FitakaButton("Save", onClick = onSave, modifier = Modifier.fillMaxWidth(), style = FitakaButtonStyle.Primary, icon = FitakaIcons.Check)
            }
        },
    ) {
        if (!ui.kindLocked) {
            item(key = "kind") {
                FitakaSegmented(EntryKind.entries.map { it.label }, ui.kind.ordinal, onSelect = { onKind(EntryKind.entries[it]) })
            }
        }

        // One half of a move between the owner's own wallets, as a bank statement showed it.
        ui.ownMove?.let { stays ->
            item(key = "own-move") {
                FitakaCard(Modifier.fillMaxWidth()) {
                    Column {
                        Text(EntryText.ownMoveTitle(ui.kind), style = type.titleSmall, color = c.ink)
                        Spacer(Modifier.height(4.dp))
                        Text(EntryText.ownMoveWords(ui.kind, stays), style = type.bodySmall, color = c.inkMuted)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FitakaChip(EntryText.OWN_MOVE, selected = stays, onClick = { onOwnMove(true) })
                            FitakaChip(EntryText.notOwnMove(ui.kind), selected = !stays, onClick = { onOwnMove(false) })
                        }
                    }
                }
            }
        }

        item(key = "amount") {
            FitakaTextField(
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

        // A move has no category: not a transfer typed in as one entry, and not a half while it stays a
        // move (it keeps the one it had, unseen, to go back to).
        if (ui.kind != EntryKind.TRANSFER && ui.ownMove != true) {
            item(key = "category") {
                Column {
                    SectionHeader("Category")
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ui.categories.forEach { FitakaChip(it.name, selected = it.id == ui.categoryId, onClick = { onCategory(it.id) }) }
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
                    FitakaCard(Modifier.fillMaxWidth()) {
                        Text("You need two wallets to move money between them.", style = type.bodyMedium, color = c.ink)
                        Spacer(Modifier.height(10.dp))
                        FitakaButton("Add a wallet", onClick = onOpenWallets, icon = FitakaIcons.Add)
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

        // Spending can be paid out of a baon you receive; it then counts against what is left of that baon.
        if (ui.kind == EntryKind.EXPENSE && ui.baons.isNotEmpty()) {
            item(key = "baon") {
                Column {
                    SectionHeader("From baon")
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FitakaChip("Not from baon", selected = ui.baonId == null, onClick = { onBaon(null) })
                        ui.baons.forEach { FitakaChip(it.name, selected = it.id == ui.baonId, onClick = { onBaon(it.id) }) }
                    }
                }
            }
        }

        item(key = "date") { DateChoice(ui.date, ui.today, onDate) }

        item(key = "note") {
            FitakaTextField(state = note, label = "Note", placeholder = "Optional")
        }

        if (ui.isEdit) {
            item(key = "delete") {
                FitakaButton("Delete entry", onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WalletChoice(title: String, wallets: List<WalletEntity>, selectedId: Long?, onSelect: (Long) -> Unit, problem: Boolean) {
    Column {
        SectionHeader(title)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            wallets.forEach { FitakaChip(it.name, selected = it.id == selectedId, onClick = { onSelect(it.id) }) }
        }
        if (problem) ProblemText("Pick a wallet")
    }
}

/** The words for an entry that is half of a move between the owner's own wallets. */
object EntryText {
    const val OWN_MOVE = "My own transfer"

    fun notOwnMove(kind: EntryKind) = if (kind == EntryKind.INCOME) "It was income" else "It was spending"

    fun ownMoveTitle(kind: EntryKind) = if (kind == EntryKind.INCOME) "Moved from another wallet of yours" else "Moved to another wallet of yours"

    fun ownMoveWords(kind: EntryKind, stays: Boolean) = when {
        stays -> "Your bank statements show this money leaving one of your wallets and arriving in another. It is not counted as spending or as income."
        kind == EntryKind.INCOME -> "Saved like this, it counts as income again, and its other half counts as spending again."
        else -> "Saved like this, it counts as spending again, and its other half counts as income again."
    }
}
