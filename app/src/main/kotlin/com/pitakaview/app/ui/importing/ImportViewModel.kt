package com.pitakaview.app.ui.importing

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pitakaview.app.data.db.FitakaDatabase
import com.pitakaview.app.data.db.dao.ImportBatchRow
import com.pitakaview.app.data.db.entity.CategoryEntity
import com.pitakaview.app.data.db.entity.ImportBatchEntity
import com.pitakaview.app.data.db.entity.WalletEntity
import com.pitakaview.app.data.repo.LedgerRepository
import com.pitakaview.app.domain.CategoryKind
import com.pitakaview.app.domain.ImportSource
import com.pitakaview.app.domain.importing.CsvStatementReader
import com.pitakaview.app.domain.importing.DirectionSolver
import com.pitakaview.app.domain.importing.ImportCandidate
import com.pitakaview.app.domain.importing.ImportPlanner
import com.pitakaview.app.domain.importing.ImportStatus
import com.pitakaview.app.domain.importing.PdfStatementReader
import com.pitakaview.app.domain.importing.StatementRead
import com.pitakaview.app.domain.importing.StatementRow
import com.pitakaview.app.domain.importing.StatementTotals
import com.pitakaview.app.domain.importing.StatementWallet
import com.pitakaview.app.domain.importing.TextMask
import com.pitakaview.app.domain.importing.UnreadableReason
import com.pitakaview.app.importing.PdfText
import com.pitakaview.app.importing.PdfTextReader
import com.pitakaview.app.ui.OneAtATime
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A file the owner picked: what it is called and what is in it. */
class PickedFile(val name: String, val bytes: ByteArray) {
    val isPdf: Boolean
        get() = name.endsWith(".pdf", ignoreCase = true) ||
            (bytes.size >= 4 && bytes[0] == '%'.code.toByte() && bytes[1] == 'P'.code.toByte() && bytes[2] == 'D'.code.toByte() && bytes[3] == 'F'.code.toByte())
}

/** Where the Import screen is. */
sealed interface ImportStage {
    /** Picking a wallet and a file; past imports are listed. */
    data object Choose : ImportStage

    /** A table was read: its lines, each ticked or not, waiting for the owner's yes. */
    data class Review(
        val fileName: String,
        val read: StatementRead.Rows,
        /** The lines as they stand now, with any direction the owner has set. */
        val candidates: List<ImportCandidate>,
        /** The owner said the file's amounts are money in, for a file that does not say which way. */
        val flipped: Boolean,
    ) : ImportStage {
        val picked: Int get() = candidates.count { it.picked && it.canPick }

        fun count(status: ImportStatus): Int = candidates.count { it.status == status }

        /** What every line that was read adds up to, each way (ticked or not: the statement's totals cover them all). */
        val sums: StatementTotals
            get() = StatementTotals(
                moneyInCentavos = candidates.filter { it.row.moneyIn }.sumOf { it.row.amountCentavos },
                moneyOutCentavos = candidates.filterNot { it.row.moneyIn }.sumOf { it.row.amountCentavos },
            )

        /** Whether the lines add up to the statement's own totals; null when the file gives none to check against. */
        val addsUp: Boolean? get() = read.totals?.let { it == sums }

        /** Lines whose direction is still only a guess. */
        val unsure: Int get() = candidates.count { !it.row.sure }

        /**
         * Nothing is imported from a statement whose lines do not add up to its own totals, or while a
         * line's direction is still a guess: a wrong direction would put the wallet's balance out by
         * twice the amount.
         */
        val canImport: Boolean get() = picked > 0 && addsUp != false && unsure == 0
    }

    /** A PDF that needs its password. */
    data class PdfLocked(val file: PickedFile, val wrongPassword: Boolean) : ImportStage

    /** A PDF from a bank this app cannot read yet: how it is laid out, with every letter and digit hidden. */
    data class PdfLayout(val fileName: String, val pages: Int, val sample: String) : ImportStage
}

