package com.pitakaview.app.reminders

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pitakaview.app.FitakaApp
import kotlinx.coroutines.CancellationException

/** The daily look for reminders, started by the system in the background. All the rules live in [Reminders]. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val reminders = (applicationContext as FitakaApp).container.reminders
        try {
            reminders.dailyRun()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Reported as done on purpose: a run marked failed would also fail the next morning's run that is
            // lined up behind it, and one bad morning would end reminders for good. The error is in the log,
            // and Settings keeps showing the time of the last check that did work.
            Log.e(TAG, "The reminder check failed", e)
        }
        return Result.success()
    }

    private companion object {
        const val TAG = "FitakaReminders"
    }
}
