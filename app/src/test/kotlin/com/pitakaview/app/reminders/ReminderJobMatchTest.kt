package com.pitakaview.app.reminders

import android.app.job.JobInfo
import android.content.ComponentName
import android.os.PersistableBundle
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.pitakaview.app.FitakaApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.util.UUID

/**
 * Since 1.1.0 the app gives the phone two kinds of job: the reminders' morning check and the daily look for a
 * new version. Opening the app asks the phone whether it still holds the REMINDER'S job; the update's job must
 * not answer for it, or a reminder job the phone lost would never be lined up again.
 *
 * With the real look at the phone's list (not a test's stand-in), on Android 13, where the test phone keeps one
 * list for every handle on it (from Android 14 it keeps one per handle, see PhoneJobListTest).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReminderJobMatchTest {
    private val app: FitakaApp get() = ApplicationProvider.getApplicationContext()
    private val container get() = app.container

    @Before
    fun setUp() {
        val config = Configuration.Builder().setMinimumLoggingLevel(Log.DEBUG).setExecutor(SynchronousExecutor()).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(app, config)
        container.reminderClock = { LocalDateTime.of(2026, 11, 27, 15, 0) }
    }

    private fun waiting() = WorkManager.getInstance(app).getWorkInfosByTag(WorkManagerReminderAlarm.TAG).get().filter { it.state == WorkInfo.State.ENQUEUED }

    /** A job on the phone's list as WorkManager writes it: its service, and the id of the work it runs. */
    private fun phoneHoldsAJobFor(jobId: Int, workId: String) {
        val extras = PersistableBundle().apply { putString(WORK_ID_EXTRA, workId) }
        workJobList(app)!!.schedule(
            JobInfo.Builder(jobId, ComponentName(app.packageName, WORK_JOB_SERVICE)).setMinimumLatency(60_000).setExtras(extras).build(),
        )
    }

    @Test
    fun aJobForOtherWorkDoesNotPassForTheReminders() = runBlocking {
        container.reminders.turnOn()
        val lined = waiting().single()
        // The phone holds a job of the app's, but for other work (the daily look for a new version).
        phoneHoldsAJobFor(1, UUID.randomUUID().toString())

        container.reminders.onAppOpened()
        assertNotEquals("the check the phone does not hold is lined up again", lined.id, waiting().single().id)
    }

    @Test
    fun theRemindersOwnJobIsFoundByItsWorkId() = runBlocking {
        container.reminders.turnOn()
        val lined = waiting().single()
        phoneHoldsAJobFor(2, lined.id.toString())

        repeat(3) { container.reminders.onAppOpened() }
        assertEquals("kept: the phone holds it", lined.id, waiting().single().id)
    }
}
