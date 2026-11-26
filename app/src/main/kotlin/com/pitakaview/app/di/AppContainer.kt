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
