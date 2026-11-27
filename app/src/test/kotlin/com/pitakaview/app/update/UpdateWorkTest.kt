package com.pitakaview.app.update

import android.os.Looper
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.pitakaview.app.FitakaApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit

/**
 * The daily look as real WorkManager work (WorkManager's own test set-up): lined up once, every 24 hours,
 * only while online, kept as it is when the app opens again, and gone when switched off. Its run looks and tells.
 */
@RunWith(RobolectricTestRunner::class)
class UpdateWorkTest {
    private val app: FitakaApp get() = ApplicationProvider.getApplicationContext()
    private val github = FakeReleaseSource()
    private val notifier = FakeNotifier()

    @Before
    fun setUp() {
        val config = Configuration.Builder().setMinimumLoggingLevel(Log.DEBUG).setExecutor(SynchronousExecutor()).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(app, config)
        app.container.updateSchedule = WorkManagerUpdateSchedule(app)
        app.container.releaseSource = github
        app.container.updateNotifier = notifier
        app.container.packageReader = FakePackages()
    }

    private fun work(): List<WorkInfo> = WorkManager.getInstance(app).getWorkInfosForUniqueWork(WorkManagerUpdateSchedule.NAME).get()

    @Test
    fun oneDailyLookOnlineAndOpeningTheAppDoesNotPushItBack() = runBlocking {
        app.container.updates.onAppOpened()
        val first = work().single()
        assertEquals(WorkInfo.State.ENQUEUED, first.state)
        assertEquals(TimeUnit.DAYS.toMillis(1), first.periodicityInfo!!.repeatIntervalMillis)
        assertEquals(NetworkType.CONNECTED, first.constraints.requiredNetworkType)

        repeat(3) { app.container.updates.onAppOpened() }
        assertEquals("the same one, not a new one each time", first.id, work().single().id)

        app.container.updates.setOn(false)
        assertTrue(work().all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun theDailyRunLooksAndTellsThePhone() = runBlocking {
        github.publish("1.2.0", UpdateSamples.apkBytes())
        app.container.updates.onAppOpened()
        github.asked.clear()
        val look = work().single()
        val driver = WorkManagerTestInitHelper.getTestDriver(app)!!
        driver.setAllConstraintsMet(look.id)
        driver.setPeriodDelayMet(look.id)
        val deadline = System.currentTimeMillis() + 10_000
        while (notifier.shown.isEmpty()) {
            check(System.currentTimeMillis() < deadline) { "the daily look did not run: ${work()}" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        assertEquals(listOf("latest"), github.asked)
        assertEquals(listOf("out 1.2.0"), notifier.shown)
    }
}
