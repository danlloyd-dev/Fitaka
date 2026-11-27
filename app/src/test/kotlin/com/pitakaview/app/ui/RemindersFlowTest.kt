package com.pitakaview.app.ui

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.core.app.ActivityOptionsCompat
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.pitakaview.app.data.db.entity.LoanEntity
import com.pitakaview.app.domain.LoanDirection
import com.pitakaview.app.domain.Reminder
import com.pitakaview.app.domain.ReminderKind
import com.pitakaview.app.reminders.ReminderNotifier
import com.pitakaview.app.reminders.ReminderOpen
import com.pitakaview.app.reminders.WorkManagerReminderAlarm
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Reminders through the real screens, on a phone-sized screen. Android's "allow notifications?" question
 * belongs to the system, so a stand-in answers it with [phoneAllows]; the switch, the saved choice, the
 * notification and the daily WorkManager work are all the real code. The clock is set to 9 in the morning.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h850dp-xhdpi")
class RemindersFlowTest : FlowTestBase() {
    private var phoneAllows = true
    private var timesAsked = 0
    @Volatile private var clock: LocalDateTime = LocalDateTime.MIN
    private val shown = java.util.concurrent.CopyOnWriteArrayList<String>()

    private val phone get() = shadowOf(rule.activity.getSystemService(NotificationManager::class.java))

