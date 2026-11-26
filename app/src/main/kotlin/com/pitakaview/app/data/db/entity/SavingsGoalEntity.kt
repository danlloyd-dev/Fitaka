package com.pitakaview.app.data.db.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/**
 * Ipon: a savings goal. Its saved amount is Σ deposits − Σ withdrawals from the ledger.
 * If [walletId] is set, deposits move money into that wallet; if null, deposits only earmark
 * money inside the wallet it came from.
 */
@Entity(
    tableName = "savings_goals",
    foreignKeys = [
        ForeignKey(entity = WalletEntity::class, parentColumns = ["id"], childColumns = ["walletId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("walletId")],
)
data class SavingsGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetCentavos: Long,
    val targetDate: LocalDate? = null,
    /** Where the saved money is kept (e.g. a MariBank savings wallet), or null to earmark only. */
    val walletId: Long? = null,
    val iconKey: String = "goal",
    val isArchived: Boolean = false,
    val createdAt: Instant,
)
