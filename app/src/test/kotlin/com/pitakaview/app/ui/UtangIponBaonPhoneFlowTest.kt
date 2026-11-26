package com.pitakaview.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.pitakaview.app.data.db.entity.BaonEntity
import com.pitakaview.app.data.db.entity.LoanEntity
import com.pitakaview.app.domain.BaonDirection
import com.pitakaview.app.domain.BaonPeriod
import com.pitakaview.app.domain.LoanDirection
import com.pitakaview.app.domain.MoveForm
import com.pitakaview.app.domain.MoveFormLogic
import com.pitakaview.app.domain.MoveTarget
import com.pitakaview.app.domain.TxPurpose
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/**
 * The M4 screens on a phone-sized screen (about 390 x 850 dp): the buttons that change or remove
 * something can be reached, and are not hidden under the Save bar.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h850dp-xhdpi")
class UtangIponBaonPhoneFlowTest : FlowTestBase() {

    private fun scrollTo(text: String) = rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))

    private fun bounds(text: String) = rule.onNode(hasText(text) and hasClickAction()).fetchSemanticsNode().boundsInRoot

    private fun addLoanWithPayment(): Long = runBlocking {
        val loanId = container.database.loanDao().insert(
            LoanEntity(
                direction = LoanDirection.I_OWE, counterparty = "Tita Baby", principalCentavos = 1_000_000, totalPayableCentavos = 1_000_000,
                startDate = today(), createdAt = Instant.now(),
            ),
        )
        val target = MoveTarget(TxPurpose.LOAN_PAYMENT, loanId = loanId, loanDirection = LoanDirection.I_OWE)
        container.ledger.add(MoveFormLogic.toTransaction(MoveForm("2000", cashId(), today()), target, null, Instant.now()))
        loanId
    }

    @Test
    fun aLoanPaymentIsDeletedFromItsOwnFormWithTheQuestionInTheFooter() {
        start()
        addLoanWithPayment()
        tapTab("Utang")
        waitFor("Owed to you")
        rule.onNode(hasText("Tita Baby") and hasClickAction()).performClick()
        waitFor("Left to pay")

        scrollTo("Loan payment")
        rule.onNode(hasText("Loan payment") and hasClickAction()).performClick()
        waitFor("Edit payment")

        scrollTo("Delete entry")
        assertTrue("Delete entry must not sit under the Save bar", bounds("Delete entry").bottom <= bounds("Save").top)
        tapButton("Delete entry")
        rule.onNode(hasText("Yes, delete") and hasClickAction()).assertIsDisplayed().performClick()
        rule.waitUntil(10_000) { loans().single().paidCentavos == 0L }
    }

    @Test
    fun aLoanIsMarkedSettledFromTheBottomOfItsForm() {
        start()
        addLoanWithPayment()
        tapTab("Utang")
        waitFor("Owed to you")
        rule.onNode(hasText("Tita Baby") and hasClickAction()).performClick()
        waitFor("Left to pay")
        rule.onNode(hasText("Edit loan") and hasClickAction()).assertIsDisplayed().performClick()
        waitFor("How it is paid back")

        scrollTo("Mark as settled")
        assertTrue("Mark as settled must not sit under the Save bar", bounds("Mark as settled").bottom <= bounds("Save").top)
        tapButton("Mark as settled")
        rule.waitUntil(10_000) { loans().single().loan.closedAt != null }
        waitFor("Marked settled") // back on the loan's own screen
    }

    /**
     * Found on the owner's phone (2026-11-26): "Spend" on a baon opened quick entry with the baon picked,
     * but the choice sat below the keyboard, so nothing on screen said the spending came from that baon.
     * The line under the title now says it, where it cannot scroll away.
     */
    @Test
    fun spendingFromABaonSaysSoUnderTheTitle() {
        start()
        runBlocking {
            container.database.baonDao().insert(
                BaonEntity(name = "My baon", direction = BaonDirection.RECEIVE, amountCentavos = 50_000, period = BaonPeriod.WEEKLY, startDate = today(), createdAt = Instant.now()),
            )
        }
        tapTab("Baon")
        waitFor("Received ₱0.00, spent ₱0.00")
        tapButton("Spend")
        waitFor("Add entry")
        rule.onNodeWithText("From baon · My baon").assertIsDisplayed()

        // Unpicking the baon takes the line away again.
        scrollTo("Not from baon")
        rule.onNode(hasText("Not from baon") and isSelectable()).performClick()
        rule.waitUntil(10_000) { !shows("From baon · My baon") }
    }

    @Test
    fun baonIsReceivedWithoutScrolling() {
        start()
        runBlocking {
            container.database.baonDao().insert(
                BaonEntity(name = "My baon", direction = BaonDirection.RECEIVE, amountCentavos = 50_000, period = BaonPeriod.WEEKLY, startDate = today(), createdAt = Instant.now()),
            )
        }
        tapTab("Baon")
        waitFor("Received ₱0.00, spent ₱0.00")
        rule.onNode(hasText("Received") and hasClickAction()).assertIsDisplayed().performClick()
        waitFor("Baon received")
        rule.onNode(hasText("Save") and hasClickAction()).assertIsDisplayed().performClick()
        rule.waitUntil(10_000) { cashBalance() == 50_000L }
        assertEquals(TxPurpose.BAON_RECEIVED, entries().single().purpose)
    }
}
