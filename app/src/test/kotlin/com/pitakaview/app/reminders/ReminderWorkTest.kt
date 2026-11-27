package com.pitakaview.app.reminders

import android.app.Notification
import android.app.NotificationManager
import android.os.Looper
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.OneTimeWorkRequest
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
        // WorkManager's test set-up keeps work to itself and gives the phone no job. A healthy phone has a
        // job for whatever is waiting; the test of an unhealthy one says so itself.
        alarm.phoneHolds = { id -> waiting().any { it.id == id } }
    }

    /** Every check there is or was, under either of its two names. */
    private fun work() = WorkManager.getInstance(app).getWorkInfosByTag(WorkManagerReminderAlarm.TAG).get()

    private val alarm get() = container.reminderAlarm as WorkManagerReminderAlarm

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

    /**
     * While a check is still running, the next morning's is already waiting in its own right, not held back
     * until this one has ended. Held back (as it was until 2026-11-27) it was given to the phone only after
     * the phone had been told the job was over; on the owner's phone, an hour after an 8:00 run, the phone's
     * job list was empty.
     */
    @Test
    fun theNextCheckIsAlreadyWaitingWhileThisOneStillRuns() = runBlocking {
        addLoan(due = friday.plusDays(1))
        container.reminders.turnOn()
        val seenDuringTheRun = mutableListOf<List<WorkInfo.State>>()
        val real = container.reminderNotifier
        container.reminderNotifier = object : ReminderNotifier {
            override fun canNotify() = real.canNotify()
            override fun show(reminder: Reminder) {
                seenDuringTheRun += work().map { it.state }.filter { !it.isFinished }.sortedBy { it.name }
                real.show(reminder)
            }
        }

        clock = friday.plusDays(1).atTime(8, 0, 10)
        runNow(waiting().single())
        assertEquals(
            "one waiting (tomorrow's) and one running (this check), nothing held back",
            listOf(listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING)), seenDuringTheRun.distinct(),
        )
        assertEquals("and afterwards one waits", WorkInfo.State.ENQUEUED, work().single { !it.state.isFinished }.state)
    }

    /** The phone can start one run a second time (the first was cut short). One check waits after it, not two. */
    @Test
    fun aRunStartedTwiceLeavesOneCheckWaiting() = runBlocking {
        container.reminders.turnOn()
        clock = friday.plusDays(1).atTime(8, 0, 10)
        runNow(waiting().single())
        val first = waiting().single()

        // The same run asks again, as it does when it is started a second time.
        alarm.scheduleAfterThisRun(Duration.ofHours(24))
        val second = waiting().single()
        assertTrue("the one from the first start went", second.id != first.id)
    }

    /**
     * WorkManager can believe a check is waiting while the phone's own list has no job for it (the phone
     * dropped it, an update cleared it, or it was never handed over). Opening the app looks at the phone's
     * list and lines one up again: one, not two.
     */
    @Test
    fun aCheckThePhoneHasLostIsLinedUpAgainWhenTheAppOpens() = runBlocking {
        container.reminders.turnOn()
        val lost = waiting().single()
        alarm.phoneHolds = { false }

        container.reminders.onAppOpened()
        val fresh = waiting().single()
        assertTrue("a new check took the lost one's place", fresh.id != lost.id)
        assertTrue("the lost one waits no more", work().none { it.id == lost.id && !it.state.isFinished })

        // With the phone holding the job again, further opens change nothing.
        alarm.phoneHolds = { id -> waiting().any { it.id == id } }
        repeat(3) { container.reminders.onAppOpened() }
        assertEquals(fresh.id, waiting().single().id)
    }

    /** Two checks waiting (it should never happen) are put right at the next opening: one is left. */
    @Test
    fun twoChecksWaitingArePutRightWhenTheAppOpens() = runBlocking {
        container.reminders.turnOn()
        WorkManager.getInstance(app).enqueue(
            OneTimeWorkRequest.Builder(ReminderWorker::class.java).setInitialDelay(Duration.ofHours(5)).addTag(WorkManagerReminderAlarm.TAG).build(),
        ).result.get()
        assertEquals(2, waiting().size)

        container.reminders.onAppOpened()
        assertEquals(1, waiting().size)
    }

    /**
     * Switching off after a morning's run: the check that run lined up goes too. It has no name of its own
     * and is found by its tag; cancelling by the first check's name alone would leave it, and a reminder
     * would come the next morning with the switch off.
     */
    @Test
    fun switchingOffAfterARunTakesAwayTheCheckThatRunLinedUp() = runBlocking {
        container.reminders.turnOn()
        clock = friday.plusDays(1).atTime(8, 0, 10)
        runNow(waiting().single())
        assertEquals("tomorrow's is lined up", 1, waiting().size)

        container.reminders.turnOff()
        assertEquals(emptyList<WorkInfo>(), waiting())
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
