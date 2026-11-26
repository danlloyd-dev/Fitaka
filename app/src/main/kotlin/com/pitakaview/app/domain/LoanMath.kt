package com.pitakaview.app.domain

import com.pitakaview.app.data.db.entity.LoanEntity
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.min

/** The next amount a loan asks for, and when. */
data class LoanDue(val date: LocalDate, val amountCentavos: Long, val isInstallment: Boolean)

/**
 * Where a loan stands, worked out from the loan and the sum of its payments. Nothing is stored about
 * "which installment was paid": deleting or changing a payment simply moves the answer.
 */
object LoanMath {

    fun remaining(loan: LoanEntity, paidCentavos: Long): Long = loan.totalPayableCentavos - paidCentavos

    /** Paid in full, or marked settled by the owner (a debt forgiven or written off). */
    fun isSettled(loan: LoanEntity, paidCentavos: Long): Boolean = loan.closedAt != null || remaining(loan, paidCentavos) <= 0

    /**
     * What is due next, or null when the loan is settled or has no dates.
     *
     * A monthly loan keeps its first payment date in `dueDate` and the day of the month in
     * `dueDayOfMonth`. Every whole installment already paid moves the next date on by one month; a
     * part-paid installment asks for the rest of it.
     */
    fun nextDue(loan: LoanEntity, paidCentavos: Long): LoanDue? {
        if (isSettled(loan, paidCentavos)) return null
        val left = remaining(loan, paidCentavos)
        val installment = loan.installmentCentavos
        val first = loan.dueDate
        if (installment != null && installment > 0 && first != null) {
            val paid = paidCentavos.coerceAtLeast(0)
            val whole = paid / installment
            val month = YearMonth.from(first).plusMonths(whole)
            val day = loan.dueDayOfMonth ?: first.dayOfMonth
            return LoanDue(
                date = month.atDay(min(day, month.lengthOfMonth())),
                amountCentavos = min(installment - paid % installment, left),
                isInstallment = true,
            )
        }
        return first?.let { LoanDue(it, left, isInstallment = false) }
    }

    /** How far along the loan is, 0 to 1. */
    fun paidFraction(loan: LoanEntity, paidCentavos: Long): Float =
        if (loan.totalPayableCentavos <= 0) 1f else (paidCentavos.toFloat() / loan.totalPayableCentavos).coerceIn(0f, 1f)
}
