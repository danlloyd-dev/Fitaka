package com.pitakaview.app.ui

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.pitakaview.app.lock.FakeUnlocker
import com.pitakaview.app.lock.LockSupport
import com.pitakaview.app.lock.UnlockResult
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

/**
 * The App lock switch in Settings, on a phone-sized screen. The phone's own unlock question is a
 * stand-in that answers as the test says; the switch, the saved choice and the rules are the real code.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h850dp-xhdpi")
class AppLockFlowTest : FlowTestBase() {
    private val phone = FakeUnlocker()

    private fun scrollTo(text: String) = rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text, substring = true))

    private fun lockSwitch(): SemanticsNodeInteraction = rule.onNode(hasText("Lock the app") and isToggleable())

    private fun lockOn() = runBlocking { container.settings.lockOn.first() }

    private fun openSettings() {
        container.unlocker = phone
        rule.onNodeWithContentDescription("Settings").performClick()
        waitFor("Money set-up")
    }

    /** Gives a wrong change the time it would need to show itself. */
    private fun settle() = repeat(12) {
        rule.waitForIdle()
        Thread.sleep(25)
    }

    @Test
    fun switchingTheLockOnOrOffNeedsThePhoneToSayYes() {
        start()
        // Reminders are on throughout, and must still be on at the end: the two switches sit one above the
        // other in Settings, and on the owner's phone (2026-11-26) reminders were found off after the lock
        // check. This pins down that the lock switch itself never touches them.
        runBlocking { container.settings.setRemindersOn(true) }
        openSettings()
        scrollTo("Lock the app")
        lockSwitch().assertIsDisplayed().assertIsEnabled().assertIsOff()

        // The phone says no (the owner cancelled): nothing changes.
        phone.answer = UnlockResult.NOT_UNLOCKED
        lockSwitch().performClick()
        settle()
        assertEquals(listOf("Turn on the app lock"), phone.asked)
        assertFalse(lockOn())
        lockSwitch().assertIsOff()

        // The phone says yes.
        phone.answer = UnlockResult.UNLOCKED
        lockSwitch().performClick()
        rule.waitUntil(10_000) { lockOn() }
        lockSwitch().assertIsOn()
        assertEquals("switching it on does not lock the app at once", null, container.appLock.locked.value.takeIf { it == true })

        // Switching it off is checked the same way, so nobody else can remove the lock.
        phone.answer = UnlockResult.NOT_UNLOCKED
        lockSwitch().performClick()
        settle()
        assertEquals("Turn off the app lock", phone.asked.last())
        assertTrue(lockOn())
        lockSwitch().assertIsOn()

        phone.answer = UnlockResult.UNLOCKED
        lockSwitch().performClick()
        rule.waitUntil(10_000) { !lockOn() }
        lockSwitch().assertIsOff()

        settle()
        assertTrue("switching the lock on and off left reminders as they were", runBlocking { container.settings.remindersOn.first() })
        rule.onNode(hasText("Remind me") and isToggleable()).assertIsOn()
    }

    @Test
    fun aPhoneWithNoScreenLockIsToldWhatToDoAndTheSwitchDoesNothing() {
        phone.support = LockSupport.NO_SCREEN_LOCK
        phone.answer = UnlockResult.UNLOCKED
        start()
        openSettings()
        scrollTo("Lock the app")

        assertTrue(shows("Set a screen lock on this phone first"))
        lockSwitch().assertIsNotEnabled().assertIsOff()
        lockSwitch().performClick()
        settle()
        assertEquals(emptyList<String>(), phone.asked)
        assertFalse(lockOn())
    }

    @Test
    fun aLockLeftOnCanStillBeSwitchedOffWhenThePhoneLostItsScreenLock() {
        phone.support = LockSupport.NO_SCREEN_LOCK
        start()
        runBlocking { container.settings.setLockOn(true) }
        openSettings()
        scrollTo("Lock the app")

        lockSwitch().assertIsEnabled().assertIsOn()
        lockSwitch().performClick()
        rule.waitUntil(10_000) { !lockOn() }
        assertEquals("the phone has nothing to ask for, so it is not asked", emptyList<String>(), phone.asked)
    }

    @Test
    fun onAPhoneThatCannotLockTheSwitchIsNotOfferedAtAll() {
        phone.support = LockSupport.TOO_OLD
        start()
        openSettings()
        scrollTo("About")
        assertFalse(shows("Lock the app"))
        assertFalse(shows("App lock"))
        // The rest of Settings is as usual.
        scrollTo("Remind me")
        assertTrue(shows("Remind me"))
    }
}
