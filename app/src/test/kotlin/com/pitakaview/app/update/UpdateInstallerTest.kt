package com.pitakaview.app.update

import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.pitakaview.app.FitakaApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The real hand-over to Android's installer (Robolectric's stand-in for it): a session for this app is made
 * and committed, asking for no second question, and Android's answers reach the app's receiver and the rules.
 */
@RunWith(RobolectricTestRunner::class)
class UpdateInstallerTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val app: FitakaApp get() = ApplicationProvider.getApplicationContext()
    private val android get() = app.packageManager.packageInstaller

    private fun handOver(): PackageInstaller.SessionInfo = runBlocking {
        val apk = temp.newFile("fitaka.apk").apply { writeBytes(UpdateSamples.apkBytes()) }
        AndroidUpdateInstaller(app).install(apk)
        android.allSessions.single()
    }

    @Test
    fun aSessionForThisAppIsMadeAndCommitted() {
        val session = handOver()
        assertEquals(app.packageName, session.appPackageName)
    }

    /**
     * The session asks for no second question. Read from the session's settings themselves: Robolectric's
     * stand-in does not copy this one into the session's info (it reads 0 there whatever is asked).
     */
    @Test
    fun theSessionAsksForNoSecondQuestion() {
        val params = updateSession(app.packageName, 1000)
        val asked = PackageInstaller.SessionParams::class.java.getField("requireUserAction").getInt(params)
        assertEquals(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED, asked)
    }

    /**
     * Android's answer goes to the receiver the session names. Robolectric's stand-in answers every commit at
     * once and without the status a real phone always sends, so here the answer reads as "not installed"; which
     * answer is read as what is held by the test below, with each real status.
     */
    @Test
    fun androidsAnswerReachesTheAppsReceiver() {
        assertEquals(UpdateStep.Idle, app.container.updates.step.value)
        handOver()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(UpdateStep.Stopped(UpdateText.installFailed(null)), app.container.updates.step.value)
    }

    @Test
    fun androidsQuestionAndItsRefusalsAreReadRightly() {
        val receiver = UpdateInstallReceiver()
        val question = Intent("android.content.pm.action.CONFIRM_INSTALL")
        receiver.onReceive(app, answer(PackageInstaller.STATUS_PENDING_USER_ACTION).putExtra(Intent.EXTRA_INTENT, question))
        assertEquals(question.action, (app.container.updates.step.value as UpdateStep.Question).ask.action)

        receiver.onReceive(app, answer(PackageInstaller.STATUS_FAILURE_ABORTED))
        assertEquals(UpdateStep.Stopped(UpdateText.CANCELLED), app.container.updates.step.value)

        receiver.onReceive(app, answer(PackageInstaller.STATUS_FAILURE_STORAGE).putExtra(PackageInstaller.EXTRA_STATUS_MESSAGE, "not enough space"))
        assertEquals(UpdateStep.Stopped(UpdateText.installFailed("not enough space")), app.container.updates.step.value)
    }

    /** The new version starts: the reminder check is made sure of, and after the app's own update the owner hears it is done. */
    @Test
    fun theNewVersionLinesUpTheRemindersAndSaysTheUpdateIsDone() = runBlocking {
        val notifier = FakeNotifier()
        app.container.updateNotifier = notifier
        app.container.packageReader = FakePackages(UpdateSamples.identity(4, "1.2.0"))
        var linedUp = 0
        app.container.reminderAlarm = object : com.pitakaview.app.reminders.ReminderAlarm {
            override suspend fun ensureScheduled(delay: java.time.Duration) {
                linedUp++
            }
            override suspend fun scheduleAfterThisRun(delay: java.time.Duration) = Unit
            override fun cancel() = Unit
        }
        app.container.settings.setRemindersOn(true)
        app.container.settings.setUpdateInstalling("v1.2.0")

        app.sendBroadcast(Intent(Intent.ACTION_MY_PACKAGE_REPLACED).setClass(app, AppUpdatedReceiver::class.java))
        shadowOf(Looper.getMainLooper()).idle()
        val deadline = System.currentTimeMillis() + 5_000
        while (notifier.shown.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(10)

        assertEquals(1, linedUp)
        assertEquals(listOf("updated 1.2.0"), notifier.shown)
        assertEquals(null, app.container.settings.updateInstalling.first())
    }

    private fun answer(status: Int) = Intent(AndroidUpdateInstaller.ACTION_ANSWER).putExtra(PackageInstaller.EXTRA_STATUS, status)
}
