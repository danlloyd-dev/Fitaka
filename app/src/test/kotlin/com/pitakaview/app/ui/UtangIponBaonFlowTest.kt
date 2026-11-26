package com.pitakaview.app.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.pitakaview.app.data.db.entity.BaonEntity
import com.pitakaview.app.data.db.entity.LoanEntity
import com.pitakaview.app.domain.BaonDirection
import com.pitakaview.app.domain.BaonPeriod
import com.pitakaview.app.domain.LoanDirection
import com.pitakaview.app.domain.MoveForm
import com.pitakaview.app.domain.MoveFormLogic
import com.pitakaview.app.domain.MoveTarget
import com.pitakaview.app.domain.TxPurpose
import com.pitakaview.app.ui.home.friendlyDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate

/**
 * M4 flows: loans, savings goals and baon through the real app shell. A tall screen keeps everything
 * on screen so these are about behaviour; UtangIponBaonPhoneFlowTest covers a phone's size.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h1500dp-xhdpi")
class UtangIponBaonFlowTest : FlowTestBase() {

    private fun chip(text: String) = rule.onNode(hasText(text) and isSelectable())

    private fun addLoan(who: String, direction: LoanDirection, centavos: Long, dueDate: LocalDate? = null, installment: Long? = null): Long = runBlocking {
        container.database.loanDao().insert(
            LoanEntity(
                direction = direction, counterparty = who, principalCentavos = centavos, totalPayableCentavos = centavos, startDate = today(),
                dueDate = dueDate, installmentCentavos = installment, dueDayOfMonth = if (installment != null) dueDate?.dayOfMonth else null,
                createdAt = Instant.now(),
            ),
        )
    }

    private fun addMove(target: MoveTarget, pesos: String) = runBlocking {
        container.ledger.add(MoveFormLogic.toTransaction(MoveForm(pesos, cashId(), today()), target, null, Instant.now()))
    }

    // ---------- Utang ----------

    @Test
    fun aLoanIsBorrowedPaidDownAndSettled() {
        start()
        tapTab("Utang")
        waitFor("No loans")
        tapButton("Add a loan")
        waitFor("How it is paid back")
        field(0).performTextInput("Tita Baby")
        field(1).performTextInput("10000")

        // Saving without saying where the money went is refused: a default must never leave a wallet wrong.
        tapButton("Save")
        waitFor("Pick a wallet, or No wallet")
        assertTrue(loans().isEmpty())

        chip("Cash").performClick()
        tapButton("Save")
        rule.waitUntil(10_000) { loans().size == 1 }
        waitFor("Owed to you") // back on the Utang tab
        assertEquals("the borrowed money is in the wallet", 1_000_000L, cashBalance())
        assertTrue(shows("₱10,000.00"))

        // Pay ₱4,000 of it.
        rule.onNode(hasText("Tita Baby") and hasClickAction()).performClick()
        waitFor("Left to pay")
        tapButton("Add a payment")
        waitFor("Paid from")
        field(0).performTextReplacement("4000") // the form starts at all that is left
        tapButton("Save")
        waitFor("Paid ₱4,000.00 of ₱10,000.00")
        assertEquals(600_000L, cashBalance())
        assertEquals(400_000L, loans().single().paidCentavos)

        // The rest: the form already offers exactly what is left.
        tapButton("Add a payment")
        waitFor("Paid from")
        tapButton("Save")
        waitFor("Paid in full")
        assertEquals(0L, cashBalance())
        rule.onNode(hasText("Add a payment") and hasClickAction()).assertIsNotEnabled() // nothing is left to pay

        back()
        waitFor("Settled")
        assertEquals("loan payments are not spending", 0, entries().count { it.purpose == TxPurpose.EXPENSE })
    }

    @Test
    fun aLoanEntryOpensItsOwnFormFromRecentAndCanBeChangedOrDeleted() {
        start()
        val loanId = addLoan("Kuya Jun", LoanDirection.OWED_TO_ME, 300_000)
        addMove(MoveTarget(TxPurpose.LOAN_PAYMENT, loanId = loanId, loanDirection = LoanDirection.OWED_TO_ME), "1000")
        waitFor("Loan payment") // Home's Recent list

        rule.onNode(hasText("Loan payment") and hasClickAction()).performClick()
        waitFor("Edit payment")
        assertTrue("it says whose loan it is", shows("Kuya Jun"))
        assertTrue("money he pays back arrives in a wallet", shows("Received in"))
        field(0).performTextReplacement("1500")
        tapButton("Save")
        rule.waitUntil(10_000) { loans().single().paidCentavos == 150_000L }
        waitFor("+₱1,500.00")
        assertEquals(150_000L, cashBalance())

        rule.onNode(hasText("Loan payment") and hasClickAction()).performClick()
        waitFor("Edit payment")
        tapButton("Delete entry")
        tapButton("Yes, delete")
        rule.waitUntil(10_000) { loans().single().paidCentavos == 0L }
        assertEquals(0L, cashBalance())
    }

    @Test
    fun aMonthlyLoanShowsItsNextInstallmentHereAndOnHome() {
        start()
        val firstPayment = today().plusDays(3)
        addLoan("SSS loan", LoanDirection.I_OWE, 1_200_000, dueDate = firstPayment, installment = 200_000)

        tapTab("Utang")
        waitFor("₱2,000.00 due ${friendlyDate(firstPayment, today())}")
        assertTrue("what is left is the whole loan", shows("₱12,000.00"))

        tapTab("Home")
        waitFor("You pay · ${friendlyDate(firstPayment, today())}")
        assertTrue(shows("SSS loan"))
        assertTrue("Coming up lists the installment, not the whole debt", shows("₱2,000.00"))
    }

    // ---------- Ipon ----------

    @Test
    fun aGoalIsSavedTowardAndTakenFromWithoutMovingTheWallet() {
        start()
        tapTab("Ipon")
        waitFor("No savings goals")
        tapButton("Add a goal")
        waitFor("Target amount")
        field(0).performTextInput("Laptop")
        field(1).performTextInput("45000")
        tapButton("Save")
        rule.waitUntil(10_000) { goals().size == 1 }
        waitFor("of ₱45,000.00")

        rule.onNode(hasText("Laptop") and hasClickAction()).performClick()
        waitFor("₱45,000.00 to go of ₱45,000.00")
        tapButton("Add to savings")
        waitFor("Taken from")
        assertTrue(shows("It stays in that wallet, set aside for this goal."))
        field(0).performTextInput("5000")
        tapButton("Save")
        waitFor("₱40,000.00 to go of ₱45,000.00")
        assertEquals("only set aside: the wallet's balance does not move", 0L, cashBalance())
        assertEquals(500_000L, goals().single().savedCentavos)

        // More than is saved cannot be taken out.
        tapButton("Take out")
        waitFor("Put into")
        field(0).performTextInput("6000")
        tapButton("Save")
        waitFor("Only ₱5,000.00 is saved in this goal")
        assertEquals(500_000L, goals().single().savedCentavos)
        field(0).performTextReplacement("2000")
        tapButton("Save")
        waitFor("₱42,000.00 to go of ₱45,000.00")
        assertEquals(300_000L, goals().single().savedCentavos)

        back()
        waitFor("Add a goal")
        tapTab("Home")
        waitFor("Your whole wallet")
        assertTrue("Home's Ipon tile follows", shows("₱3,000.00"))
    }

    // ---------- Baon ----------

    @Test
    fun baonIsReceivedSpentFromAndWhatIsLeftFollows() {
        start()
        tapTab("Baon")
        waitFor("No baon set up")
        tapButton("Add a baon")
        waitFor("How much each time")
        field(0).performTextInput("My baon")
        field(1).performTextInput("500")
        tapButton("Save")
        rule.waitUntil(10_000) { allBaon().size == 1 }
        waitFor("Received ₱0.00, spent ₱0.00")

        // Received: the form already holds the usual amount.
        tapButton("Received")
        waitFor("Baon received")
        tapButton("Save")
        waitFor("Received ₱500.00, spent ₱0.00")
        assertEquals(50_000L, cashBalance())

        // Spend: quick entry opens with this baon picked.
        tapButton("Spend")
        waitFor("From baon")
        chip("My baon").assertExists()
        field(0).performTextInput("120")
        chip("Food").performClick()
        tapButton("Save")
        waitFor("Received ₱500.00, spent ₱120.00")
        assertTrue("left = received minus spent", shows("₱380.00"))
        assertEquals(38_000L, cashBalance())
        val spent = entries().first { it.purpose == TxPurpose.EXPENSE }
        assertEquals(
            "the spending is charged to the baon",
            allBaon().single().id,
            runBlocking { container.database.transactionDao().get(spent.id) }?.baonId,
        )

        tapTab("Home")
        waitFor("Your whole wallet")
        assertTrue("Home's Baon tile shows what is left", shows("₱380.00"))
    }

    @Test
    fun baonGivenCountsAgainstWhatWasPlanned() {
        start()
        runBlocking {
            container.database.baonDao().insert(
                BaonEntity(name = "Bunso", direction = BaonDirection.GIVE, amountCentavos = 20_000, period = BaonPeriod.DAILY, startDate = today(), createdAt = Instant.now()),
            )
        }
        tapTab("Baon")
        waitFor("of ₱200.00 planned")
        tapButton("Give")
        waitFor("Baon given")
        field(0).performTextReplacement("150")
        tapButton("Save")
        rule.waitUntil(10_000) { cashBalance() == -15_000L }
        waitFor("Add a baon") // back on the Baon tab
        assertTrue("given so far today", shows("₱150.00"))
    }
}
