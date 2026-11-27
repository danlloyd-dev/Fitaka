package com.pitakaview.app.ui

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.pitakaview.app.domain.CategoryKind
import com.pitakaview.app.domain.TxPurpose
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
}
