package com.pitakaview.app

import com.pitakaview.app.update.LatestRelease
import com.pitakaview.app.update.ReleaseAsset
import com.pitakaview.app.update.ReleaseSource
import com.pitakaview.app.update.UpdateSchedule
import java.io.File
import java.io.IOException

/**
 * The app as every Robolectric test runs it (robolectric.properties names it): the real app, with no internet
 * and no WorkManager for updates. A test never reaches GitHub; one that is about updates puts in its own
 * stand-ins for GitHub and the daily look.
 */
class TestFitakaApp : FitakaApp() {
    override fun onCreate() {
        super.onCreate()
        container.releaseSource = NoInternet
        container.updateSchedule = NoSchedule
    }

    /** GitHub, as a test sees it unless it says otherwise: out of reach. */
    object NoInternet : ReleaseSource {
        override suspend fun latest(): LatestRelease? = throw IOException("tests have no internet")
        override suspend fun text(asset: ReleaseAsset, maxBytes: Int): String = throw IOException("tests have no internet")
        override suspend fun download(asset: ReleaseAsset, to: File, progress: (Long) -> Unit): String = throw IOException("tests have no internet")
    }

    object NoSchedule : UpdateSchedule {
        override suspend fun ensure() = Unit
        override fun cancel() = Unit
    }
}
