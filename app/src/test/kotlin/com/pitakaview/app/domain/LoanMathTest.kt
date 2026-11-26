package com.pitakaview.app.domain

import com.pitakaview.app.data.db.entity.LoanEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class LoanMathTest {
    private fun loan(
        total: Long,
        dueDate: LocalDate? = null,
        installment: Long? = null,
        closed: Boolean = false,
    ) = LoanEntity(
        id = 1, direction = LoanDirection.I_OWE, counterparty = "Tita Baby", principalCentavos = total, totalPayableCentavos = total,
        startDate = LocalDate.of(2026, 11, 5), dueDate = dueDate, installmentCentavos = installment,
        dueDayOfMonth = if (installment != null) dueDate?.dayOfMonth else null,
        closedAt = if (closed) Instant.EPOCH else null, createdAt = Instant.EPOCH,
    )

    // ₱12,000 paid ₱2,000 a month, first on Dec 5.
    private val monthly = loan(total = 1_200_000, dueDate = LocalDate.of(2026, 12, 5), installment = 200_000)

    @Test
    fun eachWholeInstallmentPaidMovesTheNextDateOnAMonth() {
        assertEquals(LoanDue(LocalDate.of(2026, 12, 5), 200_000, true), LoanMath.nextDue(monthly, paidCentavos = 0))
        assertEquals(LoanDue(LocalDate.of(2027, 1, 5), 200_000, true), LoanMath.nextDue(monthly, paidCentavos = 200_000))
        assertEquals("paid ahead by two", LoanDue(LocalDate.of(2027, 3, 5), 200_000, true), LoanMath.nextDue(monthly, paidCentavos = 600_000))
    }

    @Test
    fun aPartPaidInstallmentAsksForTheRestOfIt() {
        assertEquals(LoanDue(LocalDate.of(2027, 1, 5), 100_000, true), LoanMath.nextDue(monthly, paidCentavos = 300_000))
        assertEquals(LoanDue(LocalDate.of(2026, 12, 5), 150_000, true), LoanMath.nextDue(monthly, paidCentavos = 50_000))
    }

    @Test
    fun theLastInstallmentIsOnlyWhatIsLeft() {
        // ₱5,000 at ₱2,000 a month: after two payments ₱1,000 is left.
        val small = loan(total = 500_000, dueDate = LocalDate.of(2026, 12, 5), installment = 200_000)
        assertEquals(LoanDue(LocalDate.of(2027, 2, 5), 100_000, true), LoanMath.nextDue(small, paidCentavos = 400_000))
        assertNull("paid in full: nothing is due", LoanMath.nextDue(small, paidCentavos = 500_000))
        assertTrue(LoanMath.isSettled(small, 500_000))
    }

    @Test
    fun aPaymentDayAtTheEndOfTheMonthFollowsShortMonths() {
        val endOfMonth = loan(total = 1_200_000, dueDate = LocalDate.of(2027, 1, 31), installment = 200_000)
        assertEquals(LocalDate.of(2027, 2, 28), LoanMath.nextDue(endOfMonth, 200_000)?.date)
        assertEquals("back to the 31st afterwards", LocalDate.of(2027, 3, 31), LoanMath.nextDue(endOfMonth, 400_000)?.date)
    }

    @Test
    fun aLoanWithOneDueDateAsksForAllThatIsLeftOnThatDate() {
        val once = loan(total = 900_000, dueDate = LocalDate.of(2026, 12, 1))
        assertEquals(LoanDue(LocalDate.of(2026, 12, 1), 700_000, false), LoanMath.nextDue(once, paidCentavos = 200_000))
    }

    @Test
    fun noDatesMeansNothingIsScheduled() {
        val open = loan(total = 900_000)
        assertNull(LoanMath.nextDue(open, 0))
        assertFalse(LoanMath.isSettled(open, 0))
        assertEquals(900_000L, LoanMath.remaining(open, 0))
    }

    @Test
    fun aLoanMarkedSettledAsksForNothingEvenWithMoneyLeft() {
        val forgiven = loan(total = 900_000, dueDate = LocalDate.of(2026, 12, 1), closed = true)
        assertTrue(LoanMath.isSettled(forgiven, 100_000))
        assertNull(LoanMath.nextDue(forgiven, 100_000))
    }

    @Test
    fun progressRunsFromNothingToAllAndNeverPastIt() {
        assertEquals(0f, LoanMath.paidFraction(monthly, 0), 0f)
        assertEquals(0.25f, LoanMath.paidFraction(monthly, 300_000), 0.0001f)
        assertEquals("an overpayment still reads as full", 1f, LoanMath.paidFraction(monthly, 1_500_000), 0f)
    }
}
