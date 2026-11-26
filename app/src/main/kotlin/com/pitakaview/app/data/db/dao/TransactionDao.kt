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
    @Query(
        """
        SELECT t.id AS id, t.purpose AS purpose, t.amountCentavos AS amountCentavos, t.occurredOn AS occurredOn,
            t.note AS note, c.name AS categoryName, fw.name AS fromWalletName, tw.name AS toWalletName
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.categoryId
        LEFT JOIN wallets fw ON fw.id = t.fromWalletId
        LEFT JOIN wallets tw ON tw.id = t.toWalletId
        ORDER BY t.occurredOn DESC, t.id DESC
        LIMIT :limit
        """,
    )
    fun observeRecent(limit: Int): Flow<List<RecentTransaction>>

    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE externalRef = :externalRef)")
    suspend fun existsExternalRef(externalRef: String): Boolean
}
