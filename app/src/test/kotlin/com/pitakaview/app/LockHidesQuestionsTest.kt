package com.pitakaview.app

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.pitakaview.app.lock.AppLockRules
import com.pitakaview.app.lock.FakeUnlocker
import com.pitakaview.app.lock.UnlockResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/**
 * Nothing of the app can be read or answered while it is locked, a question left open included.
 *
 * Found in the review before 1.0 (2026-11-27) and proven then: with the lock on and "Delete this entry?"
 * open, the app locked after a minute away, but the question was still on screen above the lock and its
 * "Yes, delete" deleted the entry. A dialog is a window of its own, above the lock screen. The app has two
 * kinds: its questions, and the calendar for picking a date.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h1500dp-xhdpi")
class LockHidesQuestionsTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val app: FitakaApp get() = ApplicationProvider.getApplicationContext()
    private val container get() = app.container

    private fun shows(text: String) = rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(text: String) = rule.waitUntil(15_000) { shows(text) }
    private fun tap(text: String) = rule.onNode(hasText(text) and hasClickAction()).performClick()
    private fun buttons(text: String) = rule.onAllNodes(hasText(text) and hasClickAction()).fetchSemanticsNodes().size
    private fun entries() = runBlocking { container.database.transactionDao().observeRecent(10).first().size }

    /** The app with its lock on, open on Home. */
    private fun withTheLockOn(body: (ActivityScenario<MainActivity>, FakeUnlocker) -> Unit) {
        runBlocking { withTimeout(10_000) { container.database.walletDao().observeAllWithBalance().first { it.isNotEmpty() } } }
        val phone = FakeUnlocker(answer = UnlockResult.UNLOCKED)
        container.unlocker = phone
        runBlocking {
            container.settings.setOnboardingDone()
            container.settings.setLockOn(true)
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitFor("Your whole wallet")
            body(scenario, phone)
        }
    }

    /** Away for more than a minute, and back without unlocking. */
    private fun awayAndBackStillLocked(scenario: ActivityScenario<MainActivity>, phone: FakeUnlocker) {
        phone.answer = UnlockResult.NOT_UNLOCKED
        scenario.moveToState(Lifecycle.State.CREATED)
        ShadowSystemClock.advanceBy(Duration.ofMillis(AppLockRules.AWAY_MS + 1_000))
        scenario.moveToState(Lifecycle.State.RESUMED)
        waitFor("Fitaka is locked")
        rule.waitForIdle()
    }

    @Test
    fun aQuestionLeftOpenIsGoneWhileLockedAndBackAfterUnlocking() = withTheLockOn { scenario, phone ->
        tap("Add transaction")
        waitFor("Food")
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("250")
        rule.onNode(hasText("Food") and isSelectable()).performClick()
        tap("Save")
        waitFor("−₱250.00")
        tap("Food")
        waitFor("Edit entry")
        tap("Delete entry")
        waitFor("Delete this entry?")

        awayAndBackStillLocked(scenario, phone)
        assertFalse("the question is not on screen while locked", shows("Delete this entry?"))
        assertFalse(shows("It is removed from your totals"))
        assertEquals("nothing to answer it with", 0, buttons("Yes, delete"))
        assertEquals("the entry is still there", 1, entries())

        // Unlocked: the question the owner had open is there again, and still works.
        phone.answer = UnlockResult.UNLOCKED
        tap("Unlock")
        waitFor("Delete this entry?")
        assertFalse(shows("Fitaka is locked"))
        tap("Yes, delete")
        rule.waitUntil(10_000) { entries() == 0 }
        assertTrue(entries() == 0)
    }

    @Test
    fun theCalendarLeftOpenIsGoneWhileLockedAndBackAfterUnlocking() = withTheLockOn { scenario, phone ->
        tap("Add transaction")
        waitFor("Food")
        tap("Other date")
        rule.waitUntil(10_000) { buttons("OK") > 0 }
        assertEquals("the calendar is open, with its two answers", 1, buttons("Cancel"))

        awayAndBackStillLocked(scenario, phone)
        assertEquals("no calendar over the lock", 0, buttons("OK"))
        assertEquals(0, buttons("Cancel"))

        phone.answer = UnlockResult.UNLOCKED
        tap("Unlock")
        rule.waitUntil(10_000) { buttons("OK") > 0 }
        assertFalse(shows("Fitaka is locked"))
    }
}
