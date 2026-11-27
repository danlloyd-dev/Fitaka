package com.pitakaview.app

import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.pitakaview.app.data.db.entity.TransactionEntity
import com.pitakaview.app.domain.CategoryKind
import com.pitakaview.app.domain.TxPurpose
import com.pitakaview.app.domain.WalletProvider
import com.pitakaview.app.lock.AppLockRules
import com.pitakaview.app.lock.LockSupport
import com.pitakaview.app.lock.UnlockResult
import com.pitakaview.app.lock.FakeUnlocker
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
import java.time.Instant
import java.time.LocalDate

/**
 * What the real MainActivity shows when the app opens, on a phone-sized screen: the first-run welcome,
 * and the lock screen. The phone's own unlock question is a stand-in that answers as the test says.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h850dp-xhdpi")
class MainActivityStartTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val app: FitakaApp get() = ApplicationProvider.getApplicationContext()
    private val container get() = app.container

    private fun shows(text: String) = rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(text: String) = rule.waitUntil(15_000) { shows(text) }

    private fun tap(text: String) = rule.onNode(hasText(text) and hasClickAction()).assertIsDisplayed().performClick()

    private fun wallets() = runBlocking { container.database.walletDao().all() }

    private fun seeded() = runBlocking {
        withTimeout(10_000) { container.database.walletDao().observeAllWithBalance().first { it.isNotEmpty() } }
    }

    private fun welcomeDone() = runBlocking { container.settings.onboardingDone.first() }

    // ---------- the first-run welcome ----------

    @Test
    fun aFreshInstallIsWelcomedAndTheMoneyTypedInBecomesTheWallets() {
        seeded()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitFor("It keeps track of your money in five places.")
            assertTrue(shows("Step 1 of 3"))
            assertTrue("the five sections are introduced", shows("Everything you spend."))
            tap("Start")

            waitFor("How much money do you have right now?")
            rule.onAllNodes(hasSetTextAction())[0].performTextInput("1500")
            rule.onNode(hasText("GCash") and isSelectable()).performClick()
            waitFor("In GCash now")
            rule.onAllNodes(hasSetTextAction())[1].performTextInput("250.50")

            // Turning the phone here keeps the step and what was typed.
            scenario.recreate()
            waitFor("In GCash now")
            assertTrue(shows("1500"))
            assertTrue(shows("250.50"))
            assertEquals("nothing is stored before the end", 1, wallets().size)

            tap("Next")
            waitFor("You are ready.")
            tap("Open my wallet")

            // Home, with both wallets added up.
            waitFor("Your whole wallet")
            waitFor("₱1,750.50")
            assertTrue(welcomeDone())
            val all = wallets()
            assertEquals(listOf("Cash", "GCash"), all.map { it.name })
            assertEquals(listOf(150_000L, 25_050L), all.map { it.openingBalanceCentavos })
            assertEquals(WalletProvider.GCASH, all[1].provider)

            // It is never shown again.
            scenario.recreate()
            waitFor("Your whole wallet")
            assertFalse(shows("Step 1 of 3"))
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("Your whole wallet")
            assertFalse(shows("Step 1 of 3"))
        }
    }

    @Test
    fun skipLeavesAtOnceAndStoresNothingThatWasTyped() {
        seeded()
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("Step 1 of 3")
            tap("Start")
            waitFor("How much money do you have right now?")
            rule.onAllNodes(hasSetTextAction())[0].performTextInput("999")
            // Back goes a step back, not out.
            rule.onNode(hasText("Skip") and hasClickAction()).assertIsDisplayed()
            tap("Skip")

            waitFor("Your whole wallet")
            assertTrue(welcomeDone())
            assertEquals(listOf(0L), wallets().map { it.openingBalanceCentavos })
        }
    }

    @Test
    fun aHalfTypedAmountIsPointedOutBeforeGoingOn() {
        seeded()
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("Step 1 of 3")
            tap("Start")
            waitFor("How much money do you have right now?")
            // The money box lets "12." through while it is being typed; it is not an amount yet.
            rule.onAllNodes(hasSetTextAction())[0].performTextInput("12.")
            tap("Next")
            waitFor("Enter an amount, or leave it empty")
            assertTrue("still on the money step", shows("Step 2 of 3"))

            rule.onAllNodes(hasSetTextAction())[0].performTextInput("5")
            tap("Next")
            waitFor("You are ready.")
            tap("Open my wallet")
            waitFor("Your whole wallet")
            assertEquals(listOf(1_250L), wallets().map { it.openingBalanceCentavos })
        }
    }

    @Test
    fun someoneWhoAlreadyHasEntriesGoesStraightToHome() {
        seeded()
        runBlocking {
            val cash = container.database.walletDao().all().single().id
            val food = container.database.categoryDao().observeActive(CategoryKind.EXPENSE).first().first().id
            container.ledger.add(
                TransactionEntity(purpose = TxPurpose.EXPENSE, amountCentavos = 5_000, occurredOn = LocalDate.now(), fromWalletId = cash, categoryId = food, createdAt = Instant.now()),
            )
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("Your whole wallet")
            assertFalse(shows("Step 1 of 3"))
            assertTrue(welcomeDone())
        }
    }

    // ---------- the app lock ----------

    private fun lockedAppWith(phone: FakeUnlocker) {
        seeded()
        container.unlocker = phone
        runBlocking {
            container.settings.setOnboardingDone()
            container.settings.setLockOn(true)
        }
    }

    @Test
    fun aLockedAppShowsNothingUntilThePhoneSaysYes() {
        val phone = FakeUnlocker(answer = UnlockResult.NOT_UNLOCKED)
        lockedAppWith(phone)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitFor("Fitaka is locked")
            assertEquals("the phone is asked once by itself", listOf("Unlock Fitaka"), phone.asked)
            assertFalse("nothing of the app can be read", shows("Your whole wallet"))
            assertFalse(shows("Cash"))
            scenario.onActivity {
                assertTrue("kept out of screenshots and the recent-apps list", it.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            }

            // The owner cancelled; the button asks again. Still no.
            tap("Unlock")
            assertEquals(2, phone.asked.size)
            assertTrue(shows("Fitaka is locked"))
            assertFalse(shows("Your whole wallet"))

            phone.answer = UnlockResult.UNLOCKED
            tap("Unlock")
            waitFor("Your whole wallet")
            assertFalse(shows("Fitaka is locked"))
        }
    }

    @Test
    fun itLocksAgainAfterAMinuteAwayButNotAfterAShortTripOrARotation() {
        val phone = FakeUnlocker(answer = UnlockResult.UNLOCKED)
        lockedAppWith(phone)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitFor("Your whole wallet")
            assertEquals(1, phone.asked.size)

            // A rotation.
            scenario.recreate()
            waitFor("Your whole wallet")
            assertEquals(1, phone.asked.size)

            // Out of sight for half a minute.
            scenario.moveToState(Lifecycle.State.CREATED)
            ShadowSystemClock.advanceBy(Duration.ofMillis(AppLockRules.AWAY_MS / 2))
            scenario.moveToState(Lifecycle.State.RESUMED)
            waitFor("Your whole wallet")
            assertEquals(1, phone.asked.size)

            // Out of sight for more than a minute: locked, and the phone is asked again.
            phone.answer = UnlockResult.NOT_UNLOCKED
            scenario.moveToState(Lifecycle.State.CREATED)
            ShadowSystemClock.advanceBy(Duration.ofMillis(AppLockRules.AWAY_MS + 1_000))
            scenario.moveToState(Lifecycle.State.RESUMED)
            waitFor("Fitaka is locked")
            assertEquals(2, phone.asked.size)
            assertFalse(shows("Your whole wallet"))
        }
    }

    @Test
    fun nothingUnderTheLockScreenCanBeTappedOrMovedWithBack() {
        val phone = FakeUnlocker(answer = UnlockResult.UNLOCKED)
        lockedAppWith(phone)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitFor("Your whole wallet")
            // Leave the app on Settings, a screen that Back would close and whose rows open other screens.
            rule.onNodeWithContentDescription("Settings").performClick()
            waitFor("Money set-up")
            val walletsRow = rule.onNode(hasText("Wallets") and hasClickAction()).fetchSemanticsNode().boundsInRoot.center

            phone.answer = UnlockResult.NOT_UNLOCKED
            scenario.moveToState(Lifecycle.State.CREATED)
            ShadowSystemClock.advanceBy(Duration.ofMillis(AppLockRules.AWAY_MS + 1_000))
            scenario.moveToState(Lifecycle.State.RESUMED)
            waitFor("Fitaka is locked")

            // A tap exactly where the Wallets row is underneath, and the Back button.
            rule.onRoot().performTouchInput { click(walletsRow) }
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            rule.waitForIdle()

            phone.answer = UnlockResult.UNLOCKED
            tap("Unlock")
            // Settings is exactly as it was left: not closed by Back, and Wallets was not opened.
            waitFor("Money set-up")
            assertFalse(shows("Your whole wallet"))
        }
    }

    @Test
    fun aPhoneWhoseScreenLockWasRemovedDoesNotShutTheOwnerOut() {
        val phone = FakeUnlocker(answer = UnlockResult.NOT_UNLOCKED, support = LockSupport.NO_SCREEN_LOCK)
        lockedAppWith(phone)
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("Your whole wallet")
            assertEquals("there is nothing to ask the phone for", emptyList<String>(), phone.asked)
        }
    }

    @Test
    fun withTheLockOffTheAppOpensStraightAwayAndCanBeScreenshotted() {
        val phone = FakeUnlocker()
        seeded()
        container.unlocker = phone
        runBlocking { container.settings.setOnboardingDone() }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitFor("Your whole wallet")
            assertEquals(emptyList<String>(), phone.asked)
            scenario.onActivity { assertEquals(0, it.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) }
        }
    }
}
