package com.pitakaview.app.reminders

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pitakaview.app.MainActivity
import com.pitakaview.app.R
import com.pitakaview.app.domain.Reminder
import com.pitakaview.app.domain.ReminderKind

/** Shows reminders on the phone. An interface so the rules around it can be tested with a stand-in. */
interface ReminderNotifier {
    /** False when the phone will not show this app's notifications: never allowed, or switched off since. */
    fun canNotify(): Boolean

    fun show(reminder: Reminder)
}

/** What a tapped reminder asks the app to open. */
data class ReminderOpen(val kind: ReminderKind, val rowId: Long)

/** The intent a reminder carries, and how the app reads it back. */
object ReminderIntents {
    const val EXTRA_KIND = "com.pitakaview.app.reminder.KIND"
    const val EXTRA_ID = "com.pitakaview.app.reminder.ID"

    fun open(context: Context, kind: ReminderKind, rowId: Long): Intent =
        Intent(context, MainActivity::class.java)
            // Brings the open app forward instead of stacking a second copy of it.
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_KIND, kind.name)
            .putExtra(EXTRA_ID, rowId)

    /** Null for an ordinary launch, and for anything that does not read as one of our reminders. */
    fun read(intent: Intent?): ReminderOpen? {
        val name = intent?.getStringExtra(EXTRA_KIND) ?: return null
        val kind = ReminderKind.entries.firstOrNull { it.name == name } ?: return null
        val id = intent.getLongExtra(EXTRA_ID, -1)
        return if (id > 0) ReminderOpen(kind, id) else null
    }
}

class AndroidReminderNotifier(private val context: Context) : ReminderNotifier {
    // Made on first use: the container builds this class at every app start.
    private val manager by lazy { NotificationManagerCompat.from(context) }

    override fun canNotify(): Boolean = manager.areNotificationsEnabled()

    // The caller asks canNotify() first; without the permission Android drops the notification, it does not throw.
    @SuppressLint("MissingPermission")
    override fun show(reminder: Reminder) {
        // Creating a channel that exists changes nothing, so there is no "already made" flag to keep.
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Reminders")
                .setDescription("Paydays and payments due")
                .build(),
        )
        val open = PendingIntent.getActivity(
            context,
            reminder.notificationId,
            ReminderIntents.open(context, reminder.kind, reminder.rowId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // On a locked phone set to hide private details, only the title shows: no names, no amounts.
        val whenLocked = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(reminder.title)
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(reminder.title)
            .setContentText(reminder.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(whenLocked)
            .build()
        manager.notify(reminder.notificationId, notification)
    }

    companion object {
        const val CHANNEL = "reminders"
    }
}
