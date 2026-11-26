package com.pitakaview.app

import android.app.Application
import com.pitakaview.app.data.DefaultData
import com.pitakaview.app.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant

class PitakaApp : Application() {
    /** App-wide dependencies, built by hand instead of a DI framework to keep the app light. */
    lateinit var container: AppContainer
        private set

    /** Work that should finish even if the screen closes (e.g. first-run setup). */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Starter categories and a Cash wallet on first launch; a no-op afterwards (two COUNT queries).
        appScope.launch { DefaultData.seedIfEmpty(container.database, Instant.now()) }
    }
}