    private val permissionQuestion = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            timesAsked += 1
            if (phoneAllows) shadowOf(rule.activity.application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
            phone.setNotificationsEnabled(phoneAllows)
            dispatchResult(requestCode, phoneAllows)
        }
    }

    @Composable
    override fun Surroundings(content: @Composable () -> Unit) {
        val owner = remember {
            object : ActivityResultRegistryOwner {
                override val activityResultRegistry: ActivityResultRegistry = permissionQuestion
            }
        }
        CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner, content = content)
    }

    @Before
    fun setUp() {
        val config = Configuration.Builder().setMinimumLoggingLevel(Log.DEBUG).setExecutor(SynchronousExecutor()).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(rule.activity.application, config)
        clock = today().atTime(9, 0)
        container.reminderClock = { clock }
        // Counts every reminder handed to the phone: a second post to the same slot would otherwise go unseen.
        val real = container.reminderNotifier
        container.reminderNotifier = object : ReminderNotifier {
            override fun canNotify() = real.canNotify()
            override fun show(reminder: Reminder) {
                shown += reminder.title
                real.show(reminder)
            }
        }
        // As on a real phone since Android 13: no notifications until the owner says yes.
        phone.setNotificationsEnabled(false)
    }

    private fun scrollTo(text: String) = rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text, substring = true))

    private fun switch(title: String): SemanticsNodeInteraction = rule.onNode(hasText(title) and isToggleable())

    private fun remindersOn() = runBlocking { container.settings.remindersOn.first() }

    private fun waitingChecks() = WorkManager.getInstance(rule.activity.application)
        .getWorkInfosForUniqueWork(WorkManagerReminderAlarm.NAME).get().filter { it.state == WorkInfo.State.ENQUEUED }

    private fun notificationTitles() = phone.allNotifications.map { it.extras.getString(Notification.EXTRA_TITLE) }

    private fun addLoan(due: LocalDate?): Long = runBlocking {
        container.database.loanDao().insert(
            LoanEntity(
                direction = LoanDirection.I_OWE, counterparty = "Tita Baby", principalCentavos = 150_000, totalPayableCentavos = 150_000,
                startDate = today().minusDays(10), dueDate = due, createdAt = Instant.now(),
            ),
        )
    }

    private fun openSettings() {
        rule.onNodeWithContentDescription("Settings").performClick()
        waitFor("Money set-up")
        scrollTo("Remind me")
    }

    @Test
    fun switchingRemindersOnAsksThePhoneOnceShowsWhatIsDueAndLinesUpTheDailyCheck() {
        start()
        addLoan(due = today())
        openSettings()
        switch("Remind me").assertIsDisplayed().assertIsOff()
        assertFalse("nothing about phone settings until there is a reason", shows("Open phone settings"))

        switch("Remind me").performClick()
        rule.waitUntil(10_000) { remindersOn() }
        assertEquals("the phone's question was shown", 1, timesAsked)
        switch("Remind me").assertIsOn()
        waitFor("Last checked today at 9:00 AM, with the app open.")
        assertEquals(listOf("Payment due today"), notificationTitles())
        assertEquals("one daily check is waiting", 1, waitingChecks().size)
        assertFalse(shows("not letting Fitaka"))

        // Off again: the waiting check is taken away.
        switch("Remind me").performClick()
        rule.waitUntil(10_000) { !remindersOn() }
        switch("Remind me").assertIsOff()
        rule.waitUntil(10_000) { waitingChecks().isEmpty() }

        // On again half an hour later: the phone already said yes, so it is not asked a second time.
        clock = today().atTime(9, 30)
        switch("Remind me").performClick()
        rule.waitUntil(10_000) { remindersOn() }
        assertEquals(1, timesAsked)
        rule.waitUntil(10_000) { waitingChecks().size == 1 }
        waitFor("Last checked today at 9:30 AM, with the app open.")
        assertEquals("and the reminder already shown is not shown again", listOf("Payment due today"), shown.toList())
    }

    @Test
    fun whenThePhoneSaysNoRemindersStayOffAndTheScreenSaysWhatToDo() {
        phoneAllows = false
        start()
        addLoan(due = today())
        openSettings()

        switch("Remind me").performClick()
        waitFor("This phone is not letting Fitaka show notifications")
        assertEquals(1, timesAsked)
        // Nothing should happen next; a wrong "switch on anyway" gets the time it would need to show itself.
        repeat(20) {
            rule.waitForIdle()
            Thread.sleep(25)
        }
        switch("Remind me").assertIsOff()
        assertFalse(remindersOn())
        assertEquals(emptyList<String?>(), notificationTitles())
        assertTrue(waitingChecks().isEmpty())

        // The way out is on screen without hunting for it, and opens this app's page in the phone's settings.
        scrollTo("Open phone settings")
        rule.onNode(hasText("Open phone settings") and hasClickAction()).assertIsDisplayed().performClick()
        val opened = shadowOf(rule.activity.application).nextStartedActivity
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, opened.action)
        assertEquals("package:com.pitakaview.app", opened.dataString)
    }

    @Test
    fun aPaydayHasItsOwnRemindSwitchAndSaysWhenRemindersAreOffAltogether() {
        start()
        addPaydayDueToday()
        tapTab("Kita")
        waitFor("Sahod")
        rule.onNode(hasText("Sahod") and hasClickAction()).performClick()
        waitFor("Edit payday")

        scrollTo("Remind me on payday")
        switch("Remind me on payday").assertIsDisplayed().assertIsOn()
        assertTrue("reminders as a whole are off, and the screen says so", shows("Reminders are off. Turn them on in Settings."))

        switch("Remind me on payday").performClick()
        switch("Remind me on payday").assertIsOff()
        tapButton("Save")
        rule.waitUntil(10_000) { !paydays().single().remind }

        // It reads back as saved.
        waitFor("Add a payday")
        rule.onNode(hasText("Sahod") and hasClickAction()).performClick()
        waitFor("Edit payday")
        scrollTo("Remind me on payday")
        switch("Remind me on payday").assertIsOff()
    }

    @Test
    fun aLoanWithADateHasItsOwnRemindSwitchAndOneWithoutHasNothingToRemindOf() {
        start()
        addLoan(due = today().plusDays(5))
        tapTab("Utang")
        waitFor("Tita Baby")
        rule.onNode(hasText("Tita Baby") and hasClickAction()).performClick()
        waitFor("Left to pay")
        scrollTo("Edit loan")
        tapButton("Edit loan")
        waitFor("How it is paid back")

        scrollTo("Remind me when it is due")
        switch("Remind me when it is due").assertIsDisplayed().assertIsOn().performClick()
        switch("Remind me when it is due").assertIsOff()

        // With no due date there is nothing to be reminded of, so the switch goes away.
        scrollTo("No due date")
        rule.onNode(hasText("No due date") and isSelectable()).performClick()
        rule.waitUntil(10_000) { rule.onAllNodes(hasText("Remind me when it is due")).fetchSemanticsNodes().isEmpty() }
        rule.onNode(hasText("One due date") and isSelectable()).performClick()
        scrollTo("Remind me when it is due")
        switch("Remind me when it is due").assertIsOff()

        tapButton("Save")
        rule.waitUntil(10_000) { !loans().single().loan.remind }
        assertEquals("the due date was kept", today().plusDays(5), loans().single().loan.dueDate)
    }

    @Test
    fun aTappedReminderOpensItsLoanOrThePaydays() {
        start()
        val loanId = addLoan(due = today())

        // From Home.
        reminderToOpen = ReminderOpen(ReminderKind.LOAN, loanId)
        waitFor("Left to pay")
        rule.runOnIdle { assertEquals("the request is used up", null, reminderToOpen) }
        // Back goes to the list of loans, then Home: the same as if it had been opened by hand.
        back()
        rule.onNode(hasText("Utang") and isSelectable()).assertIsSelected()
        waitFor("Add a loan")

        // From a screen deep in the app.
        rule.onNode(hasText("Home") and isSelectable()).performClick()
        rule.onNodeWithContentDescription("Settings").performClick()
        waitFor("Money set-up")
        reminderToOpen = ReminderOpen(ReminderKind.PAYDAY, 1)
        rule.waitUntil(10_000) { rule.onAllNodes(hasText("Kita") and isSelectable()).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasText("Kita") and isSelectable()).assertIsSelected()
        waitFor("Add a payday")

        // A reminder for a loan that has since been deleted lands on the list, not on an empty page.
        reminderToOpen = ReminderOpen(ReminderKind.LOAN, 9_999)
        rule.waitUntil(10_000) {
            rule.waitForIdle()
            reminderToOpen == null
        }
        rule.onNode(hasText("Utang") and isSelectable()).assertIsSelected()
        waitFor("Add a loan")
        assertFalse(shows("Left to pay"))
    }
}
