package com.pitakaview.app.data.db.dao

import androidx.room3.Embedded
import com.pitakaview.app.data.db.entity.LoanEntity
import com.pitakaview.app.data.db.entity.WalletEntity
import com.pitakaview.app.domain.TxPurpose
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
    val targetCentavos: Long,
    val targetDate: LocalDate?,
    val walletId: Long?,
    val savedCentavos: Long,
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
)

/** True when money only moved between your own wallets (a transfer or a savings move): neither in nor out. */
val RecentTransaction.isMove: Boolean get() = fromWalletName != null && toWalletName != null

/** Positive for money in, negative for money out, plain amount for moves (see [isMove]). */
val RecentTransaction.signedCentavos: Long
    get() = if (toWalletName == null && fromWalletName != null) -amountCentavos else amountCentavos

/** Outside the class so Room does not try to map it as a column. */
val LoanWithPaid.remainingCentavos: Long get() = loan.totalPayableCentavos - paidCentavos
