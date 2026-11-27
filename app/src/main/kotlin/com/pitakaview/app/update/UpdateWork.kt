package com.pitakaview.app.update

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import com.pitakaview.app.FitakaApp
import kotlinx.coroutines.CancellationException
import java.time.Duration

/** Wakes the app once a day to look for a new version. An interface so the rules can be tested with a stand-in. */
interface UpdateSchedule {
    /** Makes sure the daily look is lined up (does nothing when it already is). */
    suspend fun ensure()

    fun cancel()
}

/**
 * The daily look as WorkManager work, repeating every 24 hours and only while the phone is online. Unlike the
 * reminders' 8:00 check, it may drift: when in the day it runs does not matter.
 */
class WorkManagerUpdateSchedule(private val context: Context) : UpdateSchedule {
    private val work: WorkManager get() = WorkManager.getInstance(context)

    override suspend fun ensure() {
        val request = PeriodicWorkRequest.Builder(UpdateWorker::class.java, Duration.ofDays(1))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag(NAME)
            .build()
        // KEEP: one already lined up stays as it is, so opening the app does not push it back by a day.
        work.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request).await()
    }

    override fun cancel() {
        work.cancelUniqueWork(NAME)
    }

    companion object {
        /** The unique name and the tag of the daily look. StoredNamesTest holds it: the phone keeps it lined up under it. */
        const val NAME = "update-check"
    }
}

/** The daily look for a new version, started by the system in the background. The rules live in [Updates]. */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        try {
            (applicationContext as FitakaApp).container.updates.backgroundRun()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Tomorrow's look comes anyway (it repeats); a retry would only ask GitHub again sooner.
            Log.e("FitakaUpdates", "The update check failed", e)
        }
        return Result.success()
    }
}
