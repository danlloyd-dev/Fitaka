package com.pitakaview.app.ui

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.pitakaview.app.domain.PayFrequency
import com.pitakaview.app.domain.TxPurpose
import com.pitakaview.app.ui.home.friendlyDate
import com.pitakaview.app.ui.ledger.monthName
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * M3 flows: the Gastos and Kita tabs, paydays and search, through the real app shell. A tall screen
 * keeps everything on screen so these are about behaviour; SectionsPhoneFlowTest covers a phone's size.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h1500dp-xhdpi")
class SectionsFlowTest : FlowTestBase() {

    private fun payScheduleOf(entryId: Long) = runBlocking { container.database.transactionDao().get(entryId) }?.payScheduleId

    @Test
    fun gastosShowsTheMonthByCategoryAndByDay() {
        start()
        addExpense(25_000, note = "lunch")
        addExpense(10_000, category = "Transport")
        tapTab("Gastos")

        waitFor("₱350.00") // the month's total
        assertTrue(shows("Spent in ${monthName(YearMonth.from(today()))}"))
        assertTrue(shows("2 entries"))
        assertTrue(shows("Where it went"))
        assertTrue("Food is 250 of 350", shows("71%"))
        assertTrue("Transport is the rest", shows("29%"))
        assertTrue("the note sits under its entry", shows("Cash · lunch"))

        // Another month has nothing and says so; one tap comes back.
        tapDescribed("Previous month")
        waitFor("No spending in")
        assertFalse(shows("Where it went"))
        tapButton("Back to this month")
        waitFor("₱350.00")

        // An entry opens for editing.
        rule.onNode(hasText("Food") and hasClickAction()).performClick()
        waitFor("Edit entry")
    }

    @Test
    fun aPaydayIsSetUpReceivedAndThenWaitsForTheNextOne() {
        start()
        tapTab("Kita")
        waitFor("No paydays set up")
        tapButton("Add a payday")
        waitFor("How often")
        field(0).performTextInput("Sahod")
        field(1).performTextInput("15000")
        // Weekly on today's weekday, so the payday is due today whatever day this test runs.
        rule.onNode(hasText("Every week") and isSelectable()).performClick()
        rule.onNode(hasText(today().dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)) and isSelectable()).performClick()
        tapButton("Save")

        waitFor("Due today")
        val schedule = paydays().single()
        assertEquals("Sahod", schedule.name)
        assertEquals(1_500_000L, schedule.expectedAmountCentavos)
        assertEquals(PayFrequency.WEEKLY to today().dayOfWeek, schedule.frequency to schedule.dayOfWeek)
        assertEquals("it lands in the first wallet unless another is picked", wallets().single().wallet.id, schedule.walletId)

        // Home lists it under "Coming up".
        tapTab("Home")
        waitFor("Payday · Today")

        // Received: quick entry opens already filled in, so Save is the only other tap.
        tapTab("Kita")
        waitFor("Due today")
        tapButton("Received")
        waitFor("Pay received · Sahod")
        assertFalse("it is income, so the kind is not offered", shows("Transfer"))
        tapButton("Save")

        waitFor("Next: ")
        val pay = entries().single()
        assertEquals(TxPurpose.INCOME, pay.purpose)
        assertEquals(1_500_000L, pay.amountCentavos)
        assertEquals("Salary" to "Cash", pay.categoryName to pay.toWalletName)
        assertEquals("the entry remembers which payday it was for", schedule.id, payScheduleOf(pay.id))
        assertFalse(shows("Due today"))
        assertTrue("the month's income shows it", shows("Income in ${monthName(YearMonth.from(today()))}"))
        assertEquals(1_500_000L, wallets().single().balanceCentavos)

        // Home now waits for next week's payday, not today's.
        tapTab("Home")
        waitFor("Payday · " + friendlyDate(today().plusDays(7), today()))
        assertFalse(shows("Payday · Today"))
    }

    @Test
    fun aPaydayCanBeRenamedAndRemovedWithoutLosingItsIncome() {
        start()
        val scheduleId = addPaydayDueToday()
        tapTab("Kita")
        waitFor("Due today")
        tapButton("Received")
        waitFor("Pay received · Sahod")
        tapButton("Save")
        waitFor("Next: ")

        rule.onNode(hasText("Sahod") and hasClickAction()).performClick()
        waitFor("Edit payday")
        field(0).performTextReplacement("Sweldo")
        tapButton("Save")
        rule.waitUntil(10_000) { paydays().single().name == "Sweldo" }
        waitFor("Add a payday") // back on the Kita tab
        assertEquals("renaming did not make a second payday", scheduleId, paydays().single().id)

        rule.onNode(hasText("Sweldo") and hasClickAction()).performClick()
        waitFor("Edit payday")
        tapButton("Remove payday")
        tapButton("Yes, remove")
        waitFor("No paydays set up")
        assertTrue(paydays().isEmpty())
        val pay = entries().single()
        assertEquals("the income already logged still counts", 1_500_000L, pay.amountCentavos)
        assertNull("it only stops being linked to the payday", payScheduleOf(pay.id))
    }

    @Test
    fun anIncompletePaydayExplainsWhatIsMissingAndSavesNothing() {
        start()
        tapTab("Kita")
        waitFor("No paydays set up")
        tapButton("Add a payday")
        waitFor("How often")
        field(2).performTextReplacement("40") // there is no 40th of the month
        tapButton("Save")

        waitFor("Give this payday a name")
        assertTrue(shows("Enter an amount above"))
        assertTrue(shows("Type a day from 1 to 31"))
        assertTrue("nothing was saved", paydays().isEmpty())
    }

    @Test
    fun searchFindsEntriesByWordsAndByAmountAndOpensThem() {
        start()
        addExpense(25_000, note = "lunch with Tita")
        addExpense(9_900, category = "Transport", note = "jeep")
        waitFor("−₱250.00")

        tapDescribed("Search")
        waitFor("Type to search")
        field(0).performTextInput("tita")
        waitFor("Found 1 entry")
        assertTrue(shows("Cash · lunch with Tita"))
        assertFalse(shows("Cash · jeep"))

        field(0).performTextReplacement("99") // an amount: ₱99.00
        waitFor("Cash · jeep")
        assertFalse(shows("lunch with Tita"))

        field(0).performTextReplacement("zzz")
        waitFor("Nothing found")

        field(0).performTextReplacement("food") // a category, in any letter case
        waitFor("Cash · lunch with Tita")
        rule.onNode(hasText("Food") and hasClickAction()).performClick()
        waitFor("Edit entry")
    }
}
