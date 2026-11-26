package com.pitakaview.app.ui.wallets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pitakaview.app.data.db.PitakaDatabase
import com.pitakaview.app.data.db.dao.WalletWithBalance
import com.pitakaview.app.data.db.entity.WalletEntity
import com.pitakaview.app.di.LocalAppContainer
import com.pitakaview.app.domain.Peso
import com.pitakaview.app.domain.WalletProvider
import com.pitakaview.app.domain.WalletRules
import com.pitakaview.app.domain.WalletType
import com.pitakaview.app.ui.components.AmountText
import com.pitakaview.app.ui.components.MoneyInput
import com.pitakaview.app.ui.components.PitakaButton
import com.pitakaview.app.ui.components.PitakaButtonStyle
import com.pitakaview.app.ui.components.PitakaCard
import com.pitakaview.app.ui.components.PitakaChip
import com.pitakaview.app.ui.components.PitakaDivider
import com.pitakaview.app.ui.components.PitakaListItem
import com.pitakaview.app.ui.components.PitakaScreen
import com.pitakaview.app.ui.components.PitakaTextField
import com.pitakaview.app.ui.components.ProblemText
import com.pitakaview.app.ui.components.SectionHeader
import com.pitakaview.app.ui.components.sketchBox
import com.pitakaview.app.ui.icons.PitakaIcons
import com.pitakaview.app.ui.theme.PitakaDimens
import com.pitakaview.app.ui.theme.PitakaTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

/** Quick picks when adding a wallet. The provider is what file imports will match on later. */
enum class WalletPreset(val label: String, val type: WalletType, val provider: WalletProvider, val suggestedName: String) {
    CASH("Cash", WalletType.CASH, WalletProvider.NONE, "Cash"),
    GCASH("GCash", WalletType.E_WALLET, WalletProvider.GCASH, "GCash"),
    MAYA("Maya", WalletType.E_WALLET, WalletProvider.MAYA, "Maya"),
    MARIBANK("MariBank", WalletType.BANK, WalletProvider.MARIBANK, "MariBank"),
    BANK("Other bank", WalletType.BANK, WalletProvider.NONE, ""),
    OTHER("Other", WalletType.OTHER, WalletProvider.NONE, ""),
}

val WalletType.label: String
    get() = when (this) {
        WalletType.CASH -> "Cash"
        WalletType.E_WALLET -> "E-wallet"
        WalletType.BANK -> "Bank"
        WalletType.OTHER -> "Other"
    }

// ---------- list ----------

class WalletsViewModel(db: PitakaDatabase) : ViewModel() {
    val wallets: StateFlow<List<WalletWithBalance>> = db.walletDao().observeAllWithBalance()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun WalletsRoute(onBack: () -> Unit, onAdd: () -> Unit, onOpen: (Long) -> Unit) {
    val container = LocalAppContainer.current
    val vm = viewModel { WalletsViewModel(container.database) }
    val wallets by vm.wallets.collectAsStateWithLifecycle()
    WalletsContent(wallets, onBack, onAdd, onOpen)
}

@Composable
fun WalletsContent(wallets: List<WalletWithBalance>, onBack: () -> Unit, onAdd: () -> Unit, onOpen: (Long) -> Unit) {
    val c = PitakaTheme.colors
    PitakaScreen(title = "Wallets", subtitle = "Where your money sits", onBack = onBack) {
        if (wallets.isNotEmpty()) {
            item(key = "list") {
                PitakaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    wallets.forEachIndexed { i, row ->
                        key(row.wallet.id) {
                            if (i > 0) PitakaDivider()
                            PitakaListItem(
                                title = row.wallet.name,
                                subtitle = row.wallet.type.label + if (row.wallet.isArchived) " · hidden" else "",
                                leading = {
                                    Box(
                                        Modifier.size(40.dp).sketchBox(RoundedCornerShape(12.dp), c.background, c.ink, PitakaDimens.borderThin),
                                        contentAlignment = Alignment.Center,
                                    ) { Icon(PitakaIcons.Wallet, contentDescription = null, tint = c.ink, modifier = Modifier.size(22.dp)) }
                                },
                                trailing = { AmountText(row.balanceCentavos) },
                                onClick = { onOpen(row.wallet.id) },
                            )
                        }
                    }
                }
            }
        }
        item(key = "add") {
            PitakaButton("Add wallet", onClick = onAdd, modifier = Modifier.fillMaxWidth(), style = PitakaButtonStyle.Primary, icon = PitakaIcons.Add)
        }
    }
}

// ---------- add / edit ----------

