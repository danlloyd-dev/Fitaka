package com.pitakaview.app.lock

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.annotation.RequiresApi
import com.pitakaview.app.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

object AppLockRules {
    /**
     * Away for less than this, the app does not lock again: long enough to pick a backup file or answer a
     * message, short enough that a phone left on a table is locked when someone else picks it up.
     */
    const val AWAY_MS = 60_000L

    fun locksOnReturn(lockOn: Boolean, leftAtMs: Long?, nowMs: Long): Boolean =
        lockOn && leftAtMs != null && nowMs - leftAtMs >= AWAY_MS
}

/**
 * Whether the app is showing its lock screen. One per process, so a rotation does not unlock or relock
 * anything, and a process the system ended starts locked again.
 *
 * It only keeps the state; [Unlocker] is what asks the phone to check who is holding it.
 */
class AppLock(
    private val settings: SettingsRepository,
    /** Time that keeps counting while the phone sleeps and cannot be changed by setting the clock. */
    private val elapsedMs: () -> Long = SystemClock::elapsedRealtime,
) {
    private val _locked = MutableStateFlow<Boolean?>(null)

    /** Null until the saved choice has been read; the app draws nothing until then, so nothing shows through. */
    val locked: StateFlow<Boolean?> = _locked.asStateFlow()

    private var lockOn = false
    private var leftAtMs: Long? = null

    /** Once per process: with the lock on, the app starts locked. */
    suspend fun load() {
        if (_locked.value != null) return
        lockOn = settings.lockOn.first()
        _locked.value = lockOn
    }

    /** The app went out of sight. */
    fun onLeft() {
        leftAtMs = elapsedMs()
    }

    /** The app is in sight again. */
    fun onReturned() {
        val left = leftAtMs
        leftAtMs = null
        if (AppLockRules.locksOnReturn(lockOn, left, elapsedMs())) _locked.value = true
    }

    fun onUnlocked() {
        _locked.value = false
    }

    /** Called only after the phone has confirmed who is asking. Switching on does not lock at once: they just proved it is them. */
    suspend fun setOn(on: Boolean) {
        settings.setLockOn(on)
        lockOn = on
        if (!on) _locked.value = false
    }
}

/** Whether this phone can lock the app at all. */
enum class LockSupport {
    READY,

    /** The phone has no PIN, pattern, password or fingerprint, so there is nothing to ask for. */
    NO_SCREEN_LOCK,

    /** Before Android 10 the phone's unlock question has no PIN to fall back on; a bad sensor would lock the owner out. */
    TOO_OLD,
}

enum class UnlockResult { UNLOCKED, NOT_UNLOCKED }

/** Asks the phone to check who is holding it. An interface so the screens around it can be tested with a stand-in. */
interface Unlocker {
    fun support(): LockSupport

    /** Shows the phone's own unlock question over [activity] and reports once how it ended. */
    fun ask(activity: Activity, title: String, onResult: (UnlockResult) -> Unit)
}

/**
 * Android's built-in unlock question: fingerprint or face, with the phone's PIN, pattern or password as
 * the other way in. The app never sees a fingerprint or a PIN; it is only told yes or no.
 */
class AndroidUnlocker(private val context: Context) : Unlocker {
    override fun support(): LockSupport = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> LockSupport.TOO_OLD
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure != true -> LockSupport.NO_SCREEN_LOCK
        else -> LockSupport.READY
    }

    override fun ask(activity: Activity, title: String, onResult: (UnlockResult) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            onResult(UnlockResult.NOT_UNLOCKED)
            return
        }
        show(activity, title, onResult)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun show(activity: Activity, title: String, onResult: (UnlockResult) -> Unit) {
        val builder = BiometricPrompt.Builder(activity).setTitle(title)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        } else {
            @Suppress("DEPRECATION") // The only way to offer the PIN on Android 10.
            builder.setDeviceCredentialAllowed(true)
        }
        builder.build().authenticate(
            CancellationSignal(),
            activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(UnlockResult.UNLOCKED)

                // Closed, cancelled, too many tries, or nothing to check against: all leave the app locked.
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(UnlockResult.NOT_UNLOCKED)

                // onAuthenticationFailed (one finger not recognised) is not an ending: the question stays up.
            },
        )
    }
}
