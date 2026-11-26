package com.pitakaview.app.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import com.pitakaview.app.data.db.entity.BaonEntity
import com.pitakaview.app.data.db.entity.LoanEntity
import com.pitakaview.app.data.db.entity.SavingsGoalEntity
import com.pitakaview.app.domain.BaonDirection
import com.pitakaview.app.domain.BaonPeriod
import com.pitakaview.app.domain.LoanDirection
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Walks the real screens on a phone-sized screen and checks two things a person using TalkBack or with
 * unsteady hands depends on, for everything that can be tapped:
 *  - it can be hit: its touch area is at least 48 x 48 dp (Android's own minimum);
 *  - it says what it is: it carries words or a spoken description.
 * It also checks every text box says what it is for, and every screen has a heading to jump to.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h850dp-xhdpi")
class AccessibilityAuditTest : FlowTestBase() {
    private val problems = mutableListOf<String>()

    private fun words(node: SemanticsNode): String {
        val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
        val described = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()
        return listOf(text, described).filter { it.isNotBlank() }.joinToString(" / ")
    }

    /** Everything on screen now that a finger or TalkBack can act on. */
    private fun audit(screen: String) {
        rule.waitForIdle()
        val root = rule.onAllNodes(androidx.compose.ui.test.isRoot())[0].fetchSemanticsNode()
        // A title a screen reader can jump to.
        // The title of the screen, in the bar at the top, is the first of them.
        val headings = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).fetchSemanticsNodes()
        val highest = headings.minOfOrNull { it.boundsInRoot.top / it.layoutInfo.density.density }
        if (highest == null || highest > 120f) problems += "$screen: the title at the top is not marked as a heading"
        val actionable = rule.onAllNodes(hasClickAction() or isToggleable() or isSelectable()).fetchSemanticsNodes()
        for (node in actionable) {
            val density = node.layoutInfo.density.density
            val touch = node.touchBoundsInRoot
            // Rows half under a bar or past the end of the screen are judged when they are scrolled into view.
            val whole = node.boundsInRoot.width >= node.size.width - 1 && node.boundsInRoot.height >= node.size.height - 1
            if (!whole || !root.boundsInRoot.overlaps(node.boundsInRoot)) continue
            val isTextBox = node.config.contains(SemanticsActions.SetText)
            val name = words(node)
            if (isTextBox) {
                if (node.config.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty()) {
                    problems += "$screen: a text box does not say what it is for (shows \"${name.take(30)}\")"
                }
                continue
            }
            val width = touch.width / density
            val height = touch.height / density
            if (width < 47.5f || height < 47.5f) problems += "$screen: \"${name.take(40)}\" can only be hit in ${width.toInt()} x ${height.toInt()} dp"
            if (name.isBlank()) problems += "$screen: something tappable at ${node.boundsInRoot} says nothing about itself"
        }
    }

    private fun scrollTo(text: String) = rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text, substring = true))

    private fun scrollToEnd() = rule.onNode(hasScrollToNodeAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }

    /** Audits the top of a screen and its end. */
    private fun auditAll(screen: String) {
        audit(screen)
        scrollToEnd()
        audit("$screen (end)")
    }

    private fun seed() = runBlocking {
        addExpense(25_000, note = "Lunch")
        addPaydayDueToday()
        val db = container.database
        db.loanDao().insert(
            LoanEntity(
                direction = LoanDirection.I_OWE, counterparty = "Tita Baby", principalCentavos = 150_000, totalPayableCentavos = 150_000,
                startDate = today(), dueDate = today().plusDays(3), createdAt = Instant.now(),
            ),
        )
        db.savingsGoalDao().insert(SavingsGoalEntity(name = "Laptop", targetCentavos = 5_000_000, createdAt = Instant.now()))
        db.baonDao().insert(
            BaonEntity(name = "School baon", direction = BaonDirection.RECEIVE, amountCentavos = 50_000, period = BaonPeriod.WEEKLY, startDate = today(), createdAt = Instant.now()),
        )
    }

    @Test
    fun theTabsAndTheirLists() {
        start()
        seed()
        auditAll("Home")
        for (tab in listOf("Kita", "Baon", "Ipon", "Gastos", "Utang")) {
            tapTab(tab)
            rule.waitForIdle()
            auditAll(tab)
        }
        assertEquals("", problems.joinToString("\n"))
    }

    @Test
    fun settingsAndTheScreensBehindIt() {
        start()
        seed()
        rule.onNodeWithContentDescription("Settings").performClick()
        waitFor("Money set-up")
        auditAll("Settings")
        // About shows the version that is really installed, not a number typed into the screen.
        val installed = rule.activity.packageManager.getPackageInfo(rule.activity.packageName, 0).versionName
        assertTrue("About should show version $installed", shows("PitakaView $installed"))
        for ((row, landmark) in listOf("Wallets" to "Add wallet", "Categories" to "category", "Backup" to "Keep a copy of your data", "Import" to "Which wallet is the file for")) {
            scrollTo(row)
            tapButton(row)
            waitFor(landmark)
            auditAll(row)
            back()
            waitFor("Money set-up")
        }
        back()
        rule.onNodeWithContentDescription("Search").performClick()
        rule.waitForIdle()
        audit("Search")
        back()
        tapButton("Summary")
        waitFor("Left over in")
        auditAll("Summary")
        assertEquals("", problems.joinToString("\n"))
    }

    @Test
    fun theFormsForAddingThings() {
        start()
        seed()
        tapButton("Add transaction")
        rule.onAllNodes(hasSetTextAction())[0].fetchSemanticsNode()
        auditAll("Quick entry")
        back()
        for ((tab, add) in listOf("Kita" to "Add a payday", "Utang" to "Add a loan", "Ipon" to "Add a goal", "Baon" to "Add a baon")) {
            tapTab(tab)
            rule.waitForIdle()
            scrollTo(add)
            tapButton(add)
            rule.waitForIdle()
            auditAll(add)
            back()
        }
        assertEquals("", problems.joinToString("\n"))
    }
}
