package com.pitakaview.app.ui

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.pitakaview.app.domain.CategoryKind
import com.pitakaview.app.ui.home.RestoreFailedText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** The rough-edge fixes, tapped through on the real screens at the size of the owner's phone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h850dp-xhdpi")
class RoughEdgesFlowTest : FlowTestBase() {
    private fun expenseCategories() = runBlocking { container.database.categoryDao().observeAll(CategoryKind.EXPENSE).first() }

    private fun icon(label: String) = rule.onNode(hasContentDescription(label) and isSelectable())

    private fun openCategories() {
        rule.onNodeWithContentDescription("Settings").performClick()
        waitFor("Money set-up")
        tapButton("Categories")
        waitFor("For Gastos and Kita")
    }

    @Test
    fun aRowOnHomeSaysItsNoteAndKnowsItsCategorysIcon() {
        // The app is opened first: the Cash wallet and the starter categories are made on first start,
        // and an entry added before that had nowhere to go (which made this test pass or fail by timing).
        start()
        addExpense(12_500, category = "Food", note = "lugaw")
        addExpense(8_000, category = "Food", note = "kape sa kanto")
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Recent"))

        // Two entries of the same category and wallet on the same day: the note is what tells them apart.
        waitFor("Cash · lugaw · Today")
        waitFor("Cash · kape sa kanto · Today")
        assertEquals("the row knows which icon its category wears", listOf("food", "food"), entries().map { it.categoryIconKey })
    }

    @Test
    fun aCategoryIsGivenAnIconAndKeepsItWhenRenamed() {
        start()
        openCategories()
        tapButton("Add Gastos category")
        waitFor("e.g. Coffee")
        // Until one is picked, a new category wears the three dots.
        icon("Three dots").assertIsSelected()
        field(0).performTextInput("Kape")
        icon("Cup").performClick()
        icon("Cup").assertIsSelected()
        icon("Three dots").assertIsNotSelected()
        tapButton("Save")
        waitFor("For Gastos and Kita")
        rule.waitUntil(10_000) { expenseCategories().any { it.name == "Kape" } }
        assertEquals("coffee", expenseCategories().single { it.name == "Kape" }.iconKey)

        // Opened again, it shows the icon it has; changing the name alone leaves the icon alone.
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Kape"))
        rule.onNode(hasText("Kape") and hasClickAction()).performClick()
        waitFor("Edit category")
        icon("Cup").assertIsSelected()
        field(0).performTextInput(" at merienda")
        tapButton("Save")
        rule.waitUntil(10_000) { expenseCategories().any { it.name == "Kape at merienda" } }
        assertEquals("coffee", expenseCategories().single { it.name == "Kape at merienda" }.iconKey)

        // And the icon can be changed on its own.
        waitFor("For Gastos and Kita")
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Kape at merienda"))
        rule.onNode(hasText("Kape at merienda") and hasClickAction()).performClick()
        waitFor("Edit category")
        icon("Game controller").performClick()
        tapButton("Save")
        rule.waitUntil(10_000) { expenseCategories().single { it.name == "Kape at merienda" }.iconKey == "fun" }
        assertEquals("the starter categories keep theirs", "food", expenseCategories().single { it.name == "Food" }.iconKey)
    }

    @Test
    fun afterARestoreThatBrokeOffHomeSaysSoUntilTheOwnerHasSeenIt() {
        // What the Backup screen writes down just before it restarts the app.
        runBlocking { container.settings.setRestoreFailed(true) }
        start()
        waitFor(RestoreFailedText.TITLE)
        assertTrue(shows(RestoreFailedText.BODY))

        tapButton("OK")
        rule.waitUntil(10_000) { !shows(RestoreFailedText.TITLE) }
        assertFalse("it is said once, not on every start", runBlocking { container.settings.restoreFailed.first() })
    }

    @Test
    fun withoutAFailedRestoreHomeSaysNothingAboutOne() {
        start()
        assertFalse(shows(RestoreFailedText.TITLE))
    }

    @Test
    fun searchFindsAWordWithEnyeWhicheverWayItIsTyped() {
        start()
        addExpense(18_000, note = "Piña colada")
        addExpense(9_000, note = "pixa") // one letter off: the database alone would let this through
        addExpense(7_000, note = "LAS PIÑAS toll")
        rule.onNodeWithContentDescription("Search").performClick()
        waitFor("Type to search")

        field(0).performTextInput("PIÑA")
        waitFor("Found 2 entries")
        assertTrue(shows("Piña colada"))
        assertTrue(shows("LAS PIÑAS toll"))
        assertFalse(shows("pixa"))
    }
}
