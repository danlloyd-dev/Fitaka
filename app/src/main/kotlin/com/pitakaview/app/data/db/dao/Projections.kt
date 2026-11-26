package com.pitakaview.app.data.db.dao

import androidx.room3.Embedded
import com.pitakaview.app.data.db.entity.LoanEntity
import com.pitakaview.app.data.db.entity.WalletEntity
import com.pitakaview.app.domain.TxPurpose
import java.time.Instant
import java.time.LocalDate

/** Query results that are not whole tables. */

data class WalletWithBalance(
    @Embedded val wallet: WalletEntity,
    val balanceCentavos: Long,
)

data class PurposeTotal(
    val purpose: TxPurpose,
    val totalCentavos: Long,
)

data class CategoryTotal(
    val categoryId: Long,
    val name: String,
    val iconKey: String,
    val totalCentavos: Long,
)

/** [month] is "YYYY-MM". */
data class MonthPurposeTotal(
    val month: String,
    val purpose: TxPurpose,
    val totalCentavos: Long,
)

data class BaonActivityRow(
    val baonId: Long,
    val purpose: TxPurpose,
    val occurredOn: LocalDate,
    val amountCentavos: Long,
)

data class GoalProgress(
    val goalId: Long,
    val name: String,
    /** Null for open-ended savings with no target. */
    val targetCentavos: Long?,
    val targetDate: LocalDate?,
    val walletId: Long?,
    val savedCentavos: Long,
    /** Hidden by the owner: kept with its entries, left out of Home and of the list of goals in use. */
    val isArchived: Boolean = false,
)

data class LoanWithPaid(
    @Embedded val loan: LoanEntity,
    val paidCentavos: Long,
)

data class RecentTransaction(
    val id: Long,
    val purpose: TxPurpose,
    val amountCentavos: Long,
    val occurredOn: LocalDate,
    val note: String?,
    val categoryName: String?,
    val fromWalletName: String?,
    val toWalletName: String?,
    /** The key of the category's drawn icon (see CategoryIcons); null for an entry without a category. */
    val categoryIconKey: String? = null,
)

/** Pay for [payScheduleId] was received on [occurredOn]. */
data class PayReceipt(
    val payScheduleId: Long,
    val occurredOn: LocalDate,
)

/** True when money only moved between your own wallets (a transfer or a savings move): neither in nor out. */
val RecentTransaction.isMove: Boolean get() = fromWalletName != null && toWalletName != null

/** Positive for money in, negative for money out, plain amount for moves (see [isMove]). */
val RecentTransaction.signedCentavos: Long
    get() = if (toWalletName == null && fromWalletName != null) -amountCentavos else amountCentavos

/** One past import, for the list on the Import screen. */
data class ImportBatchRow(
    val id: Long,
    val fileName: String,
    val importedAt: Instant,
    val walletName: String,
    /** Entries of this import still in the ledger (some may have been deleted by hand since). */
    val entryCount: Int,
)

/** Outside the class so Room does not try to map it as a column. */
val LoanWithPaid.remainingCentavos: Long get() = loan.totalPayableCentavos - paidCentavos
