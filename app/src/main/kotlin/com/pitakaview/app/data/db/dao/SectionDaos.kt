package com.pitakaview.app.data.db.dao

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import com.pitakaview.app.data.db.entity.BaonEntity
import com.pitakaview.app.data.db.entity.ImportBatchEntity
import com.pitakaview.app.data.db.entity.LoanEntity
import com.pitakaview.app.data.db.entity.PayScheduleEntity
import com.pitakaview.app.data.db.entity.SavingsGoalEntity
import com.pitakaview.app.domain.TxPurpose
import kotlinx.coroutines.flow.Flow

@Dao
interface PayScheduleDao {
    @Insert
    suspend fun insert(schedule: PayScheduleEntity): Long

    @Update
    suspend fun update(schedule: PayScheduleEntity)

    /** Income already logged for the schedule stays; those rows only lose their link (ON DELETE SET NULL). */
    @Delete
    suspend fun delete(schedule: PayScheduleEntity)

    @Query("SELECT * FROM pay_schedules WHERE id = :id")
    suspend fun get(id: Long): PayScheduleEntity?

    @Query("SELECT * FROM pay_schedules WHERE isActive = 1 ORDER BY id")
    fun observeActive(): Flow<List<PayScheduleEntity>>
}

@Dao
interface BaonDao {
    @Insert
    suspend fun insert(baon: BaonEntity): Long

    @Update
    suspend fun update(baon: BaonEntity)

    @Query("SELECT * FROM baon WHERE id = :id")
    suspend fun get(id: Long): BaonEntity?

    @Query("SELECT * FROM baon WHERE isActive = 1 ORDER BY id")
    fun observeActive(): Flow<List<BaonEntity>>

    /** Every baon, the stopped ones last: the Baon tab. */
    @Query("SELECT * FROM baon ORDER BY isActive DESC, id")
    fun observeAll(): Flow<List<BaonEntity>>

    @Query("SELECT * FROM baon WHERE id = :id")
    fun observe(id: Long): Flow<BaonEntity?>

    /** Refused by the database while entries still point at it. */
    @Delete
    suspend fun delete(baon: BaonEntity)
}

/** A goal with what is saved in it: deposits minus withdrawals. */
private const val GOAL_PROGRESS = """
    SELECT g.id AS goalId, g.name AS name, g.targetCentavos AS targetCentavos, g.targetDate AS targetDate,
        g.walletId AS walletId, g.isArchived AS isArchived,
        COALESCE(SUM(CASE
            WHEN t.purpose = :deposit THEN t.amountCentavos
            WHEN t.purpose = :withdrawal THEN -t.amountCentavos
            ELSE 0 END), 0) AS savedCentavos
    FROM savings_goals g
    LEFT JOIN transactions t ON t.goalId = g.id
"""

@Dao
interface SavingsGoalDao {
    @Insert
    suspend fun insert(goal: SavingsGoalEntity): Long

    @Update
    suspend fun update(goal: SavingsGoalEntity)

    @Query("SELECT * FROM savings_goals WHERE id = :id")
    suspend fun get(id: Long): SavingsGoalEntity?

    /** Refused by the database while entries still point at it. */
    @Delete
    suspend fun delete(goal: SavingsGoalEntity)

    /** The goals in use (hidden ones left out): Home. */
    @Query("$GOAL_PROGRESS WHERE g.isArchived = 0 GROUP BY g.id ORDER BY g.id")
    fun observeProgress(
        deposit: TxPurpose = TxPurpose.SAVINGS_DEPOSIT,
        withdrawal: TxPurpose = TxPurpose.SAVINGS_WITHDRAWAL,
    ): Flow<List<GoalProgress>>

    /** Every goal, the hidden ones last: the Ipon tab. */
    @Query("$GOAL_PROGRESS GROUP BY g.id ORDER BY g.isArchived, g.id")
    fun observeAllProgress(
        deposit: TxPurpose = TxPurpose.SAVINGS_DEPOSIT,
        withdrawal: TxPurpose = TxPurpose.SAVINGS_WITHDRAWAL,
    ): Flow<List<GoalProgress>>

