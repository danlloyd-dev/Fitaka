package com.pitakaview.app.data.repo

import androidx.room3.withWriteTransaction
import com.pitakaview.app.data.db.FitakaDatabase
import com.pitakaview.app.data.db.entity.ImportBatchEntity
import com.pitakaview.app.data.db.entity.LoanEntity
import com.pitakaview.app.data.db.entity.TransactionEntity
import com.pitakaview.app.domain.LedgerRules
import com.pitakaview.app.domain.TxPurpose
import com.pitakaview.app.domain.importing.ImportPlanner
import com.pitakaview.app.domain.isOwnMove
import java.time.Instant
import java.time.LocalDate

/** The only write path into the ledger: every row is checked against LedgerRules before it is stored. */
class LedgerRepository(private val db: FitakaDatabase) {

    private val transactions = db.transactionDao()

    /**
     * Stores [tx] and returns its id. Throws IllegalArgumentException if the row breaks a ledger rule, or
     * [GoalBelowZeroException] if it takes more out of a savings goal than is in it.
     */
    suspend fun add(tx: TransactionEntity): Long {
        checkValid(tx)
        val goal = tx.goalId ?: return transactions.insert(tx)
        return db.withWriteTransaction { guardingGoals(listOf(goal)) { transactions.insert(tx) } }
    }

    /** Stores a change to [tx]. The same refusals as [add]. */
    suspend fun update(tx: TransactionEntity) {
        checkValid(tx)
        db.withWriteTransaction {
            // The goal it belonged to as well as the one it belongs to now. One read by id, on an edit.
            val goals = listOfNotNull(transactions.get(tx.id)?.goalId, tx.goalId)
            guardingGoals(goals) { transactions.update(tx) }
        }
    }

    /**
     * Removes [tx]. If it was half of a move between the owner's own wallets, its other half is spending or
     * income again. A deposit whose money has since been taken out of its goal is refused ([GoalBelowZeroException]).
     */
    suspend fun delete(tx: TransactionEntity) {
        if (!tx.purpose.isOwnMove && tx.goalId == null) {
            transactions.delete(tx)
            return
        }
        db.withWriteTransaction {
            guardingGoals(listOfNotNull(tx.goalId)) {
                transactions.delete(tx)
                if (tx.purpose.isOwnMove) revertOtherHalfOf(tx)
            }
        }
    }

    /**
     * Runs [change], inside the caller's write transaction, and refuses it (the transaction is rolled back)
     * if it leaves one of [goals] with less than nothing saved, lower than it was. Until 2026-11-27 a deposit
     * could be lowered or deleted after its money was taken out: the goal then showed less than nothing
     * saved, and the wallet the money was taken from was left short.
     *
     * "Lower than it was": a goal already below zero (made so before this rule) can still be put right, by
     * a deposit or by lowering a withdrawal, one step at a time. Two reads per goal.
     */
    private suspend fun <T> guardingGoals(goals: Collection<Long>, change: suspend () -> T): T {
        val dao = db.savingsGoalDao()
        val before = goals.distinct().associateWith { dao.savedIn(it) }
        val result = change()
        for ((goal, was) in before) {
            val now = dao.savedIn(goal)
            if (now < 0 && now < was) throw GoalBelowZeroException(goal)
        }
        return result
    }

    /**
     * Stores a change to [was], one half of a move between the owner's own wallets. If [tx] is no longer a
     * half (the owner said it was spending, or income, after all), its other half goes back with it, as one step.
     */
    suspend fun updateHalf(was: TransactionEntity, tx: TransactionEntity) {
        checkValid(tx)
        db.withWriteTransaction {
            transactions.update(tx)
            if (!tx.purpose.isOwnMove) revertOtherHalfOf(was)
        }
    }

