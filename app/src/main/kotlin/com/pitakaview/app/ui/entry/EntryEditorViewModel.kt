package com.pitakaview.app.ui.entry

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pitakaview.app.data.db.FitakaDatabase
import com.pitakaview.app.data.db.entity.BaonEntity
import com.pitakaview.app.data.db.entity.CategoryEntity
import com.pitakaview.app.data.db.entity.TransactionEntity
import com.pitakaview.app.data.db.entity.WalletEntity
import com.pitakaview.app.data.prefs.SettingsRepository
import com.pitakaview.app.data.repo.LedgerRepository
import com.pitakaview.app.domain.BaonDirection
import com.pitakaview.app.domain.CategoryKind
import com.pitakaview.app.domain.EntryForm
import com.pitakaview.app.domain.EntryFormLogic
import com.pitakaview.app.domain.EntryKind
import com.pitakaview.app.domain.EntryProblem
import com.pitakaview.app.ui.OneAtATime
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Quick entry: holds what is being typed, checks it with [EntryFormLogic], and saves through the
 * ledger. [transactionId] null means a new entry; otherwise that row is loaded for editing.
 */
class EntryEditorViewModel(
    private val db: FitakaDatabase,
    private val ledger: LedgerRepository,
    private val settings: SettingsRepository,
    today: LocalDate,
    transactionId: Long?,
    startKind: EntryKind,
    payScheduleId: Long? = null,
    startBaonId: Long? = null,
    private val now: () -> Instant = Instant::now,
) : ViewModel() {

    val isEdit = transactionId != null
    val amount = TextFieldState()
    val note = TextFieldState()

    var kind by mutableStateOf(startKind)
        private set
    var categoryId by mutableStateOf<Long?>(null)
        private set
    var fromWalletId by mutableStateOf<Long?>(null)
        private set
    var toWalletId by mutableStateOf<Long?>(null)
        private set
    var date by mutableStateOf(today)
        private set

    /** Shown only after a Save attempt, so the form does not nag while you are still typing. */
    var problems by mutableStateOf(emptySet<EntryProblem>())
        private set
    var saveError by mutableStateOf<String?>(null)
        private set

    /** False when the row belongs to another section (loan, baon, savings) and is edited there. */
    var editable by mutableStateOf(true)
        private set
    var loaded by mutableStateOf(false)
        private set
    var done by mutableStateOf(false)
        private set
    private val saving = OneAtATime()

    private var existing: TransactionEntity? = null

    /** The pay schedule this entry is the pay received for, if any. Saved with an income entry. */
    private var linkedScheduleId: Long? = null

    /** That schedule's name, for the line under the title. */
    var paydayName by mutableStateOf<String?>(null)
        private set

    /** Opened from a payday's "Received" button: it is income, so the kind is not offered. */
    val kindLocked = payScheduleId != null && transactionId == null

    /** The baon this spending is paid out of, if any. It then counts against what is left of that baon. */
    var baonId by mutableStateOf<Long?>(null)
        private set

    /** The baon the owner receives and has not stopped: the ones spending can be charged to. */
    val baons: StateFlow<List<BaonEntity>> = db.baonDao().observeActive()
        .map { rows -> rows.filter { it.direction == BaonDirection.RECEIVE } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val wallets: StateFlow<List<WalletEntity>> = db.walletDao().observeActiveWithBalance()
        .map { rows -> rows.map { it.wallet } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val expenseCategories: StateFlow<List<CategoryEntity>> = db.categoryDao().observeActive(CategoryKind.EXPENSE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val incomeCategories: StateFlow<List<CategoryEntity>> = db.categoryDao().observeActive(CategoryKind.INCOME)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val tx = transactionId?.let { db.transactionDao().get(it) }
            if (tx != null) {
                existing = tx
                val form = EntryFormLogic.fromTransaction(tx)
                if (form == null) {
                    editable = false
                } else {
                    kind = form.kind
                    amount.setTextAndPlaceCursorAtEnd(form.amountText)
                    note.setTextAndPlaceCursorAtEnd(form.note)
                    categoryId = form.categoryId
                    fromWalletId = form.fromWalletId
                    toWalletId = form.toWalletId
                    date = form.date
                    linkedScheduleId = form.payScheduleId
                    baonId = form.baonId
                    paydayName = form.payScheduleId?.let { db.payScheduleDao().get(it) }?.name
                }
            } else {
                // Offer the wallet used last time; fall back to the first wallet.
                val active = db.walletDao().observeActiveWithBalance().first().map { it.wallet.id }
                val preferred = settings.lastWalletId.first()?.takeIf { it in active } ?: active.firstOrNull()
                fromWalletId = preferred
                toWalletId = if (startKind == EntryKind.TRANSFER) active.firstOrNull { it != preferred } else preferred
                // Pay received for a payday: start from what the schedule expects. Every part can still be changed.
                payScheduleId?.let { db.payScheduleDao().get(it) }?.let { schedule ->
                    kind = EntryKind.INCOME
                    amount.setTextAndPlaceCursorAtEnd(EntryFormLogic.plainAmount(schedule.expectedAmountCentavos))
                    categoryId = schedule.categoryId
                    if (schedule.walletId in active) toWalletId = schedule.walletId
                    linkedScheduleId = schedule.id
                    paydayName = schedule.name
                }
                // Opened from a baon's "Spend" button: charge it to that baon and offer its usual wallet.
                startBaonId?.let { db.baonDao().observe(it).first() }?.let { baon ->
                    baonId = baon.id
                    baon.walletId?.takeIf { it in active }?.let { fromWalletId = it }
                }
            }
            loaded = true
        }
    }

    fun selectKind(newKind: EntryKind) {
        if (newKind == kind) return
        val current = if (kind == EntryKind.INCOME) toWalletId else fromWalletId
        kind = newKind
        categoryId = null // Gastos and Kita have different categories
        when (newKind) {
            EntryKind.EXPENSE -> fromWalletId = current
            EntryKind.INCOME -> toWalletId = current
            EntryKind.TRANSFER -> {
                fromWalletId = current
                toWalletId = wallets.value.firstOrNull { it.id != current }?.id
            }
        }
        problems = emptySet()
    }

    fun selectCategory(id: Long) {
        categoryId = id
    }

    fun selectFromWallet(id: Long) {
        fromWalletId = id
    }

    fun selectToWallet(id: Long) {
        toWalletId = id
    }

    fun selectDate(newDate: LocalDate) {
        date = newDate
    }

    /** [id] null means the spending is not paid out of a baon. */
    fun selectBaon(id: Long?) {
        baonId = id
    }

    fun save() {
        val form = EntryForm(
            kind = kind, amountText = amount.text.toString(), categoryId = categoryId,
            fromWalletId = fromWalletId, toWalletId = toWalletId, date = date, note = note.text.toString(),
            payScheduleId = linkedScheduleId,
            baonId = if (kind == EntryKind.EXPENSE) baonId else null,
        )
        problems = EntryFormLogic.problems(form)
        if (problems.isNotEmpty()) return
        saving.launch(viewModelScope) {
            val tx = EntryFormLogic.toTransaction(form, existing, now())
            try {
                if (existing == null) ledger.add(tx) else ledger.update(tx)
            } catch (e: IllegalArgumentException) {
                // The ledger refused the row. Say so instead of closing as if it had been saved.
                saveError = "This entry could not be saved: ${e.message}"
                return@launch false
            }
            (tx.fromWalletId ?: tx.toWalletId)?.let { settings.setLastWalletId(it) }
            done = true
            true
        }
    }

    fun delete() {
        val tx = existing ?: return
        viewModelScope.launch {
            ledger.delete(tx)
            done = true
        }
    }
}
