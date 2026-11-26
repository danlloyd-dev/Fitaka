package com.pitakaview.app.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The M3 screens on a phone-sized screen (the owner's phone is about 390 x 850 dp): what must be
 * reachable is reachable, and a tap lands on what was aimed at, not on a bar drawn over it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h850dp-xhdpi")
class SectionsPhoneFlowTest : FlowTestBase() {

    private fun scrollTo(text: String) = rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))

    @Test
    fun theSummaryOpensFromHomeOnAPhone() {
        start()
        addExpense(25_000)
        rule.waitUntil(10_000) { entries().size == 1 }
        scrollTo("Summary")
        rule.onNode(hasText("Summary") and hasClickAction()).assertIsDisplayed().performClick()
        waitFor("Left over in")
        // The chart itself is far down the page; it must be reachable by scrolling.
        scrollTo("The last 6 months")
        waitFor("Money out")
    }

    @Test
    fun aDuePaydayIsReceivedWithoutScrolling() {
        start()
        addPaydayDueToday()
        tapTab("Kita")
        waitFor("Due today")

        rule.onNode(hasText("Received") and hasClickAction()).assertIsDisplayed().performClick()
        waitFor("Pay received · Sahod")
        rule.onNode(hasText("Save") and hasClickAction()).assertIsDisplayed().performClick()
        rule.waitUntil(10_000) { entries().size == 1 }
        assertEquals(1_500_000L, entries().single().amountCentavos)
    }

    @Test
    fun removingAPaydayIsReachableAndAskedInTheFooter() {
        start()
        val scheduleId = addPaydayDueToday()
        tapTab("Kita")
        waitFor("Due today")
        rule.onNode(hasText("Sahod") and hasClickAction()).performClick()
        waitFor("Edit payday")

        scrollTo("Remove payday")
        val remove = rule.onNode(hasText("Remove payday") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        val save = rule.onNode(hasText("Save") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        assertTrue("Remove payday ($remove) must not sit under the Save bar ($save)", remove.bottom <= save.top)

        tapButton("Remove payday")
        rule.onNode(hasText("Yes, remove") and hasClickAction()).assertIsDisplayed().performClick()
        rule.waitUntil(10_000) { runBlocking { container.database.payScheduleDao().get(scheduleId) } == null }
    }

    @Test
    fun theLastEntryOfALongDayScrollsClearOfTheTabBarAndOpens() {
        start()
        // Inserted oldest first, so "entry 0" is the lowest row on the page.
        repeat(6) { i -> addExpense(1_000L * (i + 1), note = "entry $i") }
        tapTab("Gastos")
        waitFor("Where it went")

        // "Scroll to" stops as soon as the row is inside the list's area, which includes the strip under the
        // frosted tab bar. Scroll on to the very end, as a person would, and check the row is above the bar.
        scrollTo("Cash · entry 0")
        rule.onNode(hasScrollToNodeAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
        rule.waitForIdle()
        val row = rule.onNode(hasText("Cash · entry 0") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        val tabBarTop = rule.onNode(hasText("Gastos") and isSelectable()).fetchSemanticsNode().boundsInRoot.top
        assertTrue("the last row ($row) must end above the tab bar (top at $tabBarTop)", row.bottom <= tabBarTop)

        // A tap that landed on the tab bar instead would switch tabs and never reach the editor.
        rule.onNode(hasText("Cash · entry 0") and hasClickAction()).assertIsDisplayed().performClick()
        waitFor("Edit entry")
    }
}
