package com.pitakaview.app.domain

import com.pitakaview.app.data.db.entity.BaonEntity
import com.pitakaview.app.data.db.entity.LoanEntity
import com.pitakaview.app.data.db.entity.SavingsGoalEntity
import com.pitakaview.app.data.db.entity.TransactionEntity
import java.time.Instant
import java.time.LocalDate

// The forms behind the Utang, Ipon and Baon screens. Pure, so every rule is unit-tested.

// ---------- a loan ----------

/** How a loan is meant to be paid back. */
enum class LoanPlan {
    NO_DATE,
    ONE_DATE,
    MONTHLY,

    /** Twice a month on two set days, e.g. the 15th and the last day (the owner's ask, 2026-11-27). */
    TWICE_A_MONTH,
    ;

    /** Paid in equal amounts on set dates: what was paid before the app is then said as a number of them. */
    val inInstallments: Boolean get() = this == MONTHLY || this == TWICE_A_MONTH
}

data class LoanForm(
    val direction: LoanDirection = LoanDirection.I_OWE,
    val who: String = "",
    val amountText: String = "",
    /** Amount plus interest or fees. Empty means the same as the amount. */
    val totalText: String = "",
    val startDate: LocalDate,
    val plan: LoanPlan = LoanPlan.NO_DATE,
    /** The due date, or the first payment date of a loan paid in installments. */
    val dueDate: LocalDate? = null,
    val installmentText: String = "",
    /** Twice a month: the two days of the month a payment is due (31 means the last day). */
    val firstDayText: String = "15",
    val secondDayText: String = "31",
    /** Paid before the app, for a loan paid in installments: how many of them (months, or payments twice a month). */
    val paidBeforeCountText: String = "",
    /** Paid before the app, for any other loan: how much. */
    val paidBeforeAmountText: String = "",
    val note: String = "",
    /** Only when adding: the wallet the money went into or came out of. Null for a loan from before the app. */
    val walletId: Long? = null,
    /** Whether to notify when it is due (when reminders are on in Settings). */
    val remind: Boolean = true,
)

enum class LoanProblem {
    WHO, AMOUNT, TOTAL, DUE_DATE, INSTALLMENT,

    /** Twice a month: a day that is not 1 to 31, or the same day twice. */
    DAYS,

    /** What was paid before the app is not a whole number (or an amount), or is more than the whole loan. */
    PAID_BEFORE,
}

object LoanFormLogic {
    fun amountCentavos(form: LoanForm): Long? = Peso.parse(form.amountText)?.takeIf { it > 0 }

    /** The amount to pay back in all: what was typed, or the amount itself when the field is empty. */
    fun totalCentavos(form: LoanForm): Long? =
        if (form.totalText.isBlank()) amountCentavos(form) else Peso.parse(form.totalText)?.takeIf { it > 0 }

    fun installmentCentavos(form: LoanForm): Long? = Peso.parse(form.installmentText)?.takeIf { it > 0 }

    /** How many installments were paid before the app: 0 when the field is empty, null when it is not a whole number. */
    fun paidBeforeCount(form: LoanForm): Int? = form.paidBeforeCountText.trim().let { if (it.isEmpty()) 0 else it.toIntOrNull()?.takeIf { n -> n >= 0 } }

    /**
     * What was paid before the app, in centavos: so many installments, or the amount typed. 0 when nothing
     * was typed; null when what was typed cannot be read, or the installment it counts in is not known yet.
     */
    fun paidBeforeCentavos(form: LoanForm): Long? = if (form.plan.inInstallments) {
        val count = paidBeforeCount(form)
        when {
            count == null -> null
            count == 0 -> 0
            else -> installmentCentavos(form)?.let { it * count }
        }
    } else {
        form.paidBeforeAmountText.trim().let { if (it.isEmpty()) 0 else Peso.parse(it)?.takeIf { cents -> cents >= 0 } }
    }

