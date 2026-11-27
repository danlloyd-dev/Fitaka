package com.pitakaview.app.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import com.pitakaview.app.FitakaApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Hands a checked APK to Android to install. An interface so tests can stand in for Android's installer. */
interface UpdateInstaller {
    /** Whether the owner has let this app install apps (the phone's "Install unknown apps", for this app). */
    fun allowed(): Boolean

    /** The phone's page where the owner allows it. */
    fun allowIntent(): Intent

    /** Gives [apk] to Android. Android's answers come to [UpdateInstallReceiver]; the first is a question for the owner. */
    suspend fun install(apk: File)
}

/**
 * Android's own installer, through a PackageInstaller session, asked to install without a second question
 * (the owner's choice, 2026-11-27). Android allows that, from Android 12, when the app updates itself, holds
 * UPDATE_PACKAGES_WITHOUT_USER_ACTION, has the owner's OK to install apps, and targets a recent enough Android
 * (developer.android.com, SessionParams.setRequireUserAction). Where any of that is missing, Android asks
 * instead ([UpdateInstallReceiver] gets STATUS_PENDING_USER_ACTION) and the app puts the question on screen.
 * Android refuses an APK signed with another key or older than the one installed, whatever this app does.
 */
class AndroidUpdateInstaller(private val context: Context) : UpdateInstaller {
    override fun allowed(): Boolean = context.packageManager.canRequestPackageInstalls()

    override fun allowIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())

    override suspend fun install(apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val id = installer.createSession(updateSession(context.packageName, apk.length()))
        try {
            installer.openSession(id).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("fitaka.apk", 0, apk.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                // Mutable: Android writes its answer into it. From target SDK 35 a fixed one is refused here.
                // Safe, as it names this app's own receiver, which nothing outside the app can reach.
                val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                val answer = PendingIntent.getBroadcast(
                    context, id, Intent(context, UpdateInstallReceiver::class.java).setAction(ACTION_ANSWER),
                    PendingIntent.FLAG_UPDATE_CURRENT or mutable,
                )
                session.commit(answer.intentSender)
            }
        } catch (e: Exception) {
            installer.abandonSession(id)
            throw e
        }
    }

    companion object {
        const val ACTION_ANSWER = "com.pitakaview.app.update.INSTALL_ANSWER"
    }
}

/** The session for an update of [packageName] by itself, asking Android for no second question where it allows that. */
internal fun updateSession(packageName: String, size: Long) =
    PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
        setAppPackageName(packageName)
        setSize(size)
        setInstallReason(PackageManager.INSTALL_REASON_USER)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
    }

/**
 * Android's answers about an update the app handed it. Not exported: only Android, through the app's own
 * session, can send here. When Android wants to ask the owner after all, the answer carries its question,
 * which the app shows from its own screen (the phone may hold back a start from the background).
 */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val updates = (context.applicationContext as FitakaApp).container.updates
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION ->
                updates.onInstallerAsks(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java))
            PackageInstaller.STATUS_SUCCESS -> updates.onInstalled()
            else -> updates.onInstallStopped(cancelled = status == PackageInstaller.STATUS_FAILURE_ABORTED, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
        }
    }
}

/**
 * Android installed a new version of this app over the old one (by the app's own update, or any other way).
 * Installing closes the app, so this is where the work of opening it is done instead: the reminder check is
 * lined up again, and after the app's own update the owner is told it is done.
 */
class AppUpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val container = (context.applicationContext as FitakaApp).container
        // Null when a test calls this directly rather than through the phone.
        val pending: PendingResult? = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                container.reminders.afterUpdate()
                container.updates.afterAppReplaced()
            } catch (e: Exception) {
                Log.e("FitakaUpdates", "After the update", e)
            } finally {
                pending?.finish()
            }
        }
    }
}
