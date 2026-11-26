package com.pitakaview.app.data.db.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import com.pitakaview.app.domain.TxPurpose
import java.time.Instant
import java.time.LocalDate

/**
 * The ledger: every peso that moves is one row here, whatever section it belongs to.
 * Wallet balances, monthly totals, goal progress, loan balances and baon left are all computed
 * from these rows, so the numbers on different screens cannot disagree.
 * Which wallet fields must be set depends on [purpose] — see LedgerRules.
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(entity = WalletEntity::class, parentColumns = ["id"], childColumns = ["fromWalletId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = WalletEntity::class, parentColumns = ["id"], childColumns = ["toWalletId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = BaonEntity::class, parentColumns = ["id"], childColumns = ["baonId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = SavingsGoalEntity::class, parentColumns = ["id"], childColumns = ["goalId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = LoanEntity::class, parentColumns = ["id"], childColumns = ["loanId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = PayScheduleEntity::class, parentColumns = ["id"], childColumns = ["payScheduleId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = ImportBatchEntity::class, parentColumns = ["id"], childColumns = ["importBatchId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [
        Index("occurredOn"),
        Index("fromWalletId"),
        Index("toWalletId"),
        Index("categoryId"),
        Index("baonId"),
        Index("goalId"),
        Index("loanId"),
        Index("payScheduleId"),
        Index("importBatchId"),
        // Import de-duplication, e.g. "GCASH:<reference no.>". NULL for typed entries (NULLs never collide).
        Index(value = ["externalRef"], unique = true),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val purpose: TxPurpose,
    /** Always positive, in centavos. [purpose] and the wallet fields give the direction. */
    val amountCentavos: Long,
    val occurredOn: LocalDate,
    val fromWalletId: Long? = null,
    val toWalletId: Long? = null,
    /** Required for INCOME and EXPENSE. */
    val categoryId: Long? = null,
    val note: String? = null,
    /** BAON_RECEIVED / BAON_GIVEN, or an EXPENSE charged to a received baon. */
    val baonId: Long? = null,
    val goalId: Long? = null,
    val loanId: Long? = null,
    /** The pay schedule an INCOME row was received for. */
    val payScheduleId: Long? = null,
    val importBatchId: Long? = null,
    val externalRef: String? = null,
    val createdAt: Instant,
)
