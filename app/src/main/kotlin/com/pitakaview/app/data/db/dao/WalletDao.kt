package com.pitakaview.app.data.db.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import com.pitakaview.app.data.db.entity.WalletEntity
import kotlinx.coroutines.flow.Flow

/**
 * Balance = opening + money in − money out. Two indexed sub-sums per wallet; a row naming the same
 * wallet on both sides (earmarked savings) adds and subtracts the same amount. One definition, used
 * by every wallet query, so no two screens can compute a balance differently.
 */
private const val BALANCE_SQL = """
    w.openingBalanceCentavos
    + COALESCE((SELECT SUM(t.amountCentavos) FROM transactions t WHERE t.toWalletId = w.id), 0)
    - COALESCE((SELECT SUM(t.amountCentavos) FROM transactions t WHERE t.fromWalletId = w.id), 0)
"""

@Dao
interface WalletDao {
    @Insert
    suspend fun insert(wallet: WalletEntity): Long

    @Update
    suspend fun update(wallet: WalletEntity)

    @Query("SELECT * FROM wallets WHERE id = :id")
    suspend fun get(id: Long): WalletEntity?

    @Query("SELECT COUNT(*) FROM wallets")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM wallets WHERE isArchived = 0")
    suspend fun countActive(): Int

    /** Wallets in use, for totals and for picking a wallet. */
    @Query("SELECT w.*, $BALANCE_SQL AS balanceCentavos FROM wallets w WHERE w.isArchived = 0 ORDER BY w.sortOrder, w.id")
    fun observeActiveWithBalance(): Flow<List<WalletWithBalance>>

    /** Every wallet, hidden ones last, for the wallet management screen. */
    @Query("SELECT w.*, $BALANCE_SQL AS balanceCentavos FROM wallets w ORDER BY w.isArchived, w.sortOrder, w.id")
    fun observeAllWithBalance(): Flow<List<WalletWithBalance>>
}
