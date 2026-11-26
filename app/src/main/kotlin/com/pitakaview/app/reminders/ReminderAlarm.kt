package com.pitakaview.app.reminders

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import java.time.Duration

/** Wakes the app for its daily look for reminders. An interface so the rules around it can be tested with a stand-in. */
interface ReminderAlarm {
    /** Makes sure a check is waiting to run after [delay]. Does nothing when one is already waiting. */
    fun ensureScheduled(delay: Duration)

    /** Called by the check that is running now: lines up the next one, to start [delay] after this one ends. */
    fun scheduleAfterThisRun(delay: Duration)

    fun cancel()
}

/**
 * The daily check as WorkManager work, which the system keeps across restarts of the phone.
 *
 * It is a chain of one-off runs, each lining up the next for the coming morning, and not "repeat every
 * 24 hours": a repeating run that the phone delays one morning would stay that late on every day after.
 */
class WorkManagerReminderAlarm(private val context: Context) : ReminderAlarm {
    // Asked for only when used, so an app with reminders off never starts WorkManager.
    private val work: WorkManager get() = WorkManager.getInstance(context)

    private fun run(delay: Duration) = OneTimeWorkRequest.Builder(ReminderWorker::class.java)
        .setInitialDelay(if (delay.isNegative) Duration.ZERO else delay)
        .addTag(TAG)
        .build()

    override fun ensureScheduled(delay: Duration) {
        work.enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, run(delay))
    }

    // Appended, not replaced: replacing would cancel the very run that is asking.
    override fun scheduleAfterThisRun(delay: Duration) {
        work.enqueueUniqueWork(NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, run(delay))
    }

    override fun cancel() {
        work.cancelUniqueWork(NAME)
    }

    companion object {
        const val NAME = "reminder-check"
        const val TAG = "reminder-check"
    }
}
