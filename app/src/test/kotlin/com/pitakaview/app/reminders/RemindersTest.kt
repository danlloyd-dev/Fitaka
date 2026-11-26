package com.pitakaview.app.reminders

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.pitakaview.app.PitakaApp
import com.pitakaview.app.data.db.entity.LoanEntity
import com.pitakaview.app.data.db.entity.PayScheduleEntity
import com.pitakaview.app.data.db.entity.TransactionEntity
import com.pitakaview.app.domain.CategoryKind
import com.pitakaview.app.domain.LoanDirection
import com.pitakaview.app.domain.PayFrequency
import com.pitakaview.app.domain.Reminder
import com.pitakaview.app.domain.ReminderKind
import com.pitakaview.app.domain.TxPurpose
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Reminders against the real database, the real settings file and Android's real notification
 * calls (Robolectric keeps what was posted). Only the clock and the daily alarm are stand-ins, so each
 * test can say what time it is and see what was asked of the alarm.
 */
@RunWith(RobolectricTestRunner::class)
class RemindersTest {
    private val app: PitakaApp get() = ApplicationProvider.getApplicationContext()
    private val container get() = app.container
    private val phone get() = shadowOf(app.getSystemService(NotificationManager::class.java))

    /** A Friday. */
    private val friday = LocalDate.of(2026, 11, 27)
    private var clock: LocalDateTime = friday.atTime(9, 0)

    private class RecordingAlarm : ReminderAlarm {
        val asked = mutableListOf<String>()
        override fun ensureScheduled(delay: Duration) { asked += "ensure $delay" }
        override fun scheduleAfterThisRun(delay: Duration) { asked += "after-this-run $delay" }
        override fun cancel() { asked += "cancel" }
    }

    /** Passes everything on to Android, and counts: a second post to the same slot would otherwise go unseen. */
    private class CountingNotifier(private val real: ReminderNotifier) : ReminderNotifier {
        val shown = mutableListOf<Reminder>()
        override fun canNotify() = real.canNotify()
        override fun show(reminder: Reminder) {
            shown += reminder
            real.show(reminder)
        }
    }

    private val alarm = RecordingAlarm()
    private lateinit var notifier: CountingNotifier

    @Before
    fun setUp() {
        notifier = CountingNotifier(container.reminderNotifier)
        container.reminderNotifier = notifier
        container.reminderAlarm = alarm
        container.reminderClock = { clock }
        // The app seeds a Cash wallet and starter categories on first launch; a payday needs both.
        runBlocking {
            withTimeout(10_000) {
                container.database.walletDao().observeAllWithBalance().first { it.isNotEmpty() }
                container.database.categoryDao().observeActive(CategoryKind.INCOME).first { it.isNotEmpty() }
            }
        }
    }

    private fun addLoan(due: LocalDate, direction: LoanDirection = LoanDirection.I_OWE, remind: Boolean = true): Long = runBlocking {
        container.database.loanDao().insert(
            LoanEntity(
                direction = direction, counterparty = "Tita Baby", principalCentavos = 150_000, totalPayableCentavos = 150_000,
                startDate = friday.minusDays(30), dueDate = due, remind = remind, createdAt = Instant.parse("2026-10-01T00:00:00Z"),
            ),
        )
    }

    private fun cashId() = runBlocking { container.database.walletDao().observeAllWithBalance().first().first().wallet.id }

    private fun salaryId() = runBlocking {
        container.database.categoryDao().observeActive(CategoryKind.INCOME).first().first { it.name == "Salary" }.id
    }

    /** Paid every Friday, set up long before. */
    private fun addFridayPayday(): Long = runBlocking {
        container.database.payScheduleDao().insert(
            PayScheduleEntity(
                name = "Sahod", expectedAmountCentavos = 1_500_000, walletId = cashId(), categoryId = salaryId(),
                frequency = PayFrequency.WEEKLY, dayOfWeek = friday.dayOfWeek, createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            ),
        )
    }

    private fun title(notification: Notification) = notification.extras.getString(Notification.EXTRA_TITLE)

    private fun text(notification: Notification) = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

    @Test
    fun nothingIsShownUntilRemindersAreSwitchedOn() = runBlocking {
        addLoan(due = friday)

        assertEquals(0, container.reminders.check(inBackground = false))
        container.reminders.onAppOpened()
        container.reminders.dailyRun()
        assertEquals(emptyList<Reminder>(), notifier.shown)
        assertEquals("the alarm is left alone while reminders are off", emptyList<String>(), alarm.asked)

        container.reminders.turnOn()
        assertEquals(1, phone.allNotifications.size)
        assertEquals("Payment due today", title(phone.allNotifications.single()))
        assertEquals("₱1,500.00 to Tita Baby", text(phone.allNotifications.single()))
        // 9:00 now, so the daily check is lined up for 8:00 tomorrow.
        assertEquals(listOf("ensure PT23H"), alarm.asked)
        assertTrue(container.settings.remindersOn.first())
    }

