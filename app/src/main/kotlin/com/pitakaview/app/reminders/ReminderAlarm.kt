package com.pitakaview.app.reminders

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import kotlinx.coroutines.flow.first
import java.time.Duration

/** Wakes the app for its daily look for reminders. An interface so the rules around it can be tested with a stand-in. */
interface ReminderAlarm {
    /** Makes sure one check is really waiting to run after [delay]: with the phone, not only on the app's own books. */
    suspend fun ensureScheduled(delay: Duration)

    /** Called by the check that is running now: lines up the next one, and returns only when the phone has been given it. */
    suspend fun scheduleAfterThisRun(delay: Duration)

    fun cancel()
}

/**
 * The daily check as WorkManager work, which the system keeps across restarts of the phone.
 *
 * It is a chain of one-off runs, each lining up the next for the coming morning, and not "repeat every
 * 24 hours": a repeating run that the phone delays one morning would stay that late on every day after.
 *
 * How the next run is lined up was changed after the review before 1.0 (2026-11-27). It used to be put
 * BEHIND the run that was asking (appended to it), so WorkManager held it back until that run had ended,
 * and gave it to the phone only afterwards, from a background task, once the phone had been told the job
 * was over. On the owner's phone, after an 8:00 run, the phone's job list was empty an hour later though
 * the app had not been stopped in between. Two things are different now:
 *
 *  - Each run lines the next one up as work of its own and waits for the answer. WorkManager (2.12.0, read
 *    from its bytecode) gives new work to the phone before it reports the enqueue as done, so the phone has
 *    tomorrow's job while it still counts today's as running.
 *  - Whenever the app opens, it looks at the phone's own job list, and not only at what WorkManager
 *    believes. A check the phone does not hold is lined up again.
 *
 * Every check carries [TAG], and everything here finds checks by it, so one waits and never two: the first
 * goes in under the unique name [NAME], replacing; a run cancels any check still waiting before it lines up
 * the next. Both strings are as they were in the first builds, so a check lined up by an older build is
 * found, counted and cancelled like any other.
 */
class WorkManagerReminderAlarm(private val context: Context) : ReminderAlarm {
    // Asked for only when used, so an app with reminders off never starts WorkManager.
    private val work: WorkManager get() = WorkManager.getInstance(context)

    /**
     * Whether the phone's own list holds a job for this app's work. A property so a test, where WorkManager
     * keeps work to itself and gives the phone nothing, can say what the phone has.
     *
     * A phone whose list cannot be read is taken at WorkManager's word: better to keep the check there is
     * than to cancel and line up again at every opening of the app.
     */
    var phoneHasAJob: () -> Boolean = {
        try {
            phoneJobs(context).isNotEmpty()
        } catch (e: RuntimeException) {
            Log.w(LOG, "The phone's job list could not be read; going by WorkManager's books", e)
            true
        }
    }

    private fun run(delay: Duration) = OneTimeWorkRequest.Builder(ReminderWorker::class.java)
        .setInitialDelay(if (delay.isNegative) Duration.ZERO else delay)
        .addTag(TAG)
        .build()

    private suspend fun checks(): List<WorkInfo> = work.getWorkInfosByTagFlow(TAG).first()

    override suspend fun ensureScheduled(delay: Duration) {
        val unfinished = checks().filter { !it.state.isFinished }
        // A check running right now lines up the next one itself.
        if (unfinished.any { it.state == WorkInfo.State.RUNNING }) return
        if (unfinished.size == 1 && phoneHasAJob()) return
        // Nothing is waiting, or more than one is, or WorkManager believes one is waiting and the phone holds
        // no job for it. Start afresh: whatever is on the books goes, and one check is given to the phone now.
        work.cancelAllWorkByTag(TAG).await()
        work.enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, run(delay)).await()
    }

    override suspend fun scheduleAfterThisRun(delay: Duration) {
        // A check already waiting means the phone started this run a second time (the first was cut short
        // after lining one up). It goes, so that one waits and not two.
        for (waiting in checks()) {
            if (waiting.state == WorkInfo.State.ENQUEUED || waiting.state == WorkInfo.State.BLOCKED) work.cancelWorkById(waiting.id).await()
        }
        // Work of its own, not behind this run, and not under the unique name: replacing under that name
        // would cancel the very run that is asking.
        work.enqueue(run(delay)).await()
    }

    override fun cancel() {
        work.cancelAllWorkByTag(TAG)
    }

    companion object {
        /** The unique name the first check goes in under. StoredNamesTest holds it: older builds used it for every check. */
        const val NAME = "reminder-check"

        /** On every check. What finds, counts and cancels them. StoredNamesTest holds it. */
        const val TAG = "reminder-check"

        private const val LOG = "FitakaReminders"
    }
}

/** The service WorkManager's jobs run in; the phone lists a waiting job under it. */
internal const val WORK_JOB_SERVICE = "androidx.work.impl.background.systemjob.SystemJobService"

/**
 * From Android 14 the phone keeps separate, named lists of jobs, and WorkManager (2.10 and later) puts its
 * jobs in the list of this name (read from the bytecode of 2.12.0; PhoneJobListTest holds it against
 * WorkManager's own way of reaching its list, so a new WorkManager that moves it fails that test).
 */
internal const val WORK_JOB_LIST = "androidx.work.systemjobscheduler"

/** The one of the phone's lists that WorkManager puts its jobs in. */
internal fun workJobList(context: Context): JobScheduler? {
    val phone = context.getSystemService(JobScheduler::class.java) ?: return null
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) phone.forNamespace(WORK_JOB_LIST) else phone
}

/** The jobs in [list] that are WorkManager's. */
internal fun workJobsIn(list: JobScheduler): List<JobInfo> = list.allPendingJobs.filter { it.service.className == WORK_JOB_SERVICE }

/** The jobs the phone holds for WorkManager's work in this app, read from where WorkManager puts them. */
internal fun phoneJobs(context: Context): List<JobInfo> = workJobList(context)?.let(::workJobsIn).orEmpty()
