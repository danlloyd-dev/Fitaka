package com.pitakaview.app.data.db.dao

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import com.pitakaview.app.data.db.entity.TransactionEntity
import com.pitakaview.app.domain.TxPurpose
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** A ledger row with its category and wallet names. Shared by Recent, the month lists and search. */
private const val ENTRY_ROWS = """
    SELECT t.id AS id, t.purpose AS purpose, t.amountCentavos AS amountCentavos, t.occurredOn AS occurredOn,
        t.note AS note, c.name AS categoryName, fw.name AS fromWalletName, tw.name AS toWalletName
    FROM transactions t
    LEFT JOIN categories c ON c.id = t.categoryId
    LEFT JOIN wallets fw ON fw.id = t.fromWalletId
    LEFT JOIN wallets tw ON tw.id = t.toWalletId
"""

/** Writes go through LedgerRepository, which validates each row first. */
@Dao
interface TransactionDao {
    @Insert
    suspend fun insert(tx: TransactionEntity): Long

    @Update
    suspend fun update(tx: TransactionEntity)

    @Delete
    suspend fun delete(tx: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE occurredOn BETWEEN :start AND :end ORDER BY occurredOn DESC, id DESC")
    fun observeBetween(start: LocalDate, end: LocalDate): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE purpose = :purpose AND occurredOn BETWEEN :start AND :end
        ORDER BY occurredOn DESC, id DESC
        """,
    )
    fun observeByPurpose(purpose: TxPurpose, start: LocalDate, end: LocalDate): Flow<List<TransactionEntity>>

    /** Dashboard totals: one row per purpose that has activity in the range. */
    @Query(
        """
        SELECT purpose, SUM(amountCentavos) AS totalCentavos
        FROM transactions
        WHERE occurredOn BETWEEN :start AND :end
        GROUP BY purpose
        """,
    )
    fun observeTotalsByPurpose(start: LocalDate, end: LocalDate): Flow<List<PurposeTotal>>

    /** "Where my money goes": totals per category for one purpose (normally EXPENSE), biggest first. */
    @Query(
        """
        SELECT c.id AS categoryId, c.name AS name, c.iconKey AS iconKey, SUM(t.amountCentavos) AS totalCentavos
        FROM transactions t
        JOIN categories c ON c.id = t.categoryId
        WHERE t.purpose = :purpose AND t.occurredOn BETWEEN :start AND :end
        GROUP BY c.id, c.name, c.iconKey
        ORDER BY totalCentavos DESC
        """,
    )
    fun observeCategoryTotals(purpose: TxPurpose, start: LocalDate, end: LocalDate): Flow<List<CategoryTotal>>

    /** Monthly summary chart data. occurredOn is an epoch day, so ×86400 gives unix seconds. */
    @Query(
        """
        SELECT strftime('%Y-%m', occurredOn * 86400, 'unixepoch') AS month, purpose,
            SUM(amountCentavos) AS totalCentavos
        FROM transactions
        WHERE occurredOn BETWEEN :start AND :end
        GROUP BY month, purpose
        ORDER BY month
        """,
    )
    fun observeMonthlyTotals(start: LocalDate, end: LocalDate): Flow<List<MonthPurposeTotal>>

    /** Rows needed to work out baon left; BaonMath picks each baon's current period. */
    @Query(
        """
        SELECT baonId, purpose, occurredOn, amountCentavos
        FROM transactions
        WHERE baonId IS NOT NULL AND occurredOn >= :since
        """,
    )
    fun observeBaonActivity(since: LocalDate): Flow<List<BaonActivityRow>>

    /** Latest rows with their category and wallet names, for the Home "Recent" list. */
    @Query("$ENTRY_ROWS ORDER BY t.occurredOn DESC, t.id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<RecentTransaction>>

    /** One purpose's rows in a date range, newest first: the Gastos and Kita month lists. */
    @Query(
        """$ENTRY_ROWS
        WHERE t.purpose = :purpose AND t.occurredOn BETWEEN :start AND :end
        ORDER BY t.occurredOn DESC, t.id DESC
        """,
    )
    fun observeEntries(purpose: TxPurpose, start: LocalDate, end: LocalDate): Flow<List<RecentTransaction>>

    /**
     * Search across every entry, newest first: [pattern] (from SearchQuery.pattern) is matched against
     * the note, the category and the wallet names; [amountCentavos], when given, matches the amount exactly.
     */
    @Query(
        """$ENTRY_ROWS
        WHERE t.note LIKE :pattern ESCAPE '\'
            OR c.name LIKE :pattern ESCAPE '\'
            OR fw.name LIKE :pattern ESCAPE '\'
            OR tw.name LIKE :pattern ESCAPE '\'
            OR (:amountCentavos IS NOT NULL AND t.amountCentavos = :amountCentavos)
        ORDER BY t.occurredOn DESC, t.id DESC
        LIMIT :limit
        """,
    )
    fun search(pattern: String, amountCentavos: Long?, limit: Int): Flow<List<RecentTransaction>>

    /** When pay was received for each schedule: the income rows that carry a pay schedule. */
    @Query(
        """
        SELECT payScheduleId, occurredOn FROM transactions
        WHERE payScheduleId IS NOT NULL AND purpose = :income AND occurredOn >= :since
        """,
    )
    fun observePayReceipts(since: LocalDate, income: TxPurpose = TxPurpose.INCOME): Flow<List<PayReceipt>>

    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE externalRef = :externalRef)")
    suspend fun existsExternalRef(externalRef: String): Boolean
}
