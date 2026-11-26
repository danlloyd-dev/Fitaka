package com.pitakaview.app.di

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.pitakaview.app.data.backup.BackupManager
import com.pitakaview.app.data.db.PitakaDatabase
import com.pitakaview.app.data.db.createPitakaDatabase
import com.pitakaview.app.data.prefs.SettingsRepository
import com.pitakaview.app.data.repo.LedgerRepository
import com.pitakaview.app.domain.DayClock
import com.pitakaview.app.lock.AndroidUnlocker
import com.pitakaview.app.lock.AppLock
import com.pitakaview.app.lock.Unlocker
import com.pitakaview.app.reminders.AndroidReminderNotifier
import com.pitakaview.app.reminders.ReminderAlarm
import com.pitakaview.app.reminders.ReminderNotifier
import com.pitakaview.app.reminders.Reminders
import com.pitakaview.app.reminders.WorkManagerReminderAlarm
import java.time.LocalDateTime

/** Holds app-wide singletons. Each is created lazily on first use, so app start does no extra work. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: PitakaDatabase by lazy { createPitakaDatabase(appContext) }
    val ledger: LedgerRepository by lazy { LedgerRepository(database) }
    val settings: SettingsRepository by lazy { SettingsRepository.create(appContext) }
    val backup: BackupManager by lazy {
        BackupManager(database, appContext.getDatabasePath(PitakaDatabase.FILE_NAME), AndroidSQLiteDriver(), appContext.cacheDir)
    }

    /** The one source of "today" for every screen. */
    val dayClock = DayClock()

    /**
     * Reminders and the three things they lean on. Properties, so a test can put in a clock set to the
     * morning or a stand-in for the phone's notifications; [reminders] reads them each time it runs.
     */
    var reminderClock: () -> LocalDateTime = LocalDateTime::now
    var reminderNotifier: ReminderNotifier = AndroidReminderNotifier(appContext)
    var reminderAlarm: ReminderAlarm = WorkManagerReminderAlarm(appContext)
    val reminders: Reminders by lazy {
        Reminders(database = { database }, settings = settings, notifier = { reminderNotifier }, alarm = { reminderAlarm }, now = { reminderClock() })
    }

    /** The app lock, and what asks the phone to check who is holding it (a property so tests can stand in for the phone). */
    var unlocker: Unlocker = AndroidUnlocker(appContext)
    val appLock: AppLock by lazy { AppLock(settings) }

    /**
     * Closes this process and opens the app fresh. Used after a restore, when everything in memory
     * still points at the replaced database. A property so tests can swap it out.
     */
    var restartApp: () -> Unit = {
        val launch = requireNotNull(appContext.packageManager.getLaunchIntentForPackage(appContext.packageName))
        appContext.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        Runtime.getRuntime().exit(0)
    }
}

/** The container, for screens that build their own ViewModels. */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