    fun problems(form: LoanForm): Set<LoanProblem> = buildSet {
        if (form.who.isBlank()) add(LoanProblem.WHO)
        val amount = amountCentavos(form)
        if (amount == null) add(LoanProblem.AMOUNT)
        val total = totalCentavos(form)
        // Paying back less than was lent is not a loan with interest; it is a typing slip.
        if (form.totalText.isNotBlank() && (total == null || (amount != null && total < amount))) add(LoanProblem.TOTAL)
        if (form.plan != LoanPlan.NO_DATE && form.dueDate == null) add(LoanProblem.DUE_DATE)
        val installment = installmentCentavos(form)
        if (form.plan.inInstallments && installment == null) add(LoanProblem.INSTALLMENT)
        if (form.plan == LoanPlan.TWICE_A_MONTH) {
            val first = PayScheduleFormLogic.day(form.firstDayText)
            val second = PayScheduleFormLogic.day(form.secondDayText)
            if (first == null || second == null || first == second) add(LoanProblem.DAYS)
        }
        // Unreadable, or more than the whole loan. A count with no installment yet is the installment's problem.
        val unreadable = if (form.plan.inInstallments) paidBeforeCount(form) == null else paidBeforeCentavos(form) == null
        val before = paidBeforeCentavos(form)
        if (unreadable || (before != null && total != null && before > total)) add(LoanProblem.PAID_BEFORE)
    }

    /**
     * Builds the loan. Call only when [problems] is empty. Editing keeps the id, the day it was added,
     * whether it was marked settled, and its direction: the entries already logged depend on that.
     */
    fun toEntity(form: LoanForm, existing: LoanEntity?, now: Instant): LoanEntity {
        val amount = requireNotNull(amountCentavos(form)) { "Amount must be checked with problems() first" }
        val total = requireNotNull(totalCentavos(form)) { "Total must be checked with problems() first" }
        val base = existing ?: LoanEntity(
            direction = form.direction, counterparty = "", principalCentavos = amount, totalPayableCentavos = total,
            startDate = form.startDate, createdAt = now,
        )
        val twice = form.plan == LoanPlan.TWICE_A_MONTH
        return base.copy(
            counterparty = form.who.trim(),
            principalCentavos = amount,
            totalPayableCentavos = total,
            startDate = form.startDate,
            dueDate = if (form.plan == LoanPlan.NO_DATE) null else form.dueDate,
            installmentCentavos = if (form.plan.inInstallments) installmentCentavos(form) else null,
            dueDayOfMonth = when (form.plan) {
                LoanPlan.MONTHLY -> form.dueDate?.dayOfMonth
                LoanPlan.TWICE_A_MONTH -> PayScheduleFormLogic.day(form.firstDayText)
                else -> null
            },
            secondDueDay = if (twice) PayScheduleFormLogic.day(form.secondDayText) else null,
            paidBeforeCentavos = requireNotNull(paidBeforeCentavos(form)) { "Paid-before must be checked with problems() first" },
            note = form.note.trim().ifEmpty { null },
            remind = form.remind,
        )
    }

    fun fromEntity(loan: LoanEntity): LoanForm {
        val plan = when {
            loan.installmentCentavos != null && loan.dueDate != null && loan.secondDueDay != null -> LoanPlan.TWICE_A_MONTH
            loan.installmentCentavos != null && loan.dueDate != null -> LoanPlan.MONTHLY
            loan.dueDate != null -> LoanPlan.ONE_DATE
            else -> LoanPlan.NO_DATE
        }
        val installment = loan.installmentCentavos
        val before = loan.paidBeforeCentavos
        return LoanForm(
            direction = loan.direction,
            who = loan.counterparty,
            amountText = EntryFormLogic.plainAmount(loan.principalCentavos),
            totalText = if (loan.totalPayableCentavos == loan.principalCentavos) "" else EntryFormLogic.plainAmount(loan.totalPayableCentavos),
            startDate = loan.startDate,
            plan = plan,
            dueDate = loan.dueDate,
            installmentText = installment?.let(EntryFormLogic::plainAmount).orEmpty(),
            firstDayText = if (plan == LoanPlan.TWICE_A_MONTH) loan.dueDayOfMonth?.toString() ?: "15" else "15",
            secondDayText = loan.secondDueDay?.toString() ?: "31",
            // Both are filled, so a change of plan in the form keeps what was paid: as a count, or as the amount.
            paidBeforeCountText = if (before > 0 && installment != null && installment > 0) (before / installment).toString() else "",
            paidBeforeAmountText = if (before > 0) EntryFormLogic.plainAmount(before) else "",
            note = loan.note.orEmpty(),
            remind = loan.remind,
        )
    }

    /**
     * The entry for the money changing hands when a new loan is added with a wallet: into the wallet
     * when borrowing, out of it when lending. Null when no wallet was picked (a loan from before the app).
     */
    fun startRow(form: LoanForm, loanId: Long, now: Instant): TransactionEntity? {
        val wallet = form.walletId ?: return null
        val amount = requireNotNull(amountCentavos(form)) { "Amount must be checked with problems() first" }
        val borrowed = form.direction == LoanDirection.I_OWE
        return TransactionEntity(
            purpose = TxPurpose.LOAN_START, amountCentavos = amount, occurredOn = form.startDate,
            fromWalletId = if (borrowed) null else wallet, toWalletId = if (borrowed) wallet else null,
            loanId = loanId, createdAt = now,
        )
    }
}