    /**
     * The other half of [half] is the nearest entry of the opposite kind for the same amount in another
     * wallet, a day apart at most. It goes back to what it was before the two were paired: plain spending
     * or plain income, with the category it kept. Halves of one amount in one span of days are
     * interchangeable, so taking the nearest keeps the count of pairs right. Nothing happens when there is none.
     */
    private suspend fun revertOtherHalfOf(half: TransactionEntity) {
        val wallet = half.fromWalletId ?: half.toWalletId ?: return
        val leaving = half.purpose == TxPurpose.OWN_MOVE_IN // the other half is the money leaving
        val other = transactions.ownMovePartner(
            purpose = if (leaving) TxPurpose.OWN_MOVE_OUT else TxPurpose.OWN_MOVE_IN,
            amountCentavos = half.amountCentavos,
            day = half.occurredOn,
            from = half.occurredOn.minusDays(ImportPlanner.OWN_MOVE_DAYS),
            until = half.occurredOn.plusDays(ImportPlanner.OWN_MOVE_DAYS),
            walletId = wallet,
        ) ?: return
        transactions.update(other.copy(purpose = if (leaving) TxPurpose.EXPENSE else TxPurpose.INCOME))
    }

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
    suspend fun importBatch(batch: ImportBatchEntity, pairWith: List<Long> = emptyList(), rows: (batchId: Long) -> List<TransactionEntity>): Long =
        db.withWriteTransaction {
            val id = db.importBatchDao().insert(batch)
            rows(id).forEach { row ->
                checkValid(row)
                transactions.insert(row)
            }
            // [pairWith]: entries in other wallets that lines of this file are the other half of. Each
            // becomes the half it is (see TxPurpose.OWN_MOVE_OUT), in the same step as the file's own lines.
            pairWith.forEach { entryId ->
                val entry = transactions.get(entryId)
                val half = when (entry?.purpose) {
                    TxPurpose.EXPENSE -> TxPurpose.OWN_MOVE_OUT
                    TxPurpose.INCOME -> TxPurpose.OWN_MOVE_IN
                    // Changed or removed since the file was laid out for review: nothing of the file is stored.
                    else -> throw IllegalStateException("Entry $entryId is no longer plain spending or income")
                }
                transactions.update(entry.copy(purpose = half))
            }
            id
        }

    /** Takes an import back out: its record and every entry it added that is still there. */
    suspend fun undoImport(batchId: Long) {
        db.withWriteTransaction {
            val batch = db.importBatchDao().get(batchId)
            if (batch != null) {
                // The halves this file added go with it; their other halves, in other wallets, are spending
                // or income again, as they were before this file was brought in.
                val halves = transactions.ownMoveHalvesOf(batchId, TxPurpose.OWN_MOVE_OUT, TxPurpose.OWN_MOVE_IN)
                db.importBatchDao().delete(batch)
                halves.forEach { revertOtherHalfOf(it) }
            }
        }
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

    /**
     * Stores an edited loan and, as the same all-or-nothing step, carries a change of its amount or its day
     * over to the entry for the money that changed hands. They are one event: until 2026-11-27 only the loan
     * was changed, so correcting a typing slip in the amount left the wallet with the first amount.
     *
     * Only what was in step moves. An entry the owner set apart on purpose in its own form (less arrived
     * than was borrowed, or it arrived on another day) keeps its amount or its day. A loan from before the
     * app has no such entry, and none is made.
     *
     * One extra read for an edit that leaves amount and day alone; two reads and a write for one that changes them.
     */
    suspend fun updateLoan(loan: LoanEntity) = db.withWriteTransaction {
        val before = requireNotNull(db.loanDao().get(loan.id)) { "No loan ${loan.id}" }
        db.loanDao().update(loan)
        if (before.principalCentavos != loan.principalCentavos || before.startDate != loan.startDate) {
            val start = transactions.firstForLoan(loan.id, TxPurpose.LOAN_START)
            if (start != null) {
                val row = start.copy(
                    amountCentavos = if (start.amountCentavos == before.principalCentavos) loan.principalCentavos else start.amountCentavos,
                    occurredOn = if (start.occurredOn == before.startDate) loan.startDate else start.occurredOn,
                )
                if (row != start) {
                    val problems = LedgerRules.validate(row, loan.direction)
                    require(problems.isEmpty()) { "Invalid ${row.purpose} row: $problems" }
                    transactions.update(row)
                }
            }
        }
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

/** A change would take more out of savings goal [goalId] than was put into it. */
class GoalBelowZeroException(val goalId: Long) :
    IllegalArgumentException("more would be taken out of this savings goal than was put into it")
