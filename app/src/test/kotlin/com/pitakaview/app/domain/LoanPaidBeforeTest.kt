package com.pitakaview.app.domain

import com.pitakaview.app.data.db.entity.LoanEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * A loan the owner already had before Fitaka, with some of it paid (the owner's ask, 2026-11-27: months for a
 * monthly loan, payments for one paid twice a month, an amount otherwise, never from a wallet), and loans
 * paid twice a month.
 */
class LoanPaidBeforeTest {
    private val now = Instant.parse("2026-11-27T10:00:00Z")
    private val june1 = LocalDate.of(2026, 6, 1)
    private val jul15 = LocalDate.of(2026, 7, 15)

    private val monthly = LoanForm(
        who = "Tita Baby", amountText = "12000", startDate = june1, plan = LoanPlan.MONTHLY, dueDate = jul15, installmentText = "1000",
    )
    private val twice = monthly.copy(plan = LoanPlan.TWICE_A_MONTH, installmentText = "500", firstDayText = "15", secondDayText = "31")

    private fun loan(form: LoanForm) = LoanFormLogic.toEntity(form, null, now)

    // ---------- the form ----------

    @Test
    fun monthsAlreadyPaidCountAsThatManyMonthlyAmounts() {
        val entity = loan(monthly.copy(paidBeforeCountText = "4"))
        assertEquals(400_000L, entity.paidBeforeCentavos)
        assertTrue(LoanFormLogic.problems(monthly.copy(paidBeforeCountText = "4")).isEmpty())
    }

    @Test
    fun paymentsAlreadyPaidCountAsThatManyAmountsTwiceAMonth() {
        val entity = loan(twice.copy(paidBeforeCountText = "5"))
        assertEquals(250_000L, entity.paidBeforeCentavos)
        assertEquals(15, entity.dueDayOfMonth)
        assertEquals(31, entity.secondDueDay)
        assertEquals(50_000L, entity.installmentCentavos)
    }

    @Test
    fun aLoanWithoutInstallmentsTakesAnAmount() {
        val form = monthly.copy(plan = LoanPlan.ONE_DATE, paidBeforeAmountText = "2500.50", paidBeforeCountText = "4")
        assertEquals("the amount, not the count left over from the monthly plan", 250_050L, loan(form).paidBeforeCentavos)
        assertNull(loan(form).installmentCentavos)
    }

    @Test
    fun nothingTypedIsNothingPaidBefore() {
        assertEquals(0L, loan(monthly).paidBeforeCentavos)
        assertEquals(0L, loan(monthly.copy(plan = LoanPlan.NO_DATE, dueDate = null)).paidBeforeCentavos)
        assertEquals(0L, loan(monthly.copy(paidBeforeCountText = "0")).paidBeforeCentavos)
    }

    @Test
    fun moreThanTheWholeLoanIsAProblem() {
        assertTrue(LoanProblem.PAID_BEFORE in LoanFormLogic.problems(monthly.copy(paidBeforeCountText = "13")))
        assertFalse("all twelve is the whole loan, paid", LoanProblem.PAID_BEFORE in LoanFormLogic.problems(monthly.copy(paidBeforeCountText = "12")))
        assertTrue(LoanProblem.PAID_BEFORE in LoanFormLogic.problems(monthly.copy(plan = LoanPlan.NO_DATE, paidBeforeAmountText = "12000.01")))
        // With interest, the total is what counts.
        assertFalse(LoanProblem.PAID_BEFORE in LoanFormLogic.problems(monthly.copy(totalText = "13000", paidBeforeCountText = "13")))
    }

    @Test
    fun whatCannotBeReadIsAProblem() {
        assertTrue(LoanProblem.PAID_BEFORE in LoanFormLogic.problems(monthly.copy(paidBeforeCountText = "four")))
        assertTrue(LoanProblem.PAID_BEFORE in LoanFormLogic.problems(monthly.copy(plan = LoanPlan.NO_DATE, paidBeforeAmountText = "abc")))
    }

    /** A count with no monthly amount yet is the monthly amount's problem, said once. */
    @Test
    fun aCountWithoutTheInstallmentIsTheInstallmentsProblem() {
        val problems = LoanFormLogic.problems(monthly.copy(installmentText = "", paidBeforeCountText = "4"))
        assertEquals(setOf(LoanProblem.INSTALLMENT), problems)
    }

    @Test
    fun twiceAMonthNeedsTwoDifferentDaysFromOneTo31() {
        assertTrue(LoanProblem.DAYS in LoanFormLogic.problems(twice.copy(secondDayText = "15")))
        assertTrue(LoanProblem.DAYS in LoanFormLogic.problems(twice.copy(secondDayText = "32")))
        assertTrue(LoanProblem.DAYS in LoanFormLogic.problems(twice.copy(firstDayText = "")))
        assertTrue(LoanFormLogic.problems(twice.copy(firstDayText = "10", secondDayText = "25")).isEmpty())
        // The days are asked only twice a month.
        assertTrue(LoanFormLogic.problems(monthly.copy(secondDayText = "15")).isEmpty())
    }

