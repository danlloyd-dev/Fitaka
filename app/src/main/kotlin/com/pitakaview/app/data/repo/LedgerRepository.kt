package com.pitakaview.app.data.repo

import androidx.room3.withWriteTransaction
import com.pitakaview.app.data.db.PitakaDatabase
import com.pitakaview.app.data.db.entity.TransactionEntity
import com.pitakaview.app.domain.LedgerRules
import com.pitakaview.app.domain.TxPurpose
import java.time.Instant
import java.time.LocalDate

/** The only write path into the ledger: every row is checked against LedgerRules before it is stored. */
class LedgerRepository(private val db: PitakaDatabase) {

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
