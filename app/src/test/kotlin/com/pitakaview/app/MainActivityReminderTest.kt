package com.pitakaview.app

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.pitakaview.app.data.db.entity.LoanEntity
import com.pitakaview.app.domain.LoanDirection
import com.pitakaview.app.domain.ReminderKind
import com.pitakaview.app.reminders.ReminderIntents
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate

/**
 * The real MainActivity started the way a tapped reminder starts it: from the notification's own intent.
 * Covers the three ways that intent arrives: the app was closed, the app is already open, and the screen
 * is rotated afterwards (when the same intent is still attached and must not be followed a second time).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h850dp-xhdpi")
class MainActivityReminderTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val app: PitakaApp get() = ApplicationProvider.getApplicationContext()

    private fun shows(text: String) = rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(text: String) = rule.waitUntil(15_000) { shows(text) }

    @Test
    fun aReminderOpensItsScreenFromAClosedAppAndFromAnOpenOneAndIsNotFollowedTwice() {
        val loanId = runBlocking {
            withTimeout(10_000) { app.container.database.walletDao().observeAllWithBalance().first { it.isNotEmpty() } }
            app.container.database.loanDao().insert(
                LoanEntity(
                    direction = LoanDirection.I_OWE, counterparty = "Tita Baby", principalCentavos = 150_000, totalPayableCentavos = 150_000,
                    startDate = LocalDate.now().minusDays(10), dueDate = LocalDate.now(), createdAt = Instant.now(),
                ),
            )
        }

        ActivityScenario.launch<MainActivity>(ReminderIntents.open(app, ReminderKind.LOAN, loanId)).use { scenario ->
            // The app was closed: it opens on the loan.
            waitFor("Left to pay")
            waitFor("Tita Baby")

            // The app is open and a payday reminder is tapped: the same activity gets the new intent.
            // (Handed over the way Android hands a new intent to a running singleTop activity.)
            scenario.onActivity {
                InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(it, ReminderIntents.open(app, ReminderKind.PAYDAY, 1))
            }
            waitFor("Add a payday")
            rule.onNode(hasText("Kita") and isSelectable()).assertIsSelected()

            // The owner moves on, then the screen rotates. The payday intent is still attached to the
            // activity; following it again would drag them back to Kita.
            rule.onNode(hasText("Gastos") and isSelectable()).performClick()
            waitFor("Add expense")
            scenario.recreate()
            waitFor("Add expense")
            rule.onNode(hasText("Gastos") and isSelectable()).assertIsSelected()
            assertFalse(shows("Add a payday"))
        }
    }
}
