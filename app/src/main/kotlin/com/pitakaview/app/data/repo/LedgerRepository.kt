package com.pitakaview.app.data.repo

import androidx.room3.withWriteTransaction
import com.pitakaview.app.data.db.FitakaDatabase
import com.pitakaview.app.data.db.entity.ImportBatchEntity
import com.pitakaview.app.data.db.entity.LoanEntity
import com.pitakaview.app.data.db.entity.TransactionEntity
import com.pitakaview.app.domain.LedgerRules
import com.pitakaview.app.domain.TxPurpose
import java.time.Instant
import java.time.LocalDate

/** The only write path into the ledger: every row is checked against LedgerRules before it is stored. */
class LedgerRepository(private val db: FitakaDatabase) {

    private val transactions = db.transactionDao()

    /** Stores [tx] and returns its id. Throws IllegalArgumentException if the row breaks a ledger rule. */
    suspend fun add(tx: TransactionEntity): Long {
        checkValid(tx)
        return transactions.insert(tx)
    }

    suspend fun update(tx: TransactionEntity) {
        checkValid(tx)
        transactions.update(tx)
    }

    suspend fun delete(tx: TransactionEntity) = transactions.delete(tx)

    /**
     * Stores several rows as one all-or-nothing step: if any row is refused, none of them are kept.
     * Used wherever one action writes more than one row (a new loan plus its first entry, an import).
     */
    suspend fun addAll(rows: List<TransactionEntity>): List<Long> {
        rows.forEach { checkValid(it) }
        return db.withWriteTransaction { rows.map { transactions.insert(it) } }
    }

    /**
     * Stores one imported file: its record and every entry it adds, as one all-or-nothing step, so a
     * file is never half imported. [rows] is given the new record's id so each entry can carry it.
     * Returns that id.
     */
    suspend fun importBatch(batch: ImportBatchEntity, rows: (batchId: Long) -> List<TransactionEntity>): Long =
        db.withWriteTransaction {
            val id = db.importBatchDao().insert(batch)
            rows(id).forEach { row ->
                checkValid(row)
                transactions.insert(row)
            }
            id
        }

    /** Takes an import back out: its record and every entry it added that is still there. */
    suspend fun undoImport(batchId: Long) {
        val batch = db.importBatchDao().get(batchId) ?: return
        db.importBatchDao().delete(batch)
    }

    /** Which of [refs] were imported before. Asked in chunks, to stay under SQLite's limit on values in one query. */
    suspend fun knownRefs(refs: Collection<String>): Set<String> =
        refs.chunked(500).flatMapTo(HashSet()) { transactions.knownRefs(it) }

    /**
     * Stores a new loan and, when [startRow] gives one, the entry for the money changing hands, as one
     * all-or-nothing step: a loan never ends up saved without the entry that moved the money.
     */
    suspend fun addLoan(loan: LoanEntity, startRow: (loanId: Long) -> TransactionEntity?): Long =
        db.withWriteTransaction {
            val id = db.loanDao().insert(loan)
            startRow(id)?.let { row ->
                val problems = LedgerRules.validate(row, loan.direction)
                require(problems.isEmpty()) { "Invalid ${row.purpose} row: $problems" }
                transactions.insert(row)
            }
            id
        }

    /** Puts money toward a goal: into the goal's wallet, or earmarked in [fromWalletId] if the goal has none. */
    suspend fun depositToGoal(
        goalId: Long,
        fromWalletId: Long,
        amountCentavos: Long,
        on: LocalDate,
        now: Instant,
        note: String? = null,
    ): Long {
        val goal = requireNotNull(db.savingsGoalDao().get(goalId)) { "No savings goal $goalId" }
        return add(
            TransactionEntity(
                purpose = TxPurpose.SAVINGS_DEPOSIT,
                amountCentavos = amountCentavos,
                occurredOn = on,
                fromWalletId = fromWalletId,
                toWalletId = goal.walletId ?: fromWalletId,
                goalId = goalId,
                note = note,
                createdAt = now,
            ),
        )
    }

    /** Takes money out of a goal into [toWalletId]. */
    suspend fun withdrawFromGoal(
        goalId: Long,
        toWalletId: Long,
        amountCentavos: Long,
        on: LocalDate,
        now: Instant,
        note: String? = null,
    ): Long {
        val goal = requireNotNull(db.savingsGoalDao().get(goalId)) { "No savings goal $goalId" }
        return add(
            TransactionEntity(
                purpose = TxPurpose.SAVINGS_WITHDRAWAL,
                amountCentavos = amountCentavos,
                occurredOn = on,
                fromWalletId = goal.walletId ?: toWalletId,
                toWalletId = toWalletId,
                goalId = goalId,
                note = note,
                createdAt = now,
            ),
        )
    }

    private suspend fun checkValid(tx: TransactionEntity) {
        val loanDirection = tx.loanId?.let { db.loanDao().get(it)?.direction }
        val problems = LedgerRules.validate(tx, loanDirection)
        require(problems.isEmpty()) { "Invalid ${tx.purpose} row: $problems" }
    }
}