// ---------- a savings goal ----------

data class GoalForm(
    val name: String = "",
    /** Empty means open-ended savings with no target. */
    val targetText: String = "",
    val targetDate: LocalDate? = null,
    /** Where the savings are kept, or null to only set money aside inside the wallet it is in. */
    val walletId: Long? = null,
)

enum class GoalProblem { NAME, TARGET }

object GoalFormLogic {
    fun targetCentavos(form: GoalForm): Long? = Peso.parse(form.targetText)?.takeIf { it > 0 }

    fun problems(form: GoalForm): Set<GoalProblem> = buildSet {
        if (form.name.isBlank()) add(GoalProblem.NAME)
        if (form.targetText.isNotBlank() && targetCentavos(form) == null) add(GoalProblem.TARGET)
    }

    /**
     * [walletFixed]: something is logged for the goal, so its wallet stays as it is. Every deposit went
     * into that wallet and every withdrawal takes from it; until 2026-11-27 the wallet could be changed
     * after saving into it, and "Take out" then took the money from a wallet that never had it.
     */
    fun toEntity(form: GoalForm, existing: SavingsGoalEntity?, now: Instant, walletFixed: Boolean = false): SavingsGoalEntity {
        val base = existing ?: SavingsGoalEntity(name = "", createdAt = now)
        return base.copy(
            name = form.name.trim(),
            targetCentavos = if (form.targetText.isBlank()) null else targetCentavos(form),
            targetDate = form.targetDate,
            walletId = if (existing != null && walletFixed) existing.walletId else form.walletId,
        )
    }

    fun fromEntity(goal: SavingsGoalEntity) = GoalForm(
        name = goal.name,
        targetText = goal.targetCentavos?.let(EntryFormLogic::plainAmount).orEmpty(),
        targetDate = goal.targetDate,
        walletId = goal.walletId,
    )
}

// ---------- a baon ----------

data class BaonForm(
    val direction: BaonDirection = BaonDirection.RECEIVE,
    val name: String = "",
    val amountText: String = "",
    val period: BaonPeriod = BaonPeriod.WEEKLY,
    /** Weekly baon starts again on this weekday, monthly baon on this day of the month. */
    val startDate: LocalDate,
    /** The wallet offered first for its entries. */
    val walletId: Long? = null,
)

enum class BaonProblem { NAME, AMOUNT }

object BaonFormLogic {
    fun amountCentavos(form: BaonForm): Long? = Peso.parse(form.amountText)?.takeIf { it > 0 }

    fun problems(form: BaonForm): Set<BaonProblem> = buildSet {
        if (form.name.isBlank()) add(BaonProblem.NAME)
        if (amountCentavos(form) == null) add(BaonProblem.AMOUNT)
    }

    /** Editing keeps the id, the day it was added, whether it is stopped, and its direction. */
    fun toEntity(form: BaonForm, existing: BaonEntity?, now: Instant): BaonEntity {
        val amount = requireNotNull(amountCentavos(form)) { "Amount must be checked with problems() first" }
        val base = existing ?: BaonEntity(
            name = "", direction = form.direction, amountCentavos = amount, period = form.period, startDate = form.startDate, createdAt = now,
        )
        return base.copy(
            name = form.name.trim(), amountCentavos = amount, period = form.period, startDate = form.startDate, walletId = form.walletId,
        )
    }

    fun fromEntity(baon: BaonEntity) = BaonForm(
        direction = baon.direction, name = baon.name, amountText = EntryFormLogic.plainAmount(baon.amountCentavos),
        period = baon.period, startDate = baon.startDate, walletId = baon.walletId,
    )
}

// ---------- an entry that belongs to a loan, a goal or a baon ----------

/** What such an entry is attached to. The id that matches [purpose] is set. */
data class MoveTarget(
    val purpose: TxPurpose,
    val loanId: Long? = null,
    val loanDirection: LoanDirection? = null,
    val goalId: Long? = null,
    /** The goal's own wallet, if it has one. */
    val goalWalletId: Long? = null,
    val baonId: Long? = null,
)

data class MoveForm(
    val amountText: String = "",
    /** The one wallet the owner picks: where it was paid from, received in, taken from or put into. */
    val walletId: Long? = null,
    val date: LocalDate,
    val note: String = "",
)