    @Query("$GOAL_PROGRESS WHERE g.id = :id GROUP BY g.id")
    fun observeProgressOf(
        id: Long,
        deposit: TxPurpose = TxPurpose.SAVINGS_DEPOSIT,
        withdrawal: TxPurpose = TxPurpose.SAVINGS_WITHDRAWAL,
    ): Flow<GoalProgress?>

    /** What is saved in one goal: its deposits minus its withdrawals. One indexed read. */
    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN purpose = :deposit THEN amountCentavos WHEN purpose = :withdrawal THEN -amountCentavos ELSE 0 END), 0)
        FROM transactions WHERE goalId = :id
        """,
    )
    suspend fun savedIn(
        id: Long,
        deposit: TxPurpose = TxPurpose.SAVINGS_DEPOSIT,
        withdrawal: TxPurpose = TxPurpose.SAVINGS_WITHDRAWAL,
    ): Long
}

@Dao
interface LoanDao {
    @Insert
    suspend fun insert(loan: LoanEntity): Long

    @Update
    suspend fun update(loan: LoanEntity)

    @Query("SELECT * FROM loans WHERE id = :id")
    suspend fun get(id: Long): LoanEntity?

    /** Refused by the database while entries still point at it. */
    @Delete
    suspend fun delete(loan: LoanEntity)

    /** Every loan, settled ones too, with how much has been paid: the Utang tab. */
    @Query(
        """
        SELECT l.*,
            COALESCE((SELECT SUM(t.amountCentavos) FROM transactions t
                      WHERE t.loanId = l.id AND t.purpose = :payment), 0) AS paidCentavos
        FROM loans l
        ORDER BY l.id
        """,
    )
    fun observeAllWithPaid(payment: TxPurpose = TxPurpose.LOAN_PAYMENT): Flow<List<LoanWithPaid>>

    @Query(
        """
        SELECT l.*,
            COALESCE((SELECT SUM(t.amountCentavos) FROM transactions t
                      WHERE t.loanId = l.id AND t.purpose = :payment), 0) AS paidCentavos
        FROM loans l
        WHERE l.id = :id
        """,
    )
    fun observeWithPaid(id: Long, payment: TxPurpose = TxPurpose.LOAN_PAYMENT): Flow<LoanWithPaid?>

    /** Open loans, soonest due first (no due date last), with how much has been paid. */
    @Query(
        """
        SELECT l.*,
            COALESCE((SELECT SUM(t.amountCentavos) FROM transactions t
                      WHERE t.loanId = l.id AND t.purpose = :payment), 0) AS paidCentavos
        FROM loans l
        WHERE l.closedAt IS NULL
        ORDER BY l.dueDate IS NULL, l.dueDate, l.id
        """,
    )
    fun observeOpen(payment: TxPurpose = TxPurpose.LOAN_PAYMENT): Flow<List<LoanWithPaid>>
}

@Dao
interface ImportBatchDao {
    @Insert
    suspend fun insert(batch: ImportBatchEntity): Long

    /** Undo an import: its ledger rows go with it (ON DELETE CASCADE). */
    @Delete
    suspend fun delete(batch: ImportBatchEntity)

    @Query("SELECT * FROM import_batches WHERE id = :id")
    suspend fun get(id: Long): ImportBatchEntity?

    /** Past imports, newest first, each with its wallet's name and how many of its entries are still there. */
    @Query(
        """
        SELECT b.id, b.fileName, b.importedAt, w.name AS walletName,
               (SELECT COUNT(*) FROM transactions t WHERE t.importBatchId = b.id) AS entryCount
        FROM import_batches b JOIN wallets w ON w.id = b.walletId
        ORDER BY b.importedAt DESC, b.id DESC
        """,
    )
    fun observeAll(): Flow<List<ImportBatchRow>>
}
