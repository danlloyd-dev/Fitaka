package com.pitakaview.app.di

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.pitakaview.app.data.backup.BackupManager
import com.pitakaview.app.data.db.FitakaDatabase
import com.pitakaview.app.data.db.createFitakaDatabase
import com.pitakaview.app.data.prefs.SettingsRepository
import com.pitakaview.app.data.repo.LedgerRepository
import com.pitakaview.app.domain.DayClock
import com.pitakaview.app.importing.AndroidPdfTextReader
import com.pitakaview.app.importing.PdfTextReader
import com.pitakaview.app.lock.AndroidUnlocker
import com.pitakaview.app.lock.AppLock
import com.pitakaview.app.lock.Unlocker
import com.pitakaview.app.reminders.AndroidReminderNotifier
import com.pitakaview.app.reminders.ReminderAlarm
import com.pitakaview.app.reminders.ReminderNotifier
import com.pitakaview.app.reminders.Reminders
import com.pitakaview.app.reminders.WorkManagerReminderAlarm
import com.pitakaview.app.update.AndroidPackageReader
import com.pitakaview.app.update.AndroidUpdateInstaller
import com.pitakaview.app.update.AndroidUpdateNotifier
import com.pitakaview.app.update.GitHubReleaseSource
import com.pitakaview.app.update.PackageReader
import com.pitakaview.app.update.ReleaseSource
import com.pitakaview.app.update.UpdateInstaller
import com.pitakaview.app.update.UpdateNotifier
import com.pitakaview.app.update.UpdateSchedule
import com.pitakaview.app.update.Updates
import com.pitakaview.app.update.WorkManagerUpdateSchedule
import java.io.File
import java.time.Instant
import java.time.LocalDateTime

/** Holds app-wide singletons. Each is created lazily on first use, so app start does no extra work. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: FitakaDatabase by lazy { createFitakaDatabase(appContext) }
    val ledger: LedgerRepository by lazy { LedgerRepository(database) }
    val settings: SettingsRepository by lazy { SettingsRepository.create(appContext) }
    val backup: BackupManager by lazy {
        BackupManager(database, appContext.getDatabasePath(FitakaDatabase.FILE_NAME), AndroidSQLiteDriver(), appContext.cacheDir)
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

    /**
     * New versions from GitHub, and the five things they lean on. Properties, so a test can stand in for
     * GitHub, Android's installer and the phone's notifications; [updates] reads them each time it uses them.
     */
    var releaseSource: ReleaseSource = GitHubReleaseSource()
    var packageReader: PackageReader = AndroidPackageReader(appContext)
    var updateInstaller: UpdateInstaller = AndroidUpdateInstaller(appContext)
    var updateNotifier: UpdateNotifier = AndroidUpdateNotifier(appContext)
    var updateSchedule: UpdateSchedule = WorkManagerUpdateSchedule(appContext)
    var updateClock: () -> Instant = Instant::now
    val updates: Updates by lazy {
        Updates(
            settings,
            source = { releaseSource }, packages = { packageReader }, installer = { updateInstaller },
            notifier = { updateNotifier }, schedule = { updateSchedule },
            folder = File(appContext.cacheDir, "updates"), now = { updateClock() },
        )
    }

    /** Reads the words of a PDF statement (a property so tests can stand in for Android's reader). */
    var pdfTextReader: PdfTextReader = AndroidPdfTextReader(appContext)

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
