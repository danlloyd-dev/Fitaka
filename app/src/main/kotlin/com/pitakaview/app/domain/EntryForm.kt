package com.pitakaview.app.domain

import com.pitakaview.app.data.db.entity.TransactionEntity
import java.time.Instant
import java.time.LocalDate

/** The three kinds of entry the quick-entry screen handles. Loans, baon and savings have their own flows. */
enum class EntryKind(val label: String) {
    EXPENSE("Gastos"),
    INCOME("Kita"),
    TRANSFER("Transfer"),
}

/** What the quick-entry screen holds while you type. */
data class EntryForm(
    val kind: EntryKind = EntryKind.EXPENSE,
    val amountText: String = "",
    val categoryId: Long? = null,
    val fromWalletId: Long? = null,
    val toWalletId: Long? = null,
    val date: LocalDate,
    val note: String = "",
    /** Set when an income entry is the pay received for a pay schedule. */
    val payScheduleId: Long? = null,
)

enum class EntryProblem { AMOUNT, CATEGORY, WALLET, SAME_WALLET }

/** Turns a quick-entry form into a ledger row and back. Pure, so every rule is unit-tested. */
object EntryFormLogic {

    /** The typed amount in centavos, or null unless it is a plain amount above zero. */
    fun amountCentavos(form: EntryForm): Long? = Peso.parse(form.amountText)?.takeIf { it > 0 }

    fun problems(form: EntryForm): Set<EntryProblem> = buildSet {
        if (amountCentavos(form) == null) add(EntryProblem.AMOUNT)
        when (form.kind) {
            EntryKind.EXPENSE -> {
                if (form.categoryId == null) add(EntryProblem.CATEGORY)
                if (form.fromWalletId == null) add(EntryProblem.WALLET)
            }
            EntryKind.INCOME -> {
                if (form.categoryId == null) add(EntryProblem.CATEGORY)
                if (form.toWalletId == null) add(EntryProblem.WALLET)
            }
            EntryKind.TRANSFER -> when {
                form.fromWalletId == null || form.toWalletId == null -> add(EntryProblem.WALLET)
                form.fromWalletId == form.toWalletId -> add(EntryProblem.SAME_WALLET)
            }
        }
    }

    /**
     * Builds the ledger row. Call only when [problems] is empty. Editing keeps the row's id, creation
     * time and import links; links that make no sense for the new kind are cleared.
     */
    fun toTransaction(form: EntryForm, existing: TransactionEntity?, now: Instant): TransactionEntity {
        val amount = requireNotNull(amountCentavos(form)) { "Amount must be checked with problems() first" }
        val base = existing
            ?: TransactionEntity(purpose = TxPurpose.EXPENSE, amountCentavos = amount, occurredOn = form.date, createdAt = now)
        val note = form.note.trim().ifEmpty { null }
        return when (form.kind) {
            EntryKind.EXPENSE -> base.copy(
                purpose = TxPurpose.EXPENSE, amountCentavos = amount, occurredOn = form.date,
                fromWalletId = form.fromWalletId, toWalletId = null, categoryId = form.categoryId, note = note,
                payScheduleId = null,
            )
            EntryKind.INCOME -> base.copy(
                purpose = TxPurpose.INCOME, amountCentavos = amount, occurredOn = form.date,
                fromWalletId = null, toWalletId = form.toWalletId, categoryId = form.categoryId, note = note,
                baonId = null, payScheduleId = form.payScheduleId,
            )
            EntryKind.TRANSFER -> base.copy(
                purpose = TxPurpose.TRANSFER, amountCentavos = amount, occurredOn = form.date,
                fromWalletId = form.fromWalletId, toWalletId = form.toWalletId, categoryId = null, note = note,
                baonId = null, payScheduleId = null,
            )
        }
    }

    /** The form for an existing row, or null when that row is not one of the three quick-entry kinds. */
    fun fromTransaction(tx: TransactionEntity): EntryForm? {
        val kind = when (tx.purpose) {
            TxPurpose.EXPENSE -> EntryKind.EXPENSE
            TxPurpose.INCOME -> EntryKind.INCOME
            TxPurpose.TRANSFER -> EntryKind.TRANSFER
            else -> return null
        }
        return EntryForm(
            kind = kind, amountText = plainAmount(tx.amountCentavos), categoryId = tx.categoryId,
            fromWalletId = tx.fromWalletId, toWalletId = tx.toWalletId, date = tx.occurredOn, note = tx.note.orEmpty(),
            payScheduleId = tx.payScheduleId,
        )
    }

    /** Centavos as a person would type them: 25000 -> "250", 123456 -> "1234.56". */
    fun plainAmount(centavos: Long): String {
        val pesos = centavos / 100
        val cents = centavos % 100
        return if (cents == 0L) "$pesos" else "$pesos.${cents.toString().padStart(2, '0')}"
    }
}

object WalletRules {
    /**
     * Balance correction. A wallet's balance is `opening + entries`, so to make the app show [actual]
     * without inventing a fake income or expense, the starting balance moves by the difference.
     */
    fun openingForActualBalance(opening: Long, current: Long, actual: Long): Long = opening + (actual - current)
}
