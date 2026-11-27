package com.pitakaview.app.reminders

import com.pitakaview.app.data.db.FitakaDatabase
import com.pitakaview.app.data.prefs.SettingsRepository
import com.pitakaview.app.domain.PaydayTracker
import com.pitakaview.app.domain.ReminderPlanner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Notifications about paydays and payments due. The owner switches them on in Settings; from then on a
 * check runs each morning in the background ([ReminderAlarm]), and once more when the app is opened on a
 * day the background run has not happened (some phones hold background work back).
 *
 * What to show is decided by [ReminderPlanner] from the same rows as "Coming up" on Home. Each reminder
 * is shown once: the keys of those already shown are kept in settings.
 */
class Reminders(
    private val database: () -> FitakaDatabase,
    private val settings: SettingsRepository,
    private val notifier: () -> ReminderNotifier,
    private val alarm: () -> ReminderAlarm,
    private val now: () -> LocalDateTime = LocalDateTime::now,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    // The background run and an app-open check can meet; one at a time keeps a reminder from showing twice.
    private val oneAtATime = Mutex()

    private fun delayToNextRun(): Duration = now().let { Duration.between(it, ReminderPlanner.nextRun(it)) }

    /** Switched on in Settings: remember it, line up the daily check, and look right away. */
    suspend fun turnOn() {
        settings.setRemindersOn(true)
        alarm().ensureScheduled(delayToNextRun())
        check(inBackground = false)
    }

    suspend fun turnOff() {
        settings.setRemindersOn(false)
        alarm().cancel()
    }

    /**
     * The app came to the front. With reminders off this is one read of settings already in memory.
     * With them on it makes sure the daily check is still lined up (a phone can drop it when it stops the
     * app), and looks now if today's check has not run although its hour has passed.
     */
    suspend fun onAppOpened() {
        if (!settings.remindersOn.first()) return
        alarm().ensureScheduled(delayToNextRun())
        val current = now()
        val lastCheckedOn = settings.lastReminderCheck.first()?.at?.atZone(zone())?.toLocalDate()
        if (ReminderPlanner.isTime(current) && (lastCheckedOn == null || lastCheckedOn < current.toLocalDate())) {
            check(inBackground = false)
        }
    }

    /**
     * A new version of the app was installed over this one. Installing closes the app, and the owner may not
     * open it again before the next morning, so the daily check is made sure of here, as an opening would.
     */
    suspend fun afterUpdate() {
        if (!settings.remindersOn.first()) return
        alarm().ensureScheduled(delayToNextRun())
    }

    /** The background run: line up tomorrow's first, so a failure below cannot end the chain, then look. */
    suspend fun dailyRun() {
        if (!settings.remindersOn.first()) return
        alarm().scheduleAfterThisRun(delayToNextRun())
        check(inBackground = true)
    }

    /**
     * Looks for reminders that are due and not yet shown, and shows them. Returns how many it showed.
     * Does nothing before [ReminderPlanner.HOUR] in the morning, or while the phone blocks the app's
     * notifications (so they are still shown once it stops blocking them).
     */
    suspend fun check(inBackground: Boolean): Int = oneAtATime.withLock {
        val current = now()
        if (!settings.remindersOn.first() || !ReminderPlanner.isTime(current) || !notifier().canNotify()) return@withLock 0
        val today = current.toLocalDate()
        val db = database()
        val schedules = db.payScheduleDao().observeActive().first()
        // Pay already received is only asked for when some payday wants a reminder.
        val receipts = if (schedules.any { it.remind }) {
            db.transactionDao().observePayReceipts(since = today.minusDays(PaydayTracker.LOOK_BACK_DAYS)).first()
        } else {
            emptyList()
        }
        val plan = ReminderPlanner.plan(today, db.loanDao().observeOpen().first(), schedules, receipts, zone())
        val shown = settings.remindersShown.first()
        val fresh = plan.filter { it.key !in shown }
        fresh.forEach { notifier().show(it) }
        settings.recordReminderCheck(
            at = current.atZone(zone()).toInstant(),
            inBackground = inBackground,
            shown = (shown + fresh.map { it.key }).filterTo(HashSet()) { ReminderPlanner.isKeyFresh(it, today) },
        )
        fresh.size
    }
}
