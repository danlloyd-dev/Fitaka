package com.pitakaview.app.ui

import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import com.pitakaview.app.domain.CategoryKind
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
import java.io.File

/**
 * A button tapped twice does its work once. Two things found in the review before 1.0 (2026-11-27), each
 * proven then by a test like these: a second tap on Save stored the entry twice (one ₱250.00 expense
 * became two, and Cash lost ₱500.00), and a second tap on Back closed the screen underneath as well,
 * which from Home emptied the screen stack and stopped the app.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h1500dp-xhdpi")
class TapTwiceFlowTest : FlowTestBase() {
    private val save = hasText("Save") and hasClickAction()

    private fun fillAnExpense() {
        start()
        tapButton("Add transaction")
        waitFor("Food")
        field(0).performTextInput("250")
        rule.onNode(hasText("Food") and isSelectable()).performClick()
    }

    private fun settle() {
        rule.waitForIdle()
        Thread.sleep(400) // the database and settings writes finish in real time
        rule.waitForIdle()
    }

    @Test
    fun saveTappedTwiceAtOnceStoresOneEntry() {
        fillAnExpense()
        rule.onNode(save).performTouchInput {
            click()
            click()
        }
        settle()
        assertEquals("one expense was typed, one is stored", 1, entries().size)
        assertEquals(-25_000L, cashBalance())
    }

    @Test
    fun aSecondTapOnSaveWhileTheScreenIsLeavingStoresNothingMore() {
        fillAnExpense()
        rule.mainClock.autoAdvance = false
        rule.onNode(save).performClick()
        var tappedWhileLeaving = false
        for (frame in 1..90) {
            Thread.sleep(20) // the database and settings writes finish in real time
            rule.mainClock.advanceTimeByFrame()
            // Home is on its way in, so the save has finished and the editor is on its way out, and its
            // Save is still there to be tapped. (While the editor is the only screen, Home is not drawn.)
            if (!tappedWhileLeaving && shows("Your whole wallet") && rule.onAllNodes(save).fetchSemanticsNodes().isNotEmpty()) {
                assertEquals("the first save has finished by now", 1, entries().size)
                rule.onAllNodes(save)[0].performClick()
                tappedWhileLeaving = true
            }
        }
        rule.mainClock.autoAdvance = true
        settle()
        assertTrue("the second tap did land on the Save of the screen that was leaving", tappedWhileLeaving)
        assertEquals(1, entries().size)
        assertEquals(-25_000L, cashBalance())
    }

    @Test
    fun backTappedTwiceClosesOneScreenAndNeverTheFirst() {
        start()
        // From an editor opened from Home.
        tapButton("Add transaction")
        waitFor("Food")
        rule.onNodeWithContentDescription("Back").performTouchInput {
            click()
            click()
        }
        settle()
        assertTrue("Home is still there", shows("Your whole wallet"))

        // From Settings.
        tapDescribed("Settings")
        waitFor("Money set-up")
        rule.onNodeWithContentDescription("Back").performTouchInput {
            click()
            click()
        }
        settle()
        assertTrue(shows("Your whole wallet"))

        // Two screens deep: the second tap must not close the one underneath.
        tapDescribed("Settings")
        waitFor("Money set-up")
        tapButton("Wallets")
        waitFor("Where your money sits")
        rule.onNodeWithContentDescription("Back").performTouchInput {
            click()
            click()
        }
        settle()
        assertTrue("back on Settings, not thrown out to Home", shows("Money set-up"))
    }

    @Test
    fun aButtonTappedTwiceOpensItsScreenOnce() {
        start()
        rule.onNode(hasText("Add transaction") and hasClickAction()).performTouchInput {
            click()
            click()
        }
        waitFor("Food")
        back()
        settle()
        assertTrue("one Back is enough to be on Home again", shows("Your whole wallet"))
        assertFalse(shows("Food") && rule.onAllNodes(save).fetchSemanticsNodes().isNotEmpty())
    }

    /**
     * A new category: both taps pass the "you already have this" check before either has stored anything,
     * and the database refuses the second insert (one name per kind), which used to stop the app.
     */
    @Test
    fun aNewCategorySavedWithTwoTapsIsStoredOnceAndNothingBreaks() {
        start()
        tapDescribed("Settings")
        waitFor("Money set-up")
        tapButton("Categories")
        waitFor("For Gastos and Kita")
        tapButton("Add Gastos category")
        rule.waitUntil(10_000) { rule.onAllNodes(save).fetchSemanticsNodes().isNotEmpty() }
        field(0).performTextInput("Kape")
        rule.onNode(save).performTouchInput {
            click()
            click()
        }
        settle()
        val stored = runBlocking { container.database.categoryDao().observeAll(CategoryKind.EXPENSE).first() }.count { it.name == "Kape" }
        assertEquals(1, stored)
        assertTrue("back on the list of categories", shows("For Gastos and Kita"))
    }

    /**
     * Every editor saves the same way, so the guard has to be in every one. This reads the source: any
     * screen with a `save()` that starts a write of its own, not through the guard, fails here; a new
     * editor is read like the rest without being listed.
     *
     * Not here on purpose: the welcome's Finish. What it writes is repeatable (it looks at the wallets
     * that are there and adds only what is missing, in one step), so a second tap changes nothing.
     */
    @Test
    fun everyEditorSavesThroughTheGuard() {
        val ui = File("src/main/kotlin/com/pitakaview/app/ui")
        val editors = ui.walkTopDown().filter { it.isFile && it.extension == "kt" && "fun save()" in it.readText() }.toList()
        assertTrue("the editors were found (${editors.size})", editors.size >= 8)
        for (file in editors) {
            val body = file.readText().substringAfter("fun save()").substringBefore("\n    }\n")
            assertTrue("${file.name}: save() writes through the guard", "saving.launch(viewModelScope)" in body)
            assertFalse("${file.name}: save() starts no write of its own", "viewModelScope.launch" in body)
        }
        assertTrue("Import writes through the guard", File(ui, "importing/ImportViewModel.kt").readText().contains("importing.launch(viewModelScope)"))
    }
}
