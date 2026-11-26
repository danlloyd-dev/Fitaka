package com.pitakaview.app.ui

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.pitakaview.app.domain.TxPurpose
import com.pitakaview.app.domain.WalletProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * M1 flows: quick entry (add, edit, delete), wallets and categories. A tall screen keeps every field on
 * screen so these tests are about behaviour; PhoneSizeFlowTest covers reachability on a real phone size.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h1500dp-xhdpi")
class MoneyFlowTest : FlowTestBase() {

    @Test
    fun addEditAndDeleteAnExpense() {
        start()
        tapButton("Add transaction")
        waitFor("Food")
        field(0).performTextInput("250")
        rule.onNode(hasText("Food") and isSelectable()).performClick()
        tapButton("Save")

        // Back on Home: the entry is in the database and every number on screen follows it.
        waitFor("−₱250.00")
        val saved = entries().single()
        assertEquals(TxPurpose.EXPENSE, saved.purpose)
        assertEquals(25_000L, saved.amountCentavos)
        assertEquals("Food", saved.categoryName)
        assertEquals("Cash", saved.fromWalletName)
        assertEquals(-25_000L, wallets().single().balanceCentavos)

        // Edit it from Recent.
        tapButton("Food")
        waitFor("Edit entry")
        field(0).performTextReplacement("300.50")
        tapButton("Save")
        waitFor("−₱300.50")
        assertEquals(30_050L, entries().single().amountCentavos)
        assertEquals("still one row, not a second one", 1, entries().size)

        // Delete it, with the confirmation step.
        tapButton("Food")
        waitFor("Delete entry")
        tapButton("Delete entry")
        tapButton("Yes, delete")
        waitFor("No entries yet")
        assertTrue(entries().isEmpty())
        assertEquals(0L, wallets().single().balanceCentavos)
    }

    @Test
    fun addIncomeFromTheKitaTab() {
        start()
        rule.onNode(hasText("Kita") and isSelectable()).performClick() // the Kita tab
        waitFor("Income & paydays")
        tapButton("Add transaction")
        waitFor("Salary") // quick entry opens on Kita, offering income categories
        field(0).performTextInput("15000")
        rule.onNode(hasText("Salary") and isSelectable()).performClick()
        tapButton("Save")

        rule.waitUntil(10_000) { entries().size == 1 }
        val earned = entries().single()
        assertEquals(TxPurpose.INCOME, earned.purpose)
        assertEquals("Salary", earned.categoryName)
        assertEquals("Cash", earned.toWalletName)
        assertEquals(1_500_000L, wallets().single().balanceCentavos)
    }

    @Test
    fun savingAnIncompleteEntryExplainsWhatIsMissingAndSavesNothing() {
        start()
        tapButton("Add transaction")
        waitFor("Food")
        tapButton("Save")
        waitFor("Enter an amount above")
        assertTrue(shows("Pick a category"))
        assertTrue("nothing was saved", entries().isEmpty())
    }

    @Test
    fun addAWalletThenMoveMoneyIntoIt() {
        start()
        rule.onNodeWithContentDescription("Settings").performClick()
        tapButton("Wallets")
        waitFor("Where your money sits")
        tapButton("Add wallet")
        waitFor("What kind?")
        rule.onNode(hasText("GCash") and isSelectable()).performClick()
        field(1).performTextInput("1500")
        tapButton("Save")

        waitFor("₱1,500.00")
        val gcash = wallets().single { it.wallet.name == "GCash" }
        assertEquals(150_000L, gcash.balanceCentavos)
        assertEquals("imports will match on this", WalletProvider.GCASH, gcash.wallet.provider)

        back() // Wallets -> Settings
        back() // Settings -> Home
        waitFor("Your whole wallet")

        // A transfer moves money between wallets without changing the total.
        tapButton("Add transaction")
        waitFor("Food")
        rule.onNode(hasText("Transfer") and isSelectable()).performClick()
        waitFor("From")
        field(0).performTextInput("500")
        tapButton("Save")
        rule.waitUntil(10_000) { entries().size == 1 }
        val moved = entries().single()
        assertEquals(TxPurpose.TRANSFER, moved.purpose)
        assertEquals(50_000L, moved.amountCentavos)
        assertEquals(setOf("Cash", "GCash"), setOf(moved.fromWalletName, moved.toWalletName))
        assertEquals("total unchanged by a transfer", 150_000L, wallets().sumOf { it.balanceCentavos })
    }

    @Test
    fun correctingAWalletBalanceCreatesNoEntry() {
        start()
        rule.onNodeWithContentDescription("Settings").performClick()
        tapButton("Wallets")
        waitFor("Where your money sits")
        tapButton("Cash")
        waitFor("Edit wallet")
        field(1).performTextInput("750")
        tapButton("Save")
        waitFor("₱750.00")
        assertEquals(75_000L, wallets().single().balanceCentavos)
        assertTrue("a correction is not income", entries().isEmpty())
    }

    @Test
    fun addACategoryAndFindItInQuickEntry() {
        start()
        rule.onNodeWithContentDescription("Settings").performClick()
        tapButton("Categories")
        waitFor("For Gastos and Kita")
        tapButton("Add Gastos category")
        waitFor("e.g. Coffee")
        field(0).performTextInput("Coffee")
        tapButton("Save")
        // Wait for the list screen itself: "Coffee" is already on screen in the box it was typed into,
        // so waiting for that word alone raced the save and made this test pass or fail by timing.
        waitFor("For Gastos and Kita")
        rule.waitUntil(10_000) { rule.onAllNodes(hasText("Coffee") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }

        // The same name again is refused with a reason.
        tapButton("Add Gastos category")
        waitFor("e.g. Coffee")
        field(0).performTextInput("coffee")
        tapButton("Save")
        waitFor("You already have this category")
        back() // editor -> Categories
        back() // Categories -> Settings
        back() // Settings -> Home
        waitFor("Your whole wallet")

        tapButton("Add transaction")
        waitFor("Coffee")
        rule.onNode(hasText("Coffee") and isSelectable()).assertExists()
    }
}
