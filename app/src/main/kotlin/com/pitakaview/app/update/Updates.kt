package com.pitakaview.app.update

import android.content.Intent
import com.pitakaview.app.APP_NAME
import com.pitakaview.app.data.prefs.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

/** Where an update the owner asked for has got to. */
sealed interface UpdateStep {
    data object Idle : UpdateStep
    data object Checking : UpdateStep
    data class Downloading(val percent: Int) : UpdateStep
    data object Verifying : UpdateStep

    /** The owner has not yet let the app install apps. The phone's page for that is one tap away. */
    data object NeedsPermission : UpdateStep

    /** Handed to Android, which installs it without asking; installing closes the app. */
    data object Installing : UpdateStep

    /** Android wants to ask the owner after all: its "Do you want to update this app?", for the app to show from its own screen. */
    data class Question(val ask: Intent) : UpdateStep

    /** That question is with the owner (or Android is still getting it ready). */
    data object WithTheOwner : UpdateStep

    /** It stopped: cancelled, refused, or broken off. [message] says which and what is true now. */
    data class Stopped(val message: String) : UpdateStep
}

/** Something about the download that means it must not be installed. */
class UpdateProblem(override val message: String) : Exception(message)

/** Everything the update parts say, in one place so tests can hold them. */
object UpdateText {
    const val CHECKS_TITLE = "Check for updates"
    const val CHECKS_SUBTITLE = "Once a day, $APP_NAME asks GitHub whether a new version is out. Your entries never leave this phone."
    const val CHECK_NOW = "Check now"
    const val LATER = "Later"
    const val UPDATE = "Update"
    const val ALLOW = "Allow installing"
    const val ALLOW_NOTIFICATIONS = "Allow notifications"
    const val TRY_AGAIN = "Try again"

    fun out(version: AppVersion) = "$APP_NAME $version is out"
    fun updateTo(version: AppVersion) = "Update to $version"
    fun bannerBody(bytes: Long) = "A new version is ready to download (${megabytes(bytes)}). Your entries stay as they are."
    fun downloading(percent: Int) = "Downloading… $percent%"

    const val UP_TO_DATE = "You have the newest version."
    const val CHECKING = "Asking GitHub…"
    const val VERIFYING = "Checking the download…"
    const val INSTALLING = "Installing… $APP_NAME closes for a moment while it updates. A notification says when it is done."
    const val WITH_THE_OWNER = "Tap Update on the phone's question to finish."
    const val NEEDS_PERMISSION = "To install it, the phone needs your OK for $APP_NAME to install apps. You give it once."
    const val NOTIFICATIONS_OFF = "Notifications are off for $APP_NAME, so a new version shows only here and on Home."
    const val OFFLINE = "GitHub could not be reached. Check the internet connection, then try again."

    private const val NOT_INSTALLED = "Nothing was installed."
    const val DOWNLOAD_BROKE = "The download broke off. $NOT_INSTALLED"
    const val NO_SUMS = "This release has no ${ReleaseJson.SUMS_FILE} to check the download against. $NOT_INSTALLED"
    const val NOT_IN_SUMS = "The APK is not in the release's ${ReleaseJson.SUMS_FILE}. $NOT_INSTALLED"
    const val SUMS_DISAGREE = "GitHub's checksum and the release's ${ReleaseJson.SUMS_FILE} do not agree. $NOT_INSTALLED"
    const val DAMAGED = "The download does not match its checksum. $NOT_INSTALLED"
    const val NOT_AN_APP = "The download is not an app the phone can read. $NOT_INSTALLED"
    const val NOT_FITAKA = "The download is a different app. $NOT_INSTALLED"
    const val NOT_NEWER = "The download is not newer than this version. $NOT_INSTALLED"
    const val OTHER_KEY = "The download is not signed with $APP_NAME's key. $NOT_INSTALLED"
    const val CANCELLED = "Update cancelled. $NOT_INSTALLED"
    fun installFailed(why: String?) = "The phone did not install it${why?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()}. Your entries are as they were."

    /** 2,864,829 bytes is "2.9 MB" (in thousands, as the phone's own file sizes are). */
    fun megabytes(bytes: Long) = String.format(Locale.ENGLISH, "%.1f MB", bytes / 1_000_000.0)
}

/**
 * New versions from GitHub (the owner's choices, 2026-11-27): looked for when the app opens, at most once a
 * day, and by a daily check in the background, which tells the phone's notifications once per version; a
 * switch in Settings turns all of it off. Found versions show as a banner on Home, a red dot on the Settings
 * button and a card in Settings. Update downloads the APK, checks it against GitHub's checksum and the
 * release's SHA256SUMS.txt, checks that it is this app, newer and signed with the same key, and hands it to
 * Android to install without a second question (Android asks anyway where it must).
 *
 * What it costs: one request of a few KB a day (the background check) plus one when the app opens a day
 * after the last answer; the download only when the owner taps Update. Nothing about the owner is sent.
 */
