package com.pitakaview.app.ui

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.pitakaview.app.data.db.entity.LoanEntity
import com.pitakaview.app.domain.LoanDirection
import com.pitakaview.app.domain.LoanMath
import com.pitakaview.app.domain.Peso
import com.pitakaview.app.ui.utang.PaidBeforeText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate

/**
 * A loan the owner already had before Fitaka, some of it paid, and a loan paid twice a month, through the
 * real app (the owner's asks, 2026-11-27). A tall screen keeps the whole form in view.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h1500dp-xhdpi")
class LoanPaidBeforeFlowTest : FlowTestBase() {
    private fun chip(text: String) = rule.onNode(hasText(text) and isSelectable())
    private fun box(label: String) = rule.onNode(hasSetTextAction() and hasContentDescription(label))

    private fun monthlyLoan(firstPayment: LocalDate): Long = runBlocking {
        container.database.loanDao().insert(
            LoanEntity(
                direction = LoanDirection.I_OWE, counterparty = "SSS loan", principalCentavos = 1_200_000, totalPayableCentavos = 1_200_000,
                startDate = firstPayment.minusMonths(1), dueDate = firstPayment, installmentCentavos = 100_000,
                dueDayOfMonth = firstPayment.dayOfMonth, createdAt = Instant.now(),
            ),
        )
    }

    private fun openLoanToEdit(who: String) {
        tapTab("Utang")
        waitFor(who)
        rule.onNode(hasText(who) and hasClickAction()).performClick()
        waitFor("Left to pay")
        tapButton("Edit loan")
        waitFor("How it is paid back")
    }

    @Test
    fun anOldLoanWithSomeOfItPaidIsAddedAndNoWalletMoves() {
        start()
        val cashBefore = cashBalance()
        tapTab("Utang")
        waitFor("No loans")
        tapButton("Add a loan")
        waitFor("How it is paid back")
        field(0).performTextInput("Tita Baby")
        field(1).performTextInput("10000")
        chip("No wallet").performClick()
        // No due date: what was paid before is said as an amount.
        box(PaidBeforeText.AMOUNT).performTextInput("4000")
        tapButton("Save")
        rule.waitUntil(10_000) { loans().size == 1 }

        val row = loans().single()
        assertEquals(400_000L, row.loan.paidBeforeCentavos)
        assertEquals("it counts as paid", 400_000L, row.paidCentavos)
        assertEquals("no wallet moved, for the loan or for what was paid before", cashBefore, cashBalance())

        rule.onNode(hasText("Tita Baby") and hasClickAction()).performClick()
        waitFor("Left to pay")
        assertTrue(shows("Paid ₱4,000.00 of ₱10,000.00"))
        assertTrue(shows("₱4,000.00 of it before Fitaka"))
        assertTrue(shows("₱6,000.00"))
        assertFalse("and it is no entry", shows("Loan payment"))
    }

    @Test
    fun monthsPaidAreSetLaterOnALoanAlreadyInTheAppAndTheNextMonthFollows() {
        start()
        val first = today().minusMonths(3).withDayOfMonth(15)
        monthlyLoan(first)
        openLoanToEdit("SSS loan")
        assertTrue("a monthly loan asks for months", shows(PaidBeforeText.MONTHS))
        box(PaidBeforeText.MONTHS).performTextInput("4")
        tapButton("Save")
        rule.waitUntil(10_000) { loans().single().loan.paidBeforeCentavos == 400_000L }

        val row = loans().single()
        val due = LoanMath.nextDue(row.loan, row.paidCentavos)!!
        assertEquals("four months paid: the fifth is next", first.plusMonths(4), due.date)
        assertEquals(800_000L, LoanMath.remaining(row.loan, row.paidCentavos))
        waitFor("₱8,000.00")
    }

    @Test
    fun aLoanPaidTwiceAMonthOnTheFifteenthAndTheLastDay() {
        start()
        val first = LocalDate.of(today().year, today().month, 15).minusMonths(1)
        monthlyLoan(first)
        openLoanToEdit("SSS loan")
        chip("Twice a month").performClick()
        waitFor("How much each time")
        assertTrue("the days start on the 15th and the last day", shows("Second payment day"))
        box("How much each time").performTextReplacement("500")
        box(PaidBeforeText.PAYMENTS).performTextInput("3")
        tapButton("Save")
        rule.waitUntil(10_000) { loans().single().loan.secondDueDay != null }

        val loan = loans().single().loan
        assertEquals(15, loan.dueDayOfMonth)
        assertEquals(31, loan.secondDueDay)
        assertEquals(50_000L, loan.installmentCentavos)
        assertEquals(150_000L, loan.paidBeforeCentavos)
        // The 15th and the last day of last month and the 15th of this one are paid: the last day of this month is next.
        val due = LoanMath.nextDue(loan, loans().single().paidCentavos)!!
        assertEquals(first.plusMonths(1).withDayOfMonth(first.plusMonths(1).lengthOfMonth()), due.date)
        assertEquals(Peso.format(50_000), Peso.format(due.amountCentavos))
    }

    @Test
    fun moreThanTheWholeLoanIsRefusedWhereItWasTyped() {
        start()
        monthlyLoan(today().plusDays(5))
        openLoanToEdit("SSS loan")
        box(PaidBeforeText.MONTHS).performTextInput("13")
        tapButton("Save")
        waitFor(PaidBeforeText.TOO_MUCH)
        assertEquals("nothing saved", 0L, loans().single().loan.paidBeforeCentavos)
    }
}