class WalletEditorViewModel(
    private val db: PitakaDatabase,
    walletId: Long?,
    private val now: () -> Instant = Instant::now,
) : ViewModel() {
    val isEdit = walletId != null
    val name = TextFieldState()
    val balance = TextFieldState()

    var preset by mutableStateOf<WalletPreset?>(null)
        private set
    var current by mutableStateOf<WalletWithBalance?>(null)
        private set
    var nameProblem by mutableStateOf(false)
        private set
    var balanceProblem by mutableStateOf(false)
        private set
    var hideBlocked by mutableStateOf(false)
        private set
    var loaded by mutableStateOf(false)
        private set
    var done by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            if (walletId != null) {
                current = db.walletDao().observeAllWithBalance().first().firstOrNull { it.wallet.id == walletId }
                current?.let { name.setTextAndPlaceCursorAtEnd(it.wallet.name) }
            }
            loaded = true
        }
    }

    fun selectPreset(choice: WalletPreset) {
        // Fill in the name unless the user already typed one of their own.
        val typed = name.text.toString()
        if (typed.isBlank() || WalletPreset.entries.any { it.suggestedName == typed }) {
            name.setTextAndPlaceCursorAtEnd(choice.suggestedName)
        }
        preset = choice
    }

    fun save() {
        val trimmedName = name.text.toString().trim()
        val typedBalance = balance.text.toString().trim()
        val amount = if (typedBalance.isEmpty()) null else Peso.parse(typedBalance)
        nameProblem = trimmedName.isEmpty()
        balanceProblem = typedBalance.isNotEmpty() && amount == null
        if (nameProblem || balanceProblem) return
        viewModelScope.launch {
            val row = current
            if (row == null) {
                db.walletDao().insert(
                    WalletEntity(
                        name = trimmedName,
                        type = preset?.type ?: WalletType.OTHER,
                        provider = preset?.provider ?: WalletProvider.NONE,
                        openingBalanceCentavos = amount ?: 0,
                        sortOrder = db.walletDao().count(),
                        createdAt = now(),
                    ),
                )
            } else {
                val opening = row.wallet.openingBalanceCentavos
                db.walletDao().update(
                    row.wallet.copy(
                        name = trimmedName,
                        // Empty means "the app's number is right": leave the balance alone.
                        openingBalanceCentavos = if (amount == null) opening else WalletRules.openingForActualBalance(opening, row.balanceCentavos, amount),
                    ),
                )
            }
            done = true
        }
    }

    fun toggleHidden() {
        val row = current ?: return
        viewModelScope.launch {
            if (!row.wallet.isArchived && db.walletDao().countActive() <= 1) {
                hideBlocked = true
                return@launch
            }
            db.walletDao().update(row.wallet.copy(isArchived = !row.wallet.isArchived))
            done = true
        }
    }
}

@Composable
fun WalletEditorRoute(walletId: Long?, onDone: () -> Unit) {
    val container = LocalAppContainer.current
    val vm = viewModel { WalletEditorViewModel(container.database, walletId) }
    LaunchedEffect(vm.done) { if (vm.done) onDone() }
    if (!vm.loaded) {
        Box(Modifier.fillMaxSize().background(PitakaTheme.colors.background))
        return
    }
    WalletEditorContent(
        isEdit = vm.isEdit, name = vm.name, balance = vm.balance, preset = vm.preset, current = vm.current,
        nameProblem = vm.nameProblem, balanceProblem = vm.balanceProblem, hideBlocked = vm.hideBlocked,
        onPreset = vm::selectPreset, onSave = vm::save, onToggleHidden = vm::toggleHidden, onBack = onDone,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WalletEditorContent(
    isEdit: Boolean,
    name: TextFieldState,
    balance: TextFieldState,
    preset: WalletPreset?,
    current: WalletWithBalance?,
    nameProblem: Boolean,
    balanceProblem: Boolean,
    hideBlocked: Boolean,
    onPreset: (WalletPreset) -> Unit,
    onSave: () -> Unit,
    onToggleHidden: () -> Unit,
    onBack: () -> Unit,
) {
    val c = PitakaTheme.colors
    PitakaScreen(
        title = if (isEdit) "Edit wallet" else "Add wallet",
        onBack = onBack,
        pinnedAction = {
            PitakaButton("Save", onClick = onSave, modifier = Modifier.fillMaxWidth(), style = PitakaButtonStyle.Primary, icon = PitakaIcons.Check)
        },
    ) {
        if (!isEdit) {
            item(key = "preset") {
                Column {
                    SectionHeader("What kind?")
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        WalletPreset.entries.forEach { PitakaChip(it.label, selected = preset == it, onClick = { onPreset(it) }) }
                    }
                }
            }
        }
        item(key = "name") {
            PitakaTextField(
                state = name, label = "Name", placeholder = "e.g. GCash",
                isError = nameProblem, supportingText = if (nameProblem) "Give the wallet a name" else null,
            )
        }
        item(key = "balance") {
            PitakaTextField(
                state = balance,
                label = if (isEdit) "Actual balance now" else "Starting balance",
                placeholder = "0.00",
                prefix = Peso.SIGN,
                isError = balanceProblem,
                supportingText = when {
                    balanceProblem -> "Enter a plain amount, like 1500 or 1500.50"
                    isEdit && current != null ->
                        "The app shows ${Peso.format(current.balanceCentavos)}. Type the real amount only if it is different."
                    else -> "How much is in it right now. Leave empty for ₱0.00."
                },
                inputTransformation = MoneyInput,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }
        if (isEdit && current != null) {
            item(key = "hide") {
                Column {
                    PitakaButton(
                        if (current.wallet.isArchived) "Show wallet again" else "Hide wallet",
                        onClick = onToggleHidden,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (hideBlocked) ProblemText("Keep at least one wallet in use")
                    Text(
                        "A hidden wallet keeps its history but is left out of totals and choices.",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.inkMuted,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                    )
                }
            }
        }
    }
}
