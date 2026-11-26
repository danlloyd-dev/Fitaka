package com.pitakaview.app.ui.importing

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pitakaview.app.data.db.PitakaDatabase
import com.pitakaview.app.data.db.dao.ImportBatchRow
import com.pitakaview.app.data.db.entity.CategoryEntity
import com.pitakaview.app.data.db.entity.ImportBatchEntity
import com.pitakaview.app.data.db.entity.WalletEntity
import com.pitakaview.app.data.repo.LedgerRepository
import com.pitakaview.app.domain.CategoryKind
import com.pitakaview.app.domain.ImportSource
import com.pitakaview.app.domain.importing.CsvStatementReader
import com.pitakaview.app.domain.importing.ImportCandidate
import com.pitakaview.app.domain.importing.ImportPlanner
import com.pitakaview.app.domain.importing.ImportStatus
import com.pitakaview.app.domain.importing.StatementRead
import com.pitakaview.app.domain.importing.StatementRow
import com.pitakaview.app.domain.importing.TextMask
import com.pitakaview.app.domain.importing.UnreadableReason
import com.pitakaview.app.importing.PdfText
import com.pitakaview.app.importing.PdfTextReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

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
        val candidates: List<ImportCandidate>,
        /** The owner said the file's amounts are money in, for a file that does not say which way. */
        val flipped: Boolean,
    ) : ImportStage {
        val picked: Int get() = candidates.count { it.picked && it.canPick }

        fun count(status: ImportStatus): Int = candidates.count { it.status == status }
    }

    /** A PDF that needs its password. */
    data class PdfLocked(val file: PickedFile, val wrongPassword: Boolean) : ImportStage

    /** A PDF was opened: how it is laid out, with every letter and digit hidden. */
    data class PdfLayout(val fileName: String, val pages: Int, val sample: String) : ImportStage
}

/** One line of news under the title. */
sealed interface ImportNotice {
    data class Imported(val count: Int, val walletName: String) : ImportNotice

    data class Undone(val fileName: String) : ImportNotice

    /** The file could not be used; nothing was changed. */
    data class Refused(val why: Refusal) : ImportNotice
}

enum class Refusal { TOO_BIG, EMPTY, NO_TABLE, MIXED_DATES, NOTHING_IN_IT, PDF_NEEDS_NEWER_ANDROID, PDF_UNREADABLE, COULD_NOT_SAVE }

class ImportViewModel(
    private val db: PitakaDatabase,
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

    /** The line whose category chips are open, if any. */
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

    fun selectWallet(id: Long) {
        if (id == walletId) return
        walletId = id
        // What is new and what is already there depends on the wallet, so the lines are checked again.
        (stage as? ImportStage.Review)?.let { review -> viewModelScope.launch { review(review.fileName, review.read, review.flipped) } }
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
                    is StatementRead.Rows -> if (read.rows.isEmpty()) refuse(Refusal.NOTHING_IN_IT) else review(file.name, read, flipped = false)
                }
            }
        }
    }

    private fun refuse(why: Refusal) {
        stage = ImportStage.Choose
        notice = ImportNotice.Refused(why)
    }

    /** Works out which lines are new for the chosen wallet. Two or three queries, whatever the size of the file. */
    private suspend fun review(fileName: String, read: StatementRead.Rows, flipped: Boolean) {
        val wallet = walletId ?: return
        val rows: List<StatementRow> = if (flipped) ImportPlanner.flipped(read.rows) else read.rows
        if (expenseCategories.isEmpty()) expenseCategories = db.categoryDao().observeActive(CategoryKind.EXPENSE).first()
        if (incomeCategories.isEmpty()) incomeCategories = db.categoryDao().observeActive(CategoryKind.INCOME).first()
        val refs = rows.mapNotNull { ImportPlanner.refFor(ImportSource.CSV, wallet, it) }
        val imported = if (refs.isEmpty()) emptySet() else ledger.knownRefs(refs)
        val existing = db.transactionDao().marksFor(wallet, rows.minOf { it.date }, rows.maxOf { it.date })
        openLine = null
        stage = ImportStage.Review(
            fileName, read,
            ImportPlanner.plan(rows, ImportSource.CSV, wallet, imported, existing, expenseCategories, incomeCategories),
            flipped,
        )
    }

    private fun changeLines(change: (List<ImportCandidate>) -> List<ImportCandidate>) {
        val review = stage as? ImportStage.Review ?: return
        stage = review.copy(candidates = change(review.candidates))
    }

    fun toggle(index: Int) = changeLines { lines ->
        lines.mapIndexed { i, line -> if (i == index && line.canPick) line.copy(picked = !line.picked) else line }
    }

    fun showCategories(index: Int) {
        openLine = if (openLine == index) null else index
    }

    fun setCategory(index: Int, categoryId: Long) {
        changeLines { lines -> lines.mapIndexed { i, line -> if (i == index) line.copy(categoryId = categoryId) else line } }
        openLine = null
    }

    /** For a file that does not say which way its money went: read every amount the other way round. */
    fun flip() {
        val review = stage as? ImportStage.Review ?: return
        if (review.read.directionKnown) return
        viewModelScope.launch { review(review.fileName, review.read, !review.flipped) }
    }

    fun cancel() {
        stage = ImportStage.Choose
        openLine = null
    }

    /** Stores the ticked lines and the record of this file as one step. */
    fun importPicked() {
        val review = stage as? ImportStage.Review ?: return
        val wallet = walletId ?: return
        if (review.picked == 0) return
        viewModelScope.launch {
            val at = now()
            try {
                ledger.importBatch(
                    ImportBatchEntity(source = ImportSource.CSV, walletId = wallet, fileName = review.fileName, rowCount = review.picked, importedAt = at),
                ) { batchId -> ImportPlanner.toTransactions(review.candidates, wallet, batchId, at) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // All or nothing: the write was rolled back, so nothing of this file is in the ledger.
                Log.e(TAG, "The import could not be saved", e)
                notice = ImportNotice.Refused(Refusal.COULD_NOT_SAVE)
                return@launch
            }
            notice = ImportNotice.Imported(review.picked, wallets.value.firstOrNull { it.id == wallet }?.name.orEmpty())
            stage = ImportStage.Choose
            openLine = null
        }
    }

    fun undo(batch: ImportBatchRow) {
        viewModelScope.launch {
            ledger.undoImport(batch.id)
            notice = ImportNotice.Undone(batch.fileName)
        }
    }

    // ---------- PDF statements: not brought in yet; shown as a layout with everything hidden ----------

    private suspend fun openPdf(file: PickedFile, password: String?) {
        when (val text = pdf.read(file.bytes, password)) {
            is PdfText.Pages -> stage = ImportStage.PdfLayout(file.name, text.pages.size, TextMask.sample(text.pages))
            PdfText.NeedsPassword -> stage = ImportStage.PdfLocked(file, wrongPassword = false)
            PdfText.WrongPassword -> stage = ImportStage.PdfLocked(file, wrongPassword = true)
            PdfText.NeedsNewerAndroid -> refuse(Refusal.PDF_NEEDS_NEWER_ANDROID)
            PdfText.Unreadable -> refuse(Refusal.PDF_UNREADABLE)
        }
    }

    fun unlockPdf(password: String) {
        val locked = stage as? ImportStage.PdfLocked ?: return
        if (password.isEmpty()) return
        viewModelScope.launch { openPdf(locked.file, password) }
    }

    companion object {
        private const val TAG = "PitakaImport"

        /** Statements are small; anything bigger than this is not one, and is not read into memory. */
        const val MAX_BYTES = 5 * 1024 * 1024
    }
}
