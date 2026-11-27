package com.pitakaview.app.ui

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.pitakaview.app.data.db.entity.SavingsGoalEntity
import com.pitakaview.app.data.db.entity.WalletEntity
import com.pitakaview.app.domain.CategoryKind
import com.pitakaview.app.domain.TxPurpose
import com.pitakaview.app.domain.WalletType
import java.time.Instant
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

/**
 * Bugs found by the review before 1.0 (2026-11-27) that gave a wrong number or stopped the app, each
 * through the real screens. The import's part of the category bug is in ImportFlowTest, which has the
 * stand-in for Android's file picker.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h2400dp-xhdpi")
class ReviewBugsFlowTest : FlowTestBase() {
    private fun chip(text: String) = rule.onNode(hasText(text) and isSelectable())

    private fun inUse(kind: CategoryKind) = runBlocking { container.database.categoryDao().observeActive(kind).first() }

    private fun openCategories() {
        tapDescribed("Settings")
        waitFor("Money set-up")
        tapButton("Categories")
        waitFor("For Gastos and Kita")
    }

    // ---------- a loan and the money that moved ----------

    @Test
    fun correctingALoansAmountAndDayCorrectsTheWallet() {
        start()
        tapTab("Utang")
        waitFor("No loans")
        tapButton("Add a loan")
        waitFor("How it is paid back")
        field(0).performTextInput("Tita Baby")
        field(1).performTextInput("10000")
        chip("Cash").performClick()
        tapButton("Save")
        rule.waitUntil(10_000) { loans().size == 1 }
        waitFor("Owed to you")
        assertEquals(1_000_000L, cashBalance())

        // It was 12,000, and it was yesterday.
        rule.onNode(hasText("Tita Baby") and hasClickAction()).performClick()
        waitFor("Left to pay")
        tapButton("Edit loan")
        waitFor("Date of the loan")
        field(1).performTextReplacement("12000")
        chip("Yesterday").performClick()
        tapButton("Save")
        rule.waitUntil(10_000) { loans().single().loan.principalCentavos == 1_200_000L }
        waitFor("Left to pay")

        assertEquals("the wallet has what was really borrowed", 1_200_000L, cashBalance())
        val moved = entries().single { it.purpose == TxPurpose.LOAN_START }
        assertEquals(1_200_000L, moved.amountCentavos)
        assertEquals(today().minusDays(1), moved.occurredOn)
    }

    // ---------- a day's heading ----------

    @Test
    fun aGoalsDayWithMoneyInAndOutIsNotHeadedByTheirSum() {
        start()
        tapTab("Ipon")
        waitFor("No savings goals")
        tapButton("Add a goal")
        waitFor("Target amount")
        field(0).performTextInput("Laptop")
        field(1).performTextInput("45000")
        tapButton("Save")
        rule.waitUntil(10_000) { goals().size == 1 }
        val goal = goals().single().goalId
        runBlocking {
            container.ledger.depositToGoal(goal, cashId(), 50_000, today(), Instant.now())
            container.ledger.withdrawFromGoal(goal, cashId(), 20_000, today(), Instant.now())
        }

        rule.onNode(hasText("Laptop") and hasClickAction()).performClick()
        waitFor("₱500.00")
        waitFor("₱200.00")
        assertFalse("500.00 in and 200.00 out are not 700.00 of anything", shows("₱700.00"))
    }

    // ---------- one category of each kind stays in use ----------

    @Test
    fun theLastCategoryOfAKindCannotBeHidden() {
        start()
        // Every Kita category but one is hidden, as the screen lets the owner do one at a time.
        val last = inUse(CategoryKind.INCOME).first { it.name != "Other" }
        runBlocking {
            val dao = container.database.categoryDao()
            inUse(CategoryKind.INCOME).filter { it.id != last.id }.forEach { dao.update(it.copy(isArchived = true)) }
        }
        openCategories()
        rule.onNode(hasText(last.name) and hasClickAction()).performClick()
        waitFor("Edit category")
        tapButton("Hide category")
        waitFor("Keep at least one Kita category in use")

        assertEquals(listOf(last.name), inUse(CategoryKind.INCOME).map { it.name })
        assertTrue("still on the category's own screen", shows("Edit category"))
    }

    @Test
    fun aCategoryCanBeHiddenWhileAnotherOfItsKindIsInUse() {
        start()
        val before = inUse(CategoryKind.EXPENSE)
        val one = before.first { it.name != "Other" }
        openCategories()
        rule.onNode(hasText(one.name) and hasClickAction()).performClick()
        waitFor("Edit category")
        tapButton("Hide category")
        rule.waitUntil(10_000) { inUse(CategoryKind.EXPENSE).size == before.size - 1 }
        waitFor("For Gastos and Kita")
        assertFalse(shows("Keep at least one"))
    }

    // ---------- savings goals ----------

    private fun goalSaved(): Long = goals().single().savedCentavos

    private fun openGoal(name: String) {
        tapTab("Ipon")
        rule.onNode(hasText(name) and hasClickAction()).performClick()
        waitFor("Take out")
    }

    /**
     * 500.00 saved and 300.00 of it taken back out: the deposit can no longer go below 300.00, nor be
     * deleted. Until 2026-11-27 both were possible and the goal then had less than nothing in it.
     */
    @Test
    fun aDepositWhoseMoneyWasTakenOutCannotBeLoweredPastThatNorDeleted() {
        start()
        val goal = runBlocking { container.database.savingsGoalDao().insert(SavingsGoalEntity(name = "Laptop", createdAt = Instant.now())) }
        runBlocking {
            container.ledger.depositToGoal(goal, cashId(), 50_000, today(), Instant.now())
            container.ledger.withdrawFromGoal(goal, cashId(), 30_000, today(), Instant.now())
        }
        openGoal("Laptop")
        rule.onNode(hasText("Added to savings") and hasClickAction()).performClick()
        waitFor("Edit savings entry")
        assertTrue("it says why it cannot be deleted", shows("This cannot be deleted: some of it has already been taken out of this goal."))
        assertEquals("and offers no way to", 0, rule.onAllNodes(hasText("Delete entry") and hasClickAction()).fetchSemanticsNodes().size)

        field(0).performTextReplacement("200")
        tapButton("Save")
        waitFor("At least ₱300.00: the rest has already been taken out of this goal")
        assertEquals("nothing changed", 20_000L, goalSaved())

        field(0).performTextReplacement("300")
        tapButton("Save")
        waitFor("Take out")
        assertEquals(0L, goalSaved())
    }

    /**
     * A goal kept in MariBank: once something is saved into it, its wallet is shown and not offered. Until
     * 2026-11-27 it could be changed, and "Take out" then took the money from a wallet that never had it.
     */
    @Test
    fun aGoalsWalletIsFixedOnceSomethingIsSavedIntoIt() {
        start()
        val mari = runBlocking { container.database.walletDao().insert(WalletEntity(name = "MariBank", type = WalletType.BANK, createdAt = Instant.now())) }
        val goal = runBlocking { container.database.savingsGoalDao().insert(SavingsGoalEntity(name = "Laptop", walletId = mari, createdAt = Instant.now())) }

        // Nothing saved yet: the wallet can still be chosen.
        openGoal("Laptop")
        tapButton("Edit goal")
        waitFor("Where the savings are kept")
        rule.onNode(hasText("No wallet") and isSelectable()).assertExists()
        back()
        waitFor("Take out")

        runBlocking { container.ledger.depositToGoal(goal, cashId(), 50_000, today(), Instant.now()) }
        tapButton("Edit goal")
        waitFor("Where the savings are kept")
        assertTrue(shows("This stays as it is once something is saved for this goal"))
        assertEquals("no wallet to pick", 0, rule.onAllNodes(hasText("No wallet") and isSelectable()).fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodes(hasText("Cash") and isSelectable()).fetchSemanticsNodes().size)
        field(0).performTextReplacement("Laptop for school")
        tapButton("Save")
        rule.waitUntil(10_000) { goals().single().name == "Laptop for school" }
        assertEquals("still kept in MariBank", mari, goals().single().walletId)
    }
}
