package com.pitakaview.app.reminders

import android.app.Notification
import android.app.NotificationManager
import android.os.Looper
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.pitakaview.app.FitakaApp
import com.pitakaview.app.data.db.entity.LoanEntity
import com.pitakaview.app.domain.LoanDirection
import com.pitakaview.app.domain.Reminder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
 * The daily check as real WorkManager work: the real [WorkManagerReminderAlarm] and the real
 * [ReminderWorker], run by WorkManager's own test set-up, which lets a test say "the waiting time is over".
 * Only the clock is a stand-in.
 */
@RunWith(RobolectricTestRunner::class)
class ReminderWorkTest {
    private val app: FitakaApp get() = ApplicationProvider.getApplicationContext()
    private val container get() = app.container
    private val phone get() = shadowOf(app.getSystemService(NotificationManager::class.java))

    /** A Friday. */
    private val friday = LocalDate.of(2026, 11, 27)
    private var clock: LocalDateTime = friday.atTime(15, 0)

    @Before
    fun setUp() {
        val config = Configuration.Builder().setMinimumLoggingLevel(Log.DEBUG).setExecutor(SynchronousExecutor()).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(app, config)
        container.reminderClock = { clock }
    }

    private fun work() = WorkManager.getInstance(app).getWorkInfosForUniqueWork(WorkManagerReminderAlarm.NAME).get()

    private fun waiting() = work().filter { it.state == WorkInfo.State.ENQUEUED }

    /** Tells WorkManager the waiting time of [info] is over, and waits for the run to finish. */
    private fun runNow(info: WorkInfo) {
        WorkManagerTestInitHelper.getTestDriver(app)!!.setInitialDelayMet(info.id)
        val deadline = System.currentTimeMillis() + 10_000
        while (WorkManager.getInstance(app).getWorkInfoById(info.id).get()?.state?.isFinished != true) {
            check(System.currentTimeMillis() < deadline) { "the reminder work did not finish: ${work()}" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    private fun addLoan(due: LocalDate) = runBlocking {
        container.database.loanDao().insert(
            LoanEntity(
                direction = LoanDirection.I_OWE, counterparty = "Tita Baby", principalCentavos = 150_000, totalPayableCentavos = 150_000,
                startDate = friday.minusDays(30), dueDate = due, createdAt = Instant.parse("2026-10-01T00:00:00Z"),
            ),
        )
    }

    private fun titles() = phone.allNotifications.map { it.extras.getString(Notification.EXTRA_TITLE) }

    @Test
    fun switchingOnLinesUpOneCheckForTheComingMorning() = runBlocking {
        container.reminders.turnOn()

        val only = work().single()
        assertEquals(WorkInfo.State.ENQUEUED, only.state)
        assertEquals("15:00 now, so 17 hours to 8:00 tomorrow", Duration.ofHours(17).toMillis(), only.initialDelayMillis)
        assertTrue(WorkManagerReminderAlarm.TAG in only.tags)
    }

    @Test
    fun openingTheAppAgainAndAgainNeverStacksUpChecks() = runBlocking {
        container.reminders.turnOn()
        repeat(3) { container.reminders.onAppOpened() }
        assertEquals(1, work().size)
    }

    @Test
    fun eachRunShowsWhatIsDueAndLinesUpTheMorningAfter() = runBlocking {
        addLoan(due = friday.plusDays(1))
        container.reminders.turnOn()
        assertEquals(listOf("Payment due tomorrow"), titles())

        // Saturday morning: the system starts the work.
        clock = friday.plusDays(1).atTime(8, 0, 10)
        runNow(waiting().single())
        assertEquals(listOf("Payment due today"), titles())
        assertTrue("recorded as a background run", container.settings.lastReminderCheck.first()!!.inBackground)
        val sunday = waiting().single()
        assertEquals("lined up for 8:00 on Sunday", Duration.ofHours(24).minusSeconds(10).toMillis(), sunday.initialDelayMillis)

        // Sunday morning: the chain goes on by itself.
        clock = friday.plusDays(2).atTime(8, 0, 30)
        runNow(sunday)
        assertEquals(listOf("Payment overdue"), titles())
        assertEquals("and Monday is lined up, once", 1, waiting().size)
        assertEquals(Duration.ofHours(24).minusSeconds(30).toMillis(), waiting().single().initialDelayMillis)
    }

    @Test
    fun aRunStartedEarlyStillLeavesThatMorningLinedUp() = runBlocking {
        // The same evening, hours before its time (a forced run, or the phone's clock moved back).
        container.reminders.turnOn()
        clock = friday.atTime(20, 0)
        runNow(waiting().single())

        val next = waiting().single()
        assertEquals("8:00 tomorrow is 12 hours away", Duration.ofHours(12).toMillis(), next.initialDelayMillis)
    }

    @Test
    fun aCheckThatBreaksDoesNotEndTheChain() = runBlocking {
        addLoan(due = friday.plusDays(1))
        container.reminders.turnOn()
        container.reminderNotifier = object : ReminderNotifier {
            override fun canNotify() = true
            override fun show(reminder: Reminder) = error("the phone refused")
        }

        clock = friday.plusDays(1).atTime(8, 0, 10)
        runNow(waiting().single())
        assertEquals("the next morning is still lined up", 1, waiting().size)
    }

    @Test
    fun switchingOffTakesTheWaitingCheckAway() = runBlocking {
        container.reminders.turnOn()
        container.reminders.turnOff()
        assertEquals(emptyList<WorkInfo>(), waiting())

        // And on again starts a fresh one.
        container.reminders.turnOn()
        assertEquals(1, waiting().size)
    }
}
