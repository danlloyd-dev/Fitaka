package com.pitakaview.app

import android.app.Application
import androidx.work.Configuration
import com.pitakaview.app.data.DefaultData
import com.pitakaview.app.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant

class FitakaApp : Application(), Configuration.Provider {
    /** App-wide dependencies, built by hand instead of a DI framework to keep the app light. */
    lateinit var container: AppContainer
        private set

    /** Work that should finish even if the screen closes (e.g. first-run setup). */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * WorkManager (the daily reminder check) reads this the first time it is used, or when the system
     * starts the app to run that check. Until then it is not started at all.
     */
    override val workManagerConfiguration: Configuration get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Starter categories and a Cash wallet on first launch; a no-op afterwards (two COUNT queries).
        appScope.launch { DefaultData.seedIfEmpty(container.database, Instant.now()) }
    }
}
