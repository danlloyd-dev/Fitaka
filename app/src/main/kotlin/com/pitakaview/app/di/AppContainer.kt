package com.pitakaview.app.di

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
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

    /** The one source of "today" for every screen. */
    val dayClock = DayClock()
}

/** The container, for screens that build their own ViewModels. */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
