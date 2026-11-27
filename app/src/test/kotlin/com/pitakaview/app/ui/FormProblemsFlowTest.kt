package com.pitakaview.app.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A form's problems where the owner can see them. Found in the review before 1.0 (2026-11-27): Save with
 * the field in trouble scrolled out of view seemed to do nothing, and a message stayed after the mistake
 * was put right. On a short screen, so the forms scroll.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h560dp-xhdpi")
class FormProblemsFlowTest : FlowTestBase() {
    private fun scrollDownTo(text: String) = rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))

    private fun settle() {
        rule.waitForIdle()
        Thread.sleep(300)
        rule.waitForIdle()
    }

    private fun bounds(text: String): Rect? =
        rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().firstOrNull()?.boundsInRoot

    /**
     * Whether [text] is where the owner can see it: below the screen's title (the top bar lies over the
     * list) and above Save (the footer). A node the list has let go of, or one under the bar, is not.
     */
    private fun inView(text: String, title: String): Boolean {
        val node = bounds(text) ?: return false
        val top = requireNotNull(rule.onAllNodes(hasText(title) and hasClickAction().not()).fetchSemanticsNodes().firstOrNull()) { "no title $title" }.boundsInRoot.bottom
        val bottom = rule.onNode(hasText("Save") and hasClickAction()).fetchSemanticsNode().boundsInRoot.top
        return node.top >= top && node.bottom <= bottom && node.height > 0f
    }

    @Test
    fun aRefusedSaveBringsTheFieldInTroubleIntoViewAndItsMessageGoesOnceItIsPutRight() {
        start()
        tapButton("Add transaction")
        waitFor("Food")
        rule.onNode(hasText("Food") and isSelectable()).performClick()
        // Down to the note, far below the amount, which is left empty.
        scrollDownTo("Note")
        settle()
        assertFalse("the amount is out of view to begin with", inView("Amount", "Add entry"))

        tapButton("Save")
        settle()
        assertTrue("Save brought the amount and its message into view", inView("Enter an amount above ₱0.00", "Add entry"))
        assertEquals("nothing was stored", 0, entries().size)

        // Typing the amount puts it right: the message goes at once, before any second Save.
        field(0).performTextInput("250")
        rule.waitUntil(5_000) { !shows("Enter an amount above ₱0.00") }
        tapButton("Save")
        rule.waitUntil(10_000) { entries().size == 1 }
    }

    @Test
    fun aNewLoanSavedWithNoNameShowsWhereTheNameGoes() {
        start()
        tapTab("Utang")
        waitFor("No loans")
        tapButton("Add a loan")
        waitFor("From whom")
        field(1).performTextInput("10000")
        scrollDownTo("Note")
        settle()
        assertFalse("the name is out of view to begin with", inView("From whom", "Add a loan"))

        tapButton("Save")
        settle()
        assertTrue(inView("Say who the loan is with", "Add a loan"))
        assertTrue(loans().isEmpty())

        field(0).performTextInput("Tita Baby")
        rule.waitUntil(5_000) { !shows("Say who the loan is with") }
    }

    @Test
    @Config(qualifiers = "w390dp-h1500dp-xhdpi")
    fun aCategoryNameAlreadyTakenStopsBeingFlaggedOnceTheNameChanges() {
        start()
        tapDescribed("Settings")
        waitFor("Money set-up")
        tapButton("Categories")
        waitFor("For Gastos and Kita")
        tapButton("Add Gastos category")
        waitFor("Name")
        field(0).performTextInput("Food")
        tapButton("Save")
        waitFor("You already have this category")

        field(0).performTextReplacement("Food court")
        rule.waitUntil(5_000) { !shows("You already have this category") }
        tapButton("Save")
        waitFor("For Gastos and Kita")
    }
}