    @Test
    fun eachReminderIsShownOnceHoweverOftenTheCheckRuns() = runBlocking {
        addLoan(due = friday)
        container.reminders.turnOn()
        assertEquals(1, notifier.shown.size)

        assertEquals(0, container.reminders.check(inBackground = false))
        container.reminders.dailyRun()
        clock = friday.atTime(18, 0)
        container.reminders.dailyRun()
        assertEquals("shown once", 1, notifier.shown.size)
    }

    @Test
    fun theNextMorningBringsTheNextReminderInTheSameSlot() = runBlocking {
        addLoan(due = friday.plusDays(1))
        container.reminders.turnOn()
        assertEquals("Payment due tomorrow", title(phone.allNotifications.single()))

        clock = friday.plusDays(1).atTime(8, 0, 5)
        container.reminders.dailyRun()
        assertEquals("the newer one replaces the older one", 1, phone.allNotifications.size)
        assertEquals("Payment due today", title(phone.allNotifications.single()))

        clock = friday.plusDays(2).atTime(8, 0, 5)
        container.reminders.dailyRun()
        assertEquals("Payment overdue", title(phone.allNotifications.single()))
        assertEquals("₱1,500.00 to Tita Baby was due on Nov 28", text(phone.allNotifications.single()))

        clock = friday.plusDays(3).atTime(8, 0, 5)
        container.reminders.dailyRun()
        assertEquals("overdue is said once, not every morning", 3, notifier.shown.size)
    }

    @Test
    fun nothingIsShownBeforeEightInTheMorning() = runBlocking {
        addLoan(due = friday)
        clock = friday.atTime(7, 30)

        container.reminders.turnOn()
        assertEquals(emptyList<Reminder>(), notifier.shown)
        assertNull("an early look is not counted as today's check", container.settings.lastReminderCheck.first())
        assertEquals("lined up for 8:00 today", listOf("ensure PT30M"), alarm.asked)

        clock = friday.atTime(8, 0)
        container.reminders.onAppOpened()
        assertEquals(1, notifier.shown.size)
    }

    @Test
    fun aPhoneThatBlocksNotificationsShowsNothingAndCatchesUpOnceAllowed() = runBlocking {
        addLoan(due = friday)
        phone.setNotificationsEnabled(false)

        container.reminders.turnOn()
        assertEquals(emptyList<Reminder>(), notifier.shown)
        assertNull("nothing is marked as shown while blocked", container.settings.lastReminderCheck.first())

        phone.setNotificationsEnabled(true)
        container.reminders.onAppOpened()
        assertEquals(1, notifier.shown.size)
    }

    @Test
    fun openingTheAppCatchesUpOnlyOnADayTheCheckHasNotRun() = runBlocking {
        addLoan(due = friday.plusDays(1))
        container.reminders.turnOn()
        assertEquals(1, notifier.shown.size)
        assertEquals(false, container.settings.lastReminderCheck.first()?.inBackground)

        // Later the same day: today's check is done, so opening the app only makes sure the alarm is set.
        clock = friday.atTime(13, 0)
        addLoan(due = friday)
        container.reminders.onAppOpened()
        assertEquals(1, notifier.shown.size)
        assertEquals(listOf("ensure PT23H", "ensure PT19H"), alarm.asked)

        // Next day, the phone held the background run back. Before eight: still nothing.
        clock = friday.plusDays(1).atTime(7, 0)
        container.reminders.onAppOpened()
        assertEquals(1, notifier.shown.size)

        // After eight the app catches up: "due today" for the first loan, "overdue" for the second.
        clock = friday.plusDays(1).atTime(9, 15)
        container.reminders.onAppOpened()
        // (Which of the two is posted first does not matter.)
        assertEquals("Payment due tomorrow", notifier.shown.first().title)
        assertEquals(setOf("Payment due today", "Payment overdue"), notifier.shown.drop(1).map { it.title }.toSet())
        container.reminders.onAppOpened()
        assertEquals("and only once", 3, notifier.shown.size)
    }