enum class MoveProblem {
    AMOUNT,
    WALLET,
    MORE_THAN_SAVED,

    /** A deposit lowered below what has since been taken out of its goal. */
    LESS_THAN_TAKEN_OUT,
}

object MoveFormLogic {
    /** The kinds of entry this form handles. Spending, income and transfers use quick entry instead. */
    val PURPOSES = setOf(
        TxPurpose.LOAN_START, TxPurpose.LOAN_PAYMENT, TxPurpose.SAVINGS_DEPOSIT, TxPurpose.SAVINGS_WITHDRAWAL,
        TxPurpose.BAON_RECEIVED, TxPurpose.BAON_GIVEN,
    )

    fun amountCentavos(form: MoveForm): Long? = Peso.parse(form.amountText)?.takeIf { it > 0 }

    /**
     * [mostCentavos], when given, is the most that can be taken out of a goal: what is saved in it.
     * [leastCentavos], when given, is the least an old deposit can be lowered to: what has not been taken out since.
     */
    fun problems(form: MoveForm, purpose: TxPurpose, mostCentavos: Long? = null, leastCentavos: Long? = null): Set<MoveProblem> = buildSet {
        val amount = amountCentavos(form)
        if (amount == null) add(MoveProblem.AMOUNT)
        if (form.walletId == null) add(MoveProblem.WALLET)
        if (purpose == TxPurpose.SAVINGS_WITHDRAWAL && amount != null && mostCentavos != null && amount > mostCentavos) {
            add(MoveProblem.MORE_THAN_SAVED)
        }
        if (purpose == TxPurpose.SAVINGS_DEPOSIT && amount != null && leastCentavos != null && amount < leastCentavos) {
            add(MoveProblem.LESS_THAN_TAKEN_OUT)
        }
    }

    /**
     * Builds the ledger row. Call only when [problems] is empty.
     *
     * Savings rows name two wallets. A deposit goes from the picked wallet into the goal's wallet, or
     * stays in the picked wallet (only set aside) when the goal has none; a withdrawal is the reverse.
     * Editing keeps the other side as it was, so changing an old deposit's amount does not re-route it
     * if the goal has since been given a different wallet.
     */
    fun toTransaction(form: MoveForm, target: MoveTarget, existing: TransactionEntity?, now: Instant): TransactionEntity {
        val amount = requireNotNull(amountCentavos(form)) { "Amount must be checked with problems() first" }
        val wallet = requireNotNull(form.walletId) { "Wallet must be checked with problems() first" }
        val setAside = existing == null || existing.fromWalletId == existing.toWalletId
        val (from, to) = when (target.purpose) {
            TxPurpose.SAVINGS_DEPOSIT -> wallet to (if (existing == null) target.goalWalletId ?: wallet else if (setAside) wallet else existing.toWalletId)
            TxPurpose.SAVINGS_WITHDRAWAL -> (if (existing == null) target.goalWalletId ?: wallet else if (setAside) wallet else existing.fromWalletId) to wallet
            else -> when (LedgerRules.walletShape(target.purpose, target.loanDirection)) {
                WalletShape.INTO -> null to wallet
                WalletShape.OUT_OF -> wallet to null
                WalletShape.BETWEEN -> error("${target.purpose} is not a section entry")
            }
        }
        val base = existing
            ?: TransactionEntity(purpose = target.purpose, amountCentavos = amount, occurredOn = form.date, createdAt = now)
        return base.copy(
            purpose = target.purpose, amountCentavos = amount, occurredOn = form.date, fromWalletId = from, toWalletId = to,
            categoryId = null, note = form.note.trim().ifEmpty { null },
            loanId = target.loanId, goalId = target.goalId, baonId = target.baonId, payScheduleId = null,
        )
    }

    /** The wallet the owner picked for an existing row: the side of the row that was theirs to choose. */
    fun chosenWallet(tx: TransactionEntity): Long? = when (tx.purpose) {
        TxPurpose.SAVINGS_DEPOSIT -> tx.fromWalletId
        TxPurpose.SAVINGS_WITHDRAWAL -> tx.toWalletId
        else -> tx.toWalletId ?: tx.fromWalletId
    }

    fun fromTransaction(tx: TransactionEntity) = MoveForm(
        amountText = EntryFormLogic.plainAmount(tx.amountCentavos), walletId = chosenWallet(tx), date = tx.occurredOn, note = tx.note.orEmpty(),
    )
}