    @Test
    fun aLoanOpenedAgainShowsWhatWasPaidAndItsPlan() {
        val back = LoanFormLogic.fromEntity(loan(twice.copy(paidBeforeCountText = "5", firstDayText = "10", secondDayText = "25")))
        assertEquals(LoanPlan.TWICE_A_MONTH, back.plan)
        assertEquals("5", back.paidBeforeCountText)
        assertEquals("10", back.firstDayText)
        assertEquals("25", back.secondDayText)
        // The amount is there too, so switching the plan in the form keeps what was paid.
        assertEquals(250_000L, Peso.parse(back.paidBeforeAmountText))

        val monthlyBack = LoanFormLogic.fromEntity(loan(monthly.copy(paidBeforeCountText = "4")))
        assertEquals(LoanPlan.MONTHLY, monthlyBack.plan)
        assertEquals("4", monthlyBack.paidBeforeCountText)

        val none = LoanFormLogic.fromEntity(loan(monthly))
        assertEquals("", none.paidBeforeCountText)
        assertEquals("", none.paidBeforeAmountText)
    }

    /** Editing keeps the count of months, so a corrected monthly amount moves what was paid with it. */
    @Test
    fun editingTheMonthlyAmountKeepsTheMonthsPaid() {
        val saved = loan(monthly.copy(paidBeforeCountText = "4"))
        val form = LoanFormLogic.fromEntity(saved).copy(installmentText = "1100")
        assertEquals(440_000L, LoanFormLogic.toEntity(form, saved, now).paidBeforeCentavos)
    }

    @Test
    fun aMonthlyLoanIsNotTakenForTwiceAMonthAndBack() {
        val saved = loan(twice)
        val asMonthly = LoanFormLogic.toEntity(LoanFormLogic.fromEntity(saved).copy(plan = LoanPlan.MONTHLY), saved, now)
        assertNull("monthly again: no second day", asMonthly.secondDueDay)
        assertEquals(15, asMonthly.dueDayOfMonth)
    }

    // ---------- the dates ----------

    private fun dates(loan: LoanEntity, count: Int) = (0 until count).map { LoanMath.installmentDate(loan, loan.dueDate!!, it.toLong()) }

    @Test
    fun twiceAMonthOnTheFifteenthAndTheLastDay() {
        val l = loan(twice.copy(dueDate = LocalDate.of(2027, 1, 15)))
        assertEquals(
            listOf("2027-01-15", "2027-01-31", "2027-02-15", "2027-02-28", "2027-03-15", "2027-03-31", "2027-04-15", "2027-04-30").map(LocalDate::parse),
            dates(l, 8),
        )
    }

    @Test
    fun twiceAMonthStartingBetweenItsDays() {
        val l = loan(twice.copy(dueDate = LocalDate.of(2026, 12, 20), firstDayText = "10", secondDayText = "25"))
        assertEquals(listOf("2026-12-20", "2026-12-25", "2027-01-10", "2027-01-25", "2027-02-10").map(LocalDate::parse), dates(l, 5))
    }

    @Test
    fun twiceAMonthStartingBeforeBothItsDays() {
        val l = loan(twice.copy(dueDate = LocalDate.of(2026, 12, 5)))
        assertEquals(listOf("2026-12-05", "2026-12-15", "2026-12-31", "2027-01-15").map(LocalDate::parse), dates(l, 4))
    }

    @Test
    fun monthlyDatesAreAsBefore() {
        val l = loan(monthly.copy(dueDate = LocalDate.of(2027, 1, 31)))
        assertEquals(listOf("2027-01-31", "2027-02-28", "2027-03-31", "2027-04-30").map(LocalDate::parse), dates(l, 4))
    }

    /** What the queries hand over as "paid" includes what was paid before the app; the next due moves on with it. */
    @Test
    fun theNextPaymentComesAfterTheOnesPaidBeforeTheApp() {
        val l = loan(monthly.copy(paidBeforeCountText = "4"))
        val due = LoanMath.nextDue(l, paidCentavos = l.paidBeforeCentavos)!!
        assertEquals("July, August, September, October paid: November", LocalDate.of(2026, 11, 15), due.date)
        assertEquals(100_000L, due.amountCentavos)
        assertEquals(800_000L, LoanMath.remaining(l, l.paidBeforeCentavos))

        val t = loan(twice.copy(paidBeforeCountText = "3"))
        val next = LoanMath.nextDue(t, t.paidBeforeCentavos)!!
        assertEquals("Jul 15, Jul 31, Aug 15 paid: Aug 31", LocalDate.of(2026, 8, 31), next.date)
        assertEquals(50_000L, next.amountCentavos)
    }

    @Test
    fun allOfItPaidBeforeTheAppIsSettled() {
        val l = loan(monthly.copy(paidBeforeCountText = "12"))
        assertTrue(LoanMath.isSettled(l, l.paidBeforeCentavos))
        assertNull(LoanMath.nextDue(l, l.paidBeforeCentavos))
    }
}
