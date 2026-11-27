package com.pitakaview.app.reminders

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The app asks the phone "do you hold a job for my reminder check?" at every opening. That is only worth
 * anything if it looks where WorkManager puts its jobs. From Android 14 the phone keeps separate, named
 * lists, and WorkManager uses one of its own: a look at the phone's plain list finds nothing there, ever.
 *
 * Which list is held against WorkManager's own function for reaching its list, so a WorkManager that
 * moves its jobs fails here instead of quietly making the look useless.
 *
 * What these tests cannot show: that a real phone gives back, through a second handle on the same named
 * list, what was put in through the first. The test phone (Robolectric 4.18.1) keeps jobs per handle.
 * That part is Android's own and is looked at on the owner's phone.
 */
@RunWith(RobolectricTestRunner::class)
class PhoneJobListTest {
    private val app: Context get() = ApplicationProvider.getApplicationContext()
    private val plainList: JobScheduler get() = app.getSystemService(JobScheduler::class.java)

    /**
     * The list WorkManager itself schedules into, got from WorkManager's own function for it. Called by name:
     * the function is for WorkManager's use only, and Kotlin does not let other code call it directly.
     */
    private val workManagersOwnList: JobScheduler
        get() = Class.forName("androidx.work.impl.background.systemjob.JobSchedulerExtKt")
            .getMethod("getWmJobScheduler", Context::class.java).invoke(null, app) as JobScheduler

    private fun job(id: Int, service: String = WORK_JOB_SERVICE) =
        JobInfo.Builder(id, ComponentName(app.packageName, service)).setMinimumLatency(60_000).build()

    /** Android 14 and later (the tests run as Android 16): a named list, and the same name as WorkManager's. */
    @Test
    fun theAppLooksInTheListWorkManagerUses() {
        val theirs = workManagersOwnList.namespace
        assertNotNull("WorkManager uses a named list on this Android", theirs)
        assertEquals(theirs, workJobList(app)!!.namespace)
        assertNull("and that is not the phone's plain list", plainList.namespace)
    }

    /** Before Android 14 there is one list: WorkManager's jobs are in it, and the app finds them there. */
    @Test
    @Config(sdk = [33])
    fun beforeAndroid14ThePlainListIsTheOne() {
        workManagersOwnList.schedule(job(9))
        assertEquals(listOf(9), phoneJobs(app).map { it.id })
    }

    @Test
    fun onlyWorkManagersJobsAreCounted() {
        val list = plainList
        list.schedule(job(7))
        list.schedule(job(10, service = "com.pitakaview.app.SomethingElse"))
        assertEquals(listOf(7), workJobsIn(list).map { it.id })
    }

    /** The app tells its reminder job from its update job by this extra; it must be the one WorkManager writes. */
    @Test
    fun theWorkIdIsReadUnderTheNameWorkManagerWritesItUnder() {
        val theirs = Class.forName("androidx.work.impl.background.systemjob.SystemJobInfoConverter")
            .getDeclaredField("EXTRA_WORK_SPEC_ID").apply { isAccessible = true }.get(null)
        assertEquals(theirs, WORK_ID_EXTRA)
    }

    @Test
    fun withNoJobsThePhoneHasNone() {
        assertEquals(0, phoneJobs(app).size)
    }
}
