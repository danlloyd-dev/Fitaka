package com.pitakaview.app.update

import android.content.Intent
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.pitakaview.app.data.prefs.SettingsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * The rules of updates, with stand-ins for GitHub, Android and the phone, and the real settings file: when the
 * app asks GitHub, what it remembers, what it tells, and every check a download must pass before Android gets it.
 */
@RunWith(RobolectricTestRunner::class)
class UpdatesTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val github = FakeReleaseSource()
    private val packages = FakePackages()
    private val installer = FakeInstaller()
    private val notifier = FakeNotifier()
    private val schedule = FakeSchedule()
    private var clock = Instant.parse("2026-11-28T01:00:00Z")
    private val folder by lazy { File(temp.root, "updates") }

    private val settings by lazy {
        SettingsRepository(PreferenceDataStoreFactory.create(produceFile = { File(temp.root, "settings.preferences_pb") }))
    }
    private val updates by lazy {
        Updates(settings, { github }, { packages }, { installer }, { notifier }, { schedule }, folder, now = { clock })
    }

    private val newApk = UpdateSamples.apkBytes()

    /** GitHub has 1.2.0 out, and the file downloads as a good 1.2.0 of this app, signed with the owner's key. */
    private fun goodRelease() {
        github.publish("1.2.0", newApk)
        packages.archive = UpdateSamples.identity(versionCode = 4, versionName = "1.2.0")
    }

    private fun waiting() = runBlocking { updates.waiting.first() }
    private fun latestAsks() = github.asked.count { it == "latest" }

    @Test
    fun openingTheAppAsksGithubAtMostOnceADay() = runBlocking {
        goodRelease()
        updates.onAppOpened()
        assertEquals(1, latestAsks())
        assertTrue("the daily look is lined up", schedule.lined)
        assertEquals("1.2.0", waiting()?.version.toString())

        clock += Duration.ofHours(23)
        repeat(3) { updates.onAppOpened() }
        assertEquals("not again within a day", 1, latestAsks())

        clock += Duration.ofHours(1)
        updates.onAppOpened()
        assertEquals("a day after the last answer", 2, latestAsks())
    }

    @Test
    fun withChecksOffNothingAsksGithubAndNothingShows() = runBlocking {
        goodRelease()
        updates.onAppOpened()
        assertTrue(waiting() != null)

        updates.setOn(false)
        assertFalse("the daily look is cancelled", schedule.lined)
        assertNull("the version found before is forgotten", waiting())
        assertTrue("its notification goes", notifier.cleared > 0)

        clock += Duration.ofDays(3)
        updates.onAppOpened()
        updates.backgroundRun()
        assertEquals("GitHub is not asked again", 1, latestAsks())
        assertNull(waiting())
    }

    @Test
    fun onlyANewerVersionWaits() = runBlocking {
        github.publish("1.1.0", newApk)
        updates.onAppOpened()
        assertNull("the same version is not an update", waiting())

        clock += Duration.ofDays(1)
        github.publish("1.0.9", newApk)
        updates.onAppOpened()
        assertNull("an older one is not either", waiting())

        clock += Duration.ofDays(1)
        github.publish("1.1.1", newApk)
        updates.onAppOpened()
        assertEquals("1.1.1", waiting()?.version.toString())

        // The owner removes the release from GitHub: the next answer takes it away again.
        clock += Duration.ofDays(1)
        github.release = null
        updates.onAppOpened()
        assertNull(waiting())
    }

    @Test
    fun whenGithubDoesNotAnswerTheAppTriesAgainAnHourLaterNotAtEveryOpening() = runBlocking {
        github.reachable = false
        updates.onAppOpened()
        clock += Duration.ofMinutes(59)
        updates.onAppOpened()
        assertEquals(1, latestAsks())
        assertNull("no answer is not remembered as one", settings.updateCheckedAt.first())

        clock += Duration.ofMinutes(1)
        github.reachable = true
        goodRelease()
        updates.onAppOpened()
        assertEquals(2, latestAsks())
        assertEquals(clock, settings.updateCheckedAt.first())
    }

    @Test
    fun theDailyCheckTellsThePhoneOncePerVersion() = runBlocking {
        goodRelease()
        updates.backgroundRun()
        updates.backgroundRun()
        assertEquals(listOf("out 1.2.0"), notifier.shown)

        github.publish("1.3.0", newApk)
        updates.backgroundRun()
        assertEquals(listOf("out 1.2.0", "out 1.3.0"), notifier.shown)
    }

    /** Notifications blocked when it was found: it is told once they are allowed, not lost. */
    @Test
    fun aVersionFoundWhileNotificationsAreBlockedIsToldLater() = runBlocking {
        goodRelease()
        notifier.can = false
        updates.backgroundRun()
        assertTrue(notifier.shown.isEmpty())
        notifier.can = true
        updates.backgroundRun()
        assertEquals(listOf("out 1.2.0"), notifier.shown)
    }

    @Test
    fun laterPutsTheBannerOffForTheRestOfTheDayAndTheDotStays() = runBlocking {
        goodRelease()
        updates.onAppOpened()
        val today = MutableStateFlow(LocalDate.of(2026, 11, 28))
        val release = updates.banner(today).first()!!
        updates.later(release, today.value)
        assertNull("no banner today", updates.banner(today).first())
        assertTrue("still waiting (the red dot)", waiting() != null)
        today.value = today.value.plusDays(1)
        assertEquals("back the next day", release, updates.banner(today).first())
    }

    @Test
    fun aGoodDownloadIsCheckedAndHandedToAndroidAndNothingIsLeftOnThePhone() = runBlocking {
        goodRelease()
        updates.onAppOpened()
        updates.update()
        assertEquals(listOf("latest", "text:SHA256SUMS.txt", "download:fitaka-1.2.0.apk"), github.asked)
        assertTrue("Android got exactly the bytes that were checked", installer.handedOver.single().contentEquals(newApk))
        assertEquals(UpdateStep.Installing, updates.step.value)
        assertEquals("kept for the new version to say it is done", "v1.2.0", settings.updateInstalling.first())
        assertFalse("the download is gone once Android has its copy", folder.exists() && folder.list()!!.isNotEmpty())
    }

    @Test
    fun withoutTheOwnersOkToInstallAppsNothingIsDownloaded() = runBlocking {
        goodRelease()
        installer.allowed = false
        updates.onAppOpened()
        updates.update()
        assertEquals(UpdateStep.NeedsPermission, updates.step.value)
        assertTrue(github.asked.none { it.startsWith("download") })

        // The owner allows it on the phone's page and comes back: it carries on by itself.
        installer.allowed = true
        updates.onReturned()
        waitUntil { installer.handedOver.isNotEmpty() }
    }

    @Test
    fun aDownloadThatDoesNotMatchItsChecksumIsNeverInstalled() = runBlocking {
        goodRelease()
        github.sums = UpdateSamples.sums("1.2.0", UpdateSamples.apkBytes(seed = 11))
        refused(UpdateText.SUMS_DISAGREE)

        // GitHub gives no checksum of its own, and the file is not the one in SHA256SUMS.txt.
        github.publish("1.2.0", newApk, githubChecksum = null)
        github.sums = UpdateSamples.sums("1.2.0", UpdateSamples.apkBytes(seed = 11))
        refused(UpdateText.DAMAGED)
    }

    @Test
    fun aReleaseWithoutItsChecksumFileIsNotDownloaded() = runBlocking {
        github.publish("1.2.0", newApk, withSums = false)
        refused(UpdateText.NO_SUMS)
        assertTrue(github.asked.none { it.startsWith("download") })

        github.publish("1.2.0", newApk)
        github.sums = "${sha256(newApk)}  some-other-file.apk\n"
        refused(UpdateText.NOT_IN_SUMS)
    }

    @Test
    fun onlyThisAppNewerAndSignedWithTheOwnersKeyIsInstalled() = runBlocking {
        goodRelease()
        packages.archive = null
        refused(UpdateText.NOT_AN_APP)
        packages.archive = UpdateSamples.identity(4, "1.2.0").copy(packageName = "com.example.other")
        refused(UpdateText.NOT_FITAKA)
        packages.archive = UpdateSamples.identity(3, "1.2.0")
        refused(UpdateText.NOT_NEWER)
        packages.archive = UpdateSamples.identity(4, "1.2.0").copy(signers = setOf("0".repeat(64)))
        refused(UpdateText.OTHER_KEY)
        packages.archive = UpdateSamples.identity(4, "1.2.0").copy(signers = emptySet())
        refused(UpdateText.OTHER_KEY)
    }

    @Test
    fun aDownloadThatBreaksOffSaysSoAndLeavesNothing() = runBlocking {
        goodRelease()
        github.breakDownload = true
        refused(UpdateText.DOWNLOAD_BROKE)
    }

    /** Update tapped twice before the first got anywhere: one download, one install. */
    @Test
    fun twoTapsOnUpdateInstallOnce() = runBlocking {
        goodRelease()
        updates.onAppOpened()
        val first = async { updates.update() }
        val second = async { updates.update() }
        first.await()
        second.await()
        assertEquals(1, github.asked.count { it.startsWith("download") })
        assertEquals(1, installer.handedOver.size)
    }

    @Test
    fun whenAndroidAsksAfterAllItsQuestionIsShownAndACancelSaysSo() = runBlocking {
        goodRelease()
        updates.onAppOpened()
        updates.update()
        val question = Intent("android.content.pm.action.CONFIRM_INSTALL")
        updates.onInstallerAsks(question)
        assertEquals(UpdateStep.Question(question), updates.step.value)
        updates.questionShown()
        assertEquals(UpdateStep.WithTheOwner, updates.step.value)
        updates.onInstallStopped(cancelled = true, why = null)
        assertEquals(UpdateStep.Stopped(UpdateText.CANCELLED), updates.step.value)
        waitUntil { runBlocking { settings.updateInstalling.first() } == null }
    }

    @Test
    fun theNewVersionSaysTheUpdateIsDoneOnlyAfterTheAppsOwnUpdate() = runBlocking {
        updates.afterAppReplaced()
        assertTrue("an install from elsewhere says nothing", notifier.shown.isEmpty())

        settings.setUpdateInstalling("v1.2.0")
        packages.installed = UpdateSamples.identity(4, "1.2.0")
        updates.afterAppReplaced()
        assertEquals(listOf("updated 1.2.0"), notifier.shown)
        assertNull(settings.updateInstalling.first())
    }

    @Test
    fun afterTheUpdateNothingOfItIsLeft() = runBlocking {
        goodRelease()
        updates.onAppOpened()
        // The new version is running: nothing is newer than it any more.
        packages.installed = UpdateSamples.identity(4, "1.2.0")
        val fresh = Updates(settings, { github }, { packages }, { installer }, { notifier }, { schedule }, folder, now = { clock })
        folder.mkdirs()
        File(folder, "fitaka-1.2.0.apk").writeBytes(newApk)
        fresh.onAppOpened()
        assertNull(fresh.waiting.first())
        assertFalse("the old download is gone", folder.exists())
        assertTrue("its notification is gone", notifier.cleared > 0)
    }

    /** Update tapped, and it refuses with [message]: nothing handed to Android, nothing left on the phone. */
    private suspend fun refused(message: String) {
        updates.onAppOpened()
        updates.update()
        assertEquals(UpdateStep.Stopped(message), updates.step.value)
        assertTrue("nothing went to Android", installer.handedOver.isEmpty())
        assertFalse("nothing is left on the phone", folder.exists())
        assertNull(settings.updateInstalling.first())
        clock += Duration.ofDays(1)
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }
}
