package com.pitakaview.app.update

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pitakaview.app.APP_NAME
import com.pitakaview.app.MainActivity
import com.pitakaview.app.R

/** Tells the owner, on the phone, that a new version is out. An interface so tests can stand in for the phone. */
interface UpdateNotifier {
    /** False when the phone will not show this app's notifications. */
    fun canNotify(): Boolean

    fun show(version: AppVersion)

    /** The app updated itself to [version] (installing closed it); a tap opens it again. */
    fun showUpdated(version: String)

    /** Takes the notification away again (the update is installed, or checks were switched off). */
    fun clear()
}

/** What a tapped "new version" notification carries, and how the app reads it back. */
object UpdateIntents {
    /** StoredNamesTest holds it: a notification already on the phone carries it. */
    const val EXTRA_OPEN = "com.pitakaview.app.update.OPEN"

    fun open(context: Context): Intent = Intent(context, MainActivity::class.java)
        // Brings the open app forward instead of stacking a second copy of it.
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(EXTRA_OPEN, true)

    fun read(intent: Intent?): Boolean = intent?.getBooleanExtra(EXTRA_OPEN, false) == true
}

object UpdateNotificationText {
    fun title(version: AppVersion) = "$APP_NAME $version is out"
    const val TEXT = "Tap to see it in Settings. Your entries stay as they are."
    fun updatedTitle(version: String) = "$APP_NAME is updated to $version"
    const val UPDATED_TEXT = "Tap to open it. Your entries are all there."
}

class AndroidUpdateNotifier(private val context: Context) : UpdateNotifier {
    // Made on first use: the container builds this class at every app start.
    private val manager by lazy { NotificationManagerCompat.from(context) }

    override fun canNotify(): Boolean = manager.areNotificationsEnabled()

    // The caller asks canNotify() first; without the permission Android drops the notification, it does not throw.
    override fun show(version: AppVersion) =
        post(UpdateNotificationText.title(version), UpdateNotificationText.TEXT, UpdateIntents.open(context))

    override fun showUpdated(version: String) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        post(UpdateNotificationText.updatedTitle(version), UpdateNotificationText.UPDATED_TEXT, launch)
    }

    // The caller asks canNotify() first; without the permission Android drops the notification, it does not throw.
    @SuppressLint("MissingPermission")
    private fun post(title: String, text: String, tap: Intent) {
        // High: the phone shows it as a banner across the top (heads-up), as the owner asked (2026-11-27).
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName("App updates")
                .setDescription("When a new version of $APP_NAME is out, and when it has been installed")
                .build(),
        )
        val open = PendingIntent.getActivity(context, NOTIFICATION_ID, tap, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_update)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun clear() = manager.cancel(NOTIFICATION_ID)

    companion object {
        /** The phone keeps the owner's choices for this kind of notification under it. StoredNamesTest holds it. */
        const val CHANNEL = "updates"

        /** Reminders use 1,000,000 and up; this one slot is the update's. */
        const val NOTIFICATION_ID = 7
    }
}
