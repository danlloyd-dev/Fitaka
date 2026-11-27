package com.pitakaview.app.domain

import com.pitakaview.app.data.db.entity.LoanEntity
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.min

/** The next amount a loan asks for, and when. */
data class LoanDue(val date: LocalDate, val amountCentavos: Long, val isInstallment: Boolean)

/**
 * Where a loan stands, worked out from the loan and how much has been paid (what was paid before the app
 * plus the payments logged; the queries add the two). Nothing is stored about "which installment was paid":
 * deleting or changing a payment simply moves the answer.
 */
object LoanMath {

    fun remaining(loan: LoanEntity, paidCentavos: Long): Long = loan.totalPayableCentavos - paidCentavos

    /** Paid in full, or marked settled by the owner (a debt forgiven or written off). */
    fun isSettled(loan: LoanEntity, paidCentavos: Long): Boolean = loan.closedAt != null || remaining(loan, paidCentavos) <= 0

    /**
     * What is due next, or null when the loan is settled or has no dates.
     *
     * A loan paid in installments keeps its first payment date in `dueDate`, and the day (or, twice a month,
     * the two days) of the month in `dueDayOfMonth` and `secondDueDay`. Every whole installment already paid
     * moves the next date on by one installment ([installmentDate]); a part-paid installment asks for the rest.
     */
    fun nextDue(loan: LoanEntity, paidCentavos: Long): LoanDue? {
        if (isSettled(loan, paidCentavos)) return null
        val left = remaining(loan, paidCentavos)
        val installment = loan.installmentCentavos
        val first = loan.dueDate
        if (installment != null && installment > 0 && first != null) {
            val paid = paidCentavos.coerceAtLeast(0)
            return LoanDue(
                date = installmentDate(loan, first, paid / installment),
                amountCentavos = min(installment - paid % installment, left),
                isInstallment = true,
            )
        }
        return first?.let { LoanDue(it, left, isInstallment = false) }
    }

    /**
     * The date of installment number [index] (0 is the first payment, on [first]).
     *
     * Monthly: the same day each month, or the month's last day where the month is shorter. Twice a month:
     * the first payment date, then each of the loan's two days after it, in date order (31 is the last day of
     * every month; in a short month two days can land on the same last day, and both payments fall on it).
     */
    fun installmentDate(loan: LoanEntity, first: LocalDate, index: Long): LocalDate {
        val firstDay = loan.dueDayOfMonth ?: first.dayOfMonth
        val second = loan.secondDueDay
        if (second == null) {
            val month = YearMonth.from(first).plusMonths(index)
            return month.on(firstDay)
        }
        if (index <= 0) return first
        val days = listOf(firstDay, second).sorted()
        // The set days still to come in the first payment's own month, then two in every month after it.
        val firstMonth = YearMonth.from(first)
        val laterThatMonth = days.map { firstMonth.on(it) }.filter { it > first }
        if (index <= laterThatMonth.size) return laterThatMonth[(index - 1).toInt()]
        val after = index - laterThatMonth.size - 1 // 0-based count of set days in the months that follow
        val month = firstMonth.plusMonths(after / days.size + 1)
        return month.on(days[(after % days.size).toInt()])
    }

    private fun YearMonth.on(day: Int): LocalDate = atDay(min(day, lengthOfMonth()))

    /** How far along the loan is, 0 to 1. */
    fun paidFraction(loan: LoanEntity, paidCentavos: Long): Float =
        if (loan.totalPayableCentavos <= 0) 1f else (paidCentavos.toFloat() / loan.totalPayableCentavos).coerceIn(0f, 1f)
}