    @Test
    fun theBackgroundRunLinesUpTheNextMorningAndIsRecordedAsBackground() = runBlocking {
        container.reminders.turnOn()
        alarm.asked.clear()

        clock = friday.plusDays(1).atTime(8, 0, 10)
        container.reminders.dailyRun()
        assertEquals(listOf("after-this-run PT23H59M50S"), alarm.asked)
        val check = container.settings.lastReminderCheck.first()
        assertNotNull(check)
        assertTrue(check!!.inBackground)
        assertEquals(clock.atZone(java.time.ZoneId.systemDefault()).toInstant(), check.at)
    }

    @Test
    fun switchingOffCancelsTheDailyCheckAndNothingMoreIsShown() = runBlocking {
        addLoan(due = friday.plusDays(1))
        container.reminders.turnOn()
        alarm.asked.clear()

        container.reminders.turnOff()
        assertEquals(listOf("cancel"), alarm.asked)
        assertFalse(container.settings.remindersOn.first())

        // A run that was already on its way does nothing and lines nothing up.
        clock = friday.plusDays(1).atTime(8, 0, 5)
        container.reminders.dailyRun()
        container.reminders.onAppOpened()
        assertEquals(1, notifier.shown.size)
        assertEquals(listOf("cancel"), alarm.asked)
    }

    @Test
    fun aPaydayIsRemindedUnlessItsPayIsAlreadyLogged() = runBlocking {
        val payday = addFridayPayday()
        container.reminders.turnOn()
        assertEquals("Payday today", title(phone.allNotifications.single()))
        assertEquals("Sahod · ₱15,000.00. Tap to mark it received.", text(phone.allNotifications.single()))

        // The same payday a week later, with the pay logged the evening before: no reminder.
        container.ledger.add(
            TransactionEntity(
                purpose = TxPurpose.INCOME, amountCentavos = 1_500_000, occurredOn = friday.plusDays(6),
                toWalletId = cashId(), categoryId = salaryId(), payScheduleId = payday, createdAt = Instant.now(),
            ),
        )
        clock = friday.plusDays(7).atTime(8, 0, 5)
        container.reminders.dailyRun()
        assertEquals(1, notifier.shown.size)
    }

    @Test
    fun aLoanOrPaydayWithItsOwnSwitchOffIsLeftOut() = runBlocking {
        addLoan(due = friday, remind = false)
        val payday = addFridayPayday()
        container.database.payScheduleDao().update(container.database.payScheduleDao().get(payday)!!.copy(remind = false))

        container.reminders.turnOn()
        assertEquals(emptyList<Reminder>(), notifier.shown)
        assertNotNull("the check itself ran", container.settings.lastReminderCheck.first())
    }

    @Test
    fun aTappedReminderCarriesWhereToGoAndHidesDetailsOnALockedPhone() = runBlocking {
        val loanId = addLoan(due = friday, direction = LoanDirection.OWED_TO_ME)
        container.reminders.turnOn()

        val notification = phone.allNotifications.single()
        assertEquals("Money due to you today", title(notification))
        assertEquals(AndroidReminderNotifier.CHANNEL, notification.channelId)
        val channel = app.getSystemService(NotificationManager::class.java).getNotificationChannel(AndroidReminderNotifier.CHANNEL)
        assertEquals("the channel the phone's settings list", "Reminders", channel?.name?.toString())
        assertTrue("goes away when tapped", notification.flags and Notification.FLAG_AUTO_CANCEL != 0)

        val opens = shadowOf(notification.contentIntent).savedIntent
        assertEquals(ReminderOpen(ReminderKind.LOAN, loanId), ReminderIntents.read(opens))
        assertEquals("com.pitakaview.app.MainActivity", opens.component?.className)

        // The version for a locked phone that hides private details: the title only.
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
        assertEquals("Money due to you today", title(notification.publicVersion))
        assertNull(text(notification.publicVersion))
    }

    @Test
    fun onlyOurOwnWellFormedIntentsAreReadAsReminders() {
        assertNull("an ordinary launch", ReminderIntents.read(Intent(Intent.ACTION_MAIN)))
        assertNull(ReminderIntents.read(null))
        assertNull("an unknown kind", ReminderIntents.read(Intent().putExtra(ReminderIntents.EXTRA_KIND, "WALLET").putExtra(ReminderIntents.EXTRA_ID, 3L)))
        assertNull("no id", ReminderIntents.read(Intent().putExtra(ReminderIntents.EXTRA_KIND, "LOAN")))
        assertNull("an id that cannot be a row", ReminderIntents.read(Intent().putExtra(ReminderIntents.EXTRA_KIND, "LOAN").putExtra(ReminderIntents.EXTRA_ID, 0L)))
        assertEquals(ReminderOpen(ReminderKind.PAYDAY, 4), ReminderIntents.read(ReminderIntents.open(app, ReminderKind.PAYDAY, 4)))
    }
}
