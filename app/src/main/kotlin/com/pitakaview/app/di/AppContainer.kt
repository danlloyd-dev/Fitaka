package com.pitakaview.app.di

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.pitakaview.app.data.db.PitakaDatabase
import com.pitakaview.app.data.db.createPitakaDatabase
import com.pitakaview.app.data.prefs.SettingsRepository
import com.pitakaview.app.data.repo.LedgerRepository

/** Holds app-wide singletons. Each is created lazily on first use, so app start does no extra work. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: PitakaDatabase by lazy { createPitakaDatabase(appContext) }
    val ledger: LedgerRepository by lazy { LedgerRepository(database) }
    val settings: SettingsRepository by lazy { SettingsRepository.create(appContext) }
}

/** The container, for screens that build their own ViewModels. */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