/** One line of news under the title. */
sealed interface ImportNotice {
    data class Imported(val count: Int, val walletName: String) : ImportNotice

    data class Undone(val fileName: String) : ImportNotice

    /** The file could not be used; nothing was changed. */
    data class Refused(val why: Refusal) : ImportNotice
}

enum class Refusal { TOO_BIG, EMPTY, NO_TABLE, MIXED_DATES, NOTHING_IN_IT, PDF_NEEDS_NEWER_ANDROID, PDF_UNREADABLE, COULD_NOT_SAVE, NO_CATEGORY_IN_USE }

class ImportViewModel(
    private val db: FitakaDatabase,
    private val ledger: LedgerRepository,
    private val pdf: PdfTextReader,
    private val now: () -> Instant = Instant::now,
) : ViewModel() {
    var stage by mutableStateOf<ImportStage>(ImportStage.Choose)
        private set
    var notice by mutableStateOf<ImportNotice?>(null)
        private set
    var walletId by mutableStateOf<Long?>(null)
        private set
    var loaded by mutableStateOf(false)
        private set

    /** The line whose choices (which way, which category) are open, if any. */
    var openLine by mutableStateOf<Int?>(null)
        private set

    val wallets: StateFlow<List<WalletEntity>> = db.walletDao().observeActiveWithBalance()
        .map { rows -> rows.map { it.wallet } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val batches: StateFlow<List<ImportBatchRow>> = db.importBatchDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var expenseCategories by mutableStateOf(emptyList<CategoryEntity>())
        private set
    var incomeCategories by mutableStateOf(emptyList<CategoryEntity>())
        private set

    init {
        viewModelScope.launch {
            walletId = db.walletDao().observeActiveWithBalance().first().firstOrNull()?.wallet?.id
            loaded = true
        }
    }

    /** The owner tapped a wallet themselves; from then on the app does not choose one for them. */
    private var walletPickedByOwner = false

    fun selectWallet(id: Long) {
        walletPickedByOwner = true
        if (id == walletId) return
        walletId = id
        // What is new and what is already there depends on the wallet, so the lines are checked again.
        (stage as? ImportStage.Review)?.let { review ->
            viewModelScope.launch { plan(review.fileName, review.read, review.candidates.map { it.row }, review.flipped, keep = review.candidates) }
        }
    }

    /** A file was picked. */
    fun open(file: PickedFile) {
        notice = null
        viewModelScope.launch {
            when {
                file.bytes.size > MAX_BYTES -> refuse(Refusal.TOO_BIG)
                file.isPdf -> openPdf(file, password = null)
                else -> when (val read = CsvStatementReader.read(file.bytes.decodeToString())) {
                    is StatementRead.Unreadable -> refuse(
                        when (read.reason) {
                            UnreadableReason.EMPTY -> Refusal.EMPTY
                            UnreadableReason.NO_HEADER -> Refusal.NO_TABLE
                            UnreadableReason.MIXED_DATES -> Refusal.MIXED_DATES
                        },
                    )
                    is StatementRead.Rows -> if (read.rows.isEmpty()) refuse(Refusal.NOTHING_IN_IT) else plan(file.name, read, read.rows, flipped = false)
                }
            }
        }
    }

    private fun refuse(why: Refusal) {
        stage = ImportStage.Choose
        notice = ImportNotice.Refused(why)
    }

    /**
     * Works out which of [rows] are new for the chosen wallet. Two or three queries, whatever the size
     * of the file. Ticks and categories the owner already set ([keep]) stay on the lines that did not change.
     */
    private suspend fun plan(fileName: String, read: StatementRead.Rows, rows: List<StatementRow>, flipped: Boolean, keep: List<ImportCandidate> = emptyList()) {
        val wallet = walletId ?: return
        if (expenseCategories.isEmpty()) expenseCategories = db.categoryDao().observeActive(CategoryKind.EXPENSE).first()
        if (incomeCategories.isEmpty()) incomeCategories = db.categoryDao().observeActive(CategoryKind.INCOME).first()
        // Every line gets a category of its kind. The category screen now keeps one of each kind in use;
        // data from before that (2026-11-27) may have none, and guessing from an empty list stopped the app.
        if (expenseCategories.isEmpty() || incomeCategories.isEmpty()) {
            refuse(Refusal.NO_CATEGORY_IN_USE)
            return
        }
        // A line the file does not settle (its direction is a guess, or the file does not say at all which
        // way its money went) is asked for both ways: if it was brought in before, the owner settled it
        // then, and the ledger knows which way it went (see ImportPlanner.settledByLedger). Still one query.
        val open = { row: StatementRow -> !row.sure || !read.directionKnown }
        fun ref(row: StatementRow, moneyIn: Boolean) = ImportPlanner.refFor(read.source, wallet, row.copy(moneyIn = moneyIn))
        val refs = rows.flatMap { row -> if (open(row)) listOfNotNull(ref(row, true), ref(row, false)) else listOfNotNull(ref(row, row.moneyIn)) }
        val imported = if (refs.isEmpty()) emptySet() else ledger.knownRefs(refs)
        val existing = db.transactionDao().marksFor(wallet, rows.minOf { it.date }, rows.maxOf { it.date })
        val settled = settleFromTotals(ImportPlanner.settledByLedger(rows, open, read.source, wallet, imported, existing), read.totals)
        // Only a file that names wallets (the app's own export) needs the wallet's name: one small read then.
        val walletName = if (rows.any { it.walletName != null }) db.walletDao().get(wallet)?.name else null
        val fresh = ImportPlanner.plan(settled, read.source, wallet, imported, existing, expenseCategories, incomeCategories, walletName)
        stage = ImportStage.Review(
            fileName, read,
            fresh.mapIndexed { i, line ->
                val before = keep.getOrNull(i)
                if (before != null && before.row == line.row && before.status == line.status) line.copy(picked = before.picked, categoryId = before.categoryId) else line
            },
            flipped,
        )
    }

    /**
     * With the lines that are settled taken as facts, tries the statement's totals again for the lines
     * still open: one line settled may settle others. When the totals cannot be reached with those
     * facts, the lines are left as they are, and the screen says they do not add up.
     */
    private fun settleFromTotals(rows: List<StatementRow>, totals: StatementTotals?): List<StatementRow> {
        if (totals == null || rows.all { it.sure }) return rows
        val solved = DirectionSolver.solve(rows.map { it.amountCentavos }, rows.map { if (it.sure) it.moneyIn else null }, totals) ?: return rows
        return rows.mapIndexed { i, row -> if (row.sure) row else row.copy(moneyIn = solved[i].moneyIn, sure = solved[i].sure) }
    }

    private fun changeLines(change: (List<ImportCandidate>) -> List<ImportCandidate>) {
        val review = stage as? ImportStage.Review ?: return
        stage = review.copy(candidates = change(review.candidates))
    }

    fun toggle(index: Int) = changeLines { lines ->
        lines.mapIndexed { i, line -> if (i == index && line.canPick) line.copy(picked = !line.picked) else line }
    }

    fun showChoices(index: Int) {
        openLine = if (openLine == index) null else index
    }

    fun setCategory(index: Int, categoryId: Long) {
        changeLines { lines -> lines.mapIndexed { i, line -> if (i == index) line.copy(categoryId = categoryId) else line } }
        openLine = null
    }

    /**
     * The owner says which way one line went. That settles the line, and the lines are checked again:
     * money in and money out are compared with different entries and take different categories.
     */
    fun setDirection(index: Int, moneyIn: Boolean) {
        val review = stage as? ImportStage.Review ?: return
        val rows = review.candidates.mapIndexed { i, line -> if (i == index) line.row.copy(moneyIn = moneyIn, sure = true) else line.row }
        // The plan settles whatever now follows from the statement's totals.
        viewModelScope.launch { plan(review.fileName, review.read, rows, review.flipped, keep = review.candidates) }
    }

    /** For a file that does not say which way its money went: read every amount the other way round. */
    fun flip() {
        val review = stage as? ImportStage.Review ?: return
        if (review.read.directionKnown) return
        val flipped = !review.flipped
        viewModelScope.launch { plan(review.fileName, review.read, if (flipped) ImportPlanner.flipped(review.read.rows) else review.read.rows, flipped) }
    }

    fun cancel() {
        stage = ImportStage.Choose
        openLine = null
    }

    private val importing = OneAtATime()

    /** Stores the ticked lines and the record of this file as one step. */
    fun importPicked() {
        val review = stage as? ImportStage.Review ?: return
        val wallet = walletId ?: return
        if (!review.canImport) return
        // One write at a time: a second tap while the file is being stored would store it again.
        importing.launch(viewModelScope) {
            val at = now()
            try {
                ledger.importBatch(
                    ImportBatchEntity(source = review.read.source, walletId = wallet, fileName = review.fileName, rowCount = review.picked, importedAt = at),
                ) { batchId -> ImportPlanner.toTransactions(review.candidates, wallet, batchId, at) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // All or nothing: the write was rolled back, so nothing of this file is in the ledger.
                Log.e(TAG, "The import could not be saved", e)
                notice = ImportNotice.Refused(Refusal.COULD_NOT_SAVE)
                return@launch false
            }
            notice = ImportNotice.Imported(review.picked, wallets.value.firstOrNull { it.id == wallet }?.name.orEmpty())
            stage = ImportStage.Choose
            openLine = null
            // The review is gone, so nothing is left to tap twice; another file can be brought in.
            false
        }
    }

    fun undo(batch: ImportBatchRow) {
        viewModelScope.launch {
            ledger.undoImport(batch.id)
            notice = ImportNotice.Undone(batch.fileName)
        }
    }

    // ---------- PDF statements ----------

    private suspend fun openPdf(file: PickedFile, password: String?) {
        when (val text = pdf.read(file.bytes, password)) {
            is PdfText.Pages -> when (val read = PdfStatementReader.read(text.pages)) {
                is StatementRead.Rows -> if (read.rows.isEmpty()) {
                    refuse(Refusal.NOTHING_IN_IT)
                } else {
                    preferWalletNamedFor(read.source)
                    plan(file.name, read, read.rows, flipped = false)
                }
                // Not a bank this app can read yet: show how the file is laid out, with everything hidden,
                // which is what is needed to add that bank.
                is StatementRead.Unreadable -> stage = ImportStage.PdfLayout(file.name, text.pages.size, TextMask.sample(text.pages))
            }
            PdfText.NeedsPassword -> stage = ImportStage.PdfLocked(file, wrongPassword = false)
            PdfText.WrongPassword -> stage = ImportStage.PdfLocked(file, wrongPassword = true)
            PdfText.NeedsNewerAndroid -> refuse(Refusal.PDF_NEEDS_NEWER_ANDROID)
            PdfText.Unreadable -> refuse(Refusal.PDF_UNREADABLE)
        }
    }

    /**
     * A bank's own statement says which bank it is from. Unless the owner has chosen a wallet themselves,
     * it goes to the one wallet named after that bank, not to whichever wallet happens to be first.
     */
    private suspend fun preferWalletNamedFor(source: ImportSource) {
        if (walletPickedByOwner) return
        val bank = StatementWallet.bankName(source) ?: return
        val active = db.walletDao().observeActiveWithBalance().first().map { it.wallet }
        StatementWallet.only(bank, active)?.let { walletId = it.id }
    }

    fun unlockPdf(password: String) {
        val locked = stage as? ImportStage.PdfLocked ?: return
        if (password.isEmpty()) return
        viewModelScope.launch { openPdf(locked.file, password) }
    }

    companion object {
        private const val TAG = "FitakaImport"

        /** Statements are small; anything bigger than this is not one, and is not read into memory. */
        const val MAX_BYTES = 5 * 1024 * 1024
    }
}