class Updates(
    private val settings: SettingsRepository,
    private val source: () -> ReleaseSource,
    private val packages: () -> PackageReader,
    private val installer: () -> UpdateInstaller,
    private val notifier: () -> UpdateNotifier,
    private val schedule: () -> UpdateSchedule,
    /** Where a download is kept until it has been handed to Android. */
    private val folder: File,
    private val now: () -> Instant = Instant::now,
) {
    // Downloads and the owner's checks run here, so leaving the screen does not stop them.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val looking = Mutex()
    private val updating = Mutex()

    private val _step = MutableStateFlow<UpdateStep>(UpdateStep.Idle)
    val step: StateFlow<UpdateStep> = _step.asStateFlow()

    /** What the last check the owner asked for found out ("You have the newest version."), until the next one. */
    private val _note = MutableStateFlow<String?>(null)
    val note: StateFlow<String?> = _note.asStateFlow()

    /** When GitHub last failed to answer, in this process: the app does not ask again on every opening. */
    @Volatile private var lastFailure: Instant? = null

    /** This app's version. Read once: it cannot change while this process lives (an update ends it). */
    val installedVersion: AppVersion? by lazy { AppVersion.parse(packages().installed().versionName) }

    val on: Flow<Boolean> get() = settings.updateChecksOn
    val lastCheckedAt: Flow<Instant?> get() = settings.updateCheckedAt

    /** A version newer than this one, found by a look and not yet installed; null when there is none. */
    val waiting: Flow<LatestRelease?> = settings.updateFound
        .map { text -> text?.let(ReleaseJson::fromStored)?.takeIf(::isNewer) }
        .distinctUntilChanged()

    /** What Home's banner shows: the waiting version, unless the owner put it off on [today]. */
    fun banner(today: Flow<LocalDate>): Flow<LatestRelease?> =
        combine(waiting, settings.updateLater, today) { release, later, day -> release?.takeIf { later != laterMark(it, day) } }
            .distinctUntilChanged()

    // A version that cannot be read is never older than anything: no update rather than a wrong one.
    private fun isNewer(release: LatestRelease) = installedVersion?.let { release.version > it } ?: false

    private fun laterMark(release: LatestRelease, day: LocalDate) = "${release.tag} $day"

    /** Whether this process has made sure of the daily look (WorkManager keeps it from then on, across restarts). */
    @Volatile private var lined = false

    /** The app came to the front. With checks on: keep the daily check lined up, tidy up after an update, and look if a day has passed. */
    suspend fun onAppOpened() {
        if (!settings.updateChecksOn.first()) return
        // Once per start of the app, not at every return to it: each is a write to WorkManager's database.
        if (!lined) {
            schedule().ensure()
            lined = true
        }
        if (waiting.first() == null && _step.value.isQuiet()) {
            // Installed (or gone from GitHub): nothing of it should be left on the phone.
            notifier().clear()
            folder.deleteRecursively()
        }
        if (isDue()) look()
    }

    private suspend fun isDue(): Boolean {
        val current = now()
        val last = settings.updateCheckedAt.first()
        val failed = lastFailure
        return (last == null || Duration.between(last, current) >= DAY) && (failed == null || Duration.between(failed, current) >= RETRY_AFTER)
    }

    /** The daily check in the background: look, and tell the phone's notifications about a version once. */
    suspend fun backgroundRun() {
        if (!settings.updateChecksOn.first() || !look()) return
        val release = waiting.first() ?: return
        if (settings.updateNotified.first() == release.tag || !notifier().canNotify()) return
        notifier().show(release.version)
        settings.setUpdateNotified(release.tag)
    }

    /** Asks GitHub for the latest release and keeps the answer. False when no answer came. */
    private suspend fun look(): Boolean = looking.withLock {
        val latest = try {
            source().latest()
        } catch (e: IOException) {
            lastFailure = now()
            return@withLock false
        }
        lastFailure = null
        settings.recordUpdateCheck(now(), latest?.takeIf(::isNewer)?.let(ReleaseJson::toStored))
        true
    }

    /** The owner tapped "Check for updates" in Settings. Works with the switch off too: they asked. */
    fun checkNow() {
        if (!_step.value.isQuiet()) return
        scope.launch {
            _note.value = null
            _step.value = UpdateStep.Checking
            val answered = look()
            _step.value = UpdateStep.Idle
            _note.value = when {
                !answered -> UpdateText.OFFLINE
                waiting.first() == null -> UpdateText.UP_TO_DATE
                else -> null
            }
        }
    }

    suspend fun setOn(on: Boolean) {
        settings.setUpdateChecksOn(on)
        _note.value = null
        if (on) {
            schedule().ensure()
            lined = true
            look()
        } else {
            schedule().cancel()
            lined = false
            notifier().clear()
        }
    }

    /** Home's banner, put off for the rest of [today]. The red dot stays. */
    suspend fun later(release: LatestRelease, today: LocalDate) = settings.setUpdateLater(laterMark(release, today))

    /** The owner tapped Update. A second tap while one is under way does nothing. */
    fun startUpdate() {
        scope.launch { update() }
    }

    /** Coming back to the app from the phone's page for installing apps: carry on if the owner said yes there. */
    fun onReturned() {
        if (_step.value == UpdateStep.NeedsPermission && installer().allowed()) startUpdate()
    }

    suspend fun update() {
        if (!updating.tryLock()) return
        try {
            val release = waiting.first() ?: return
            if (!installer().allowed()) {
                _step.value = UpdateStep.NeedsPermission
                return
            }
            _note.value = null
            _step.value = UpdateStep.Downloading(0)
            try {
                val apk = downloadAndCheck(release)
                _step.value = UpdateStep.Installing
                // Kept, as installing ends this process: the new version reads it and says the update is done.
                settings.setUpdateInstalling(release.tag)
                installer().install(apk)
                // Android has its own copy now; Android's answers come to onInstallerAsks / onInstallStopped.
                folder.deleteRecursively()
            } catch (e: UpdateProblem) {
                stop(e.message)
            } catch (e: IOException) {
                stop(UpdateText.DOWNLOAD_BROKE)
            } catch (e: RuntimeException) {
                // Android's installer refusing the session (no space, a phone policy, ...).
                stop(UpdateText.installFailed(e.message))
            }
        } finally {
            updating.unlock()
        }
    }

    private suspend fun stop(message: String) {
        folder.deleteRecursively()
        settings.setUpdateInstalling(null)
        _step.value = UpdateStep.Stopped(message)
    }

    /** Downloads the release's APK and returns it once it has passed every check; throws [UpdateProblem] when it fails one. */
    private suspend fun downloadAndCheck(release: LatestRelease): File {
        val sums = release.sums ?: throw UpdateProblem(UpdateText.NO_SUMS)
        val expected = Sha256Sums.parse(source().text(sums, MAX_SUMS))[release.apk.name] ?: throw UpdateProblem(UpdateText.NOT_IN_SUMS)
        if (release.apk.sha256 != null && release.apk.sha256 != expected) throw UpdateProblem(UpdateText.SUMS_DISAGREE)

        folder.deleteRecursively()
        folder.mkdirs()
        val apk = File(folder, "fitaka-${release.version}.apk")
        val size = release.apk.size.coerceAtLeast(1)
        val got = source().download(release.apk, apk) { bytes -> _step.value = UpdateStep.Downloading((bytes * 100 / size).toInt().coerceIn(0, 100)) }

        _step.value = UpdateStep.Verifying
        if (got != expected) throw UpdateProblem(UpdateText.DAMAGED)
        val inside = packages().archive(apk) ?: throw UpdateProblem(UpdateText.NOT_AN_APP)
        val here = packages().installed()
        when {
            inside.packageName != here.packageName -> throw UpdateProblem(UpdateText.NOT_FITAKA)
            inside.versionCode <= here.versionCode -> throw UpdateProblem(UpdateText.NOT_NEWER)
            // Every key the same: Android would refuse it anyway, but only after asking the owner.
            inside.signers.isEmpty() || inside.signers != here.signers -> throw UpdateProblem(UpdateText.OTHER_KEY)
        }
        return apk
    }

    /** Android wants to ask the owner after all, and has its question ready. */
    fun onInstallerAsks(ask: Intent?) {
        if (ask != null) _step.value = UpdateStep.Question(ask) else onInstallStopped(cancelled = false, why = null)
    }

    /** The app has put Android's question on screen. */
    fun questionShown() {
        if (_step.value is UpdateStep.Question) _step.value = UpdateStep.WithTheOwner
    }

    fun onInstallStopped(cancelled: Boolean, why: String?) {
        _step.value = UpdateStep.Stopped(if (cancelled) UpdateText.CANCELLED else UpdateText.installFailed(why))
        scope.launch { settings.setUpdateInstalling(null) }
    }

    /**
     * This is the new version, just installed over the old one. After the app's own update, the owner is told
     * it is done (installing closed the app under their finger); after any other install, nothing is said.
     */
    suspend fun afterAppReplaced() {
        if (settings.updateInstalling.first() == null) return
        settings.setUpdateInstalling(null)
        if (notifier().canNotify()) notifier().showUpdated(packages().installed().versionName)
    }

    /** Installed. Android usually ends this process first, so this is seen only where it does not. */
    fun onInstalled() {
        _step.value = UpdateStep.Idle
    }

    private fun UpdateStep.isQuiet() = this == UpdateStep.Idle || this is UpdateStep.Stopped

    companion object {
        private val DAY: Duration = Duration.ofHours(24)

        /** After GitHub did not answer, the next opening of the app asks again only this much later. */
        private val RETRY_AFTER: Duration = Duration.ofHours(1)

        /** A release's checksums are two lines; this is far more than any such file. */
        private const val MAX_SUMS = 64 * 1024
    }
}
