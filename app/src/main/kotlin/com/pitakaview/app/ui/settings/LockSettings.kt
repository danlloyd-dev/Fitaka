package com.pitakaview.app.ui.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pitakaview.app.di.LocalAppContainer
import com.pitakaview.app.lock.LockSupport
import com.pitakaview.app.lock.UnlockResult
import com.pitakaview.app.ui.components.FitakaCard
import com.pitakaview.app.ui.components.FitakaToggleRow
import kotlinx.coroutines.launch

/** What the App lock card in Settings shows. The default is "not on this phone", which hides the card. */
data class LockSettingsUi(val on: Boolean = false, val support: LockSupport = LockSupport.TOO_OLD)

object LockSettingsText {
    const val TITLE = "Lock the app"
    const val SUBTITLE = "Ask for your fingerprint, face or phone PIN when the app opens"
    const val NO_SCREEN_LOCK = "Set a screen lock on this phone first (a PIN, pattern or fingerprint)."
    const val TURN_ON = "Turn on the app lock"
    const val TURN_OFF = "Turn off the app lock"

    /** The switch can always be turned OFF, so a lock left on can never be stuck on. */
    fun canTap(ui: LockSettingsUi): Boolean = ui.on || ui.support == LockSupport.READY
}

@Composable
fun LockCard(ui: LockSettingsUi, onChange: (Boolean) -> Unit) {
    FitakaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        FitakaToggleRow(
            title = LockSettingsText.TITLE,
            subtitle = if (ui.support == LockSupport.NO_SCREEN_LOCK) LockSettingsText.NO_SCREEN_LOCK else LockSettingsText.SUBTITLE,
            checked = ui.on,
            onCheckedChange = onChange,
            enabled = LockSettingsText.canTap(ui),
        )
    }
}

/** The card's state and what its switch does. */
class LockSettings(val ui: LockSettingsUi, val onChange: (Boolean) -> Unit)

/**
 * Wires the App lock card to the app. Switching the lock on or off first asks the phone to check who
 * is holding it: on, so the owner knows it works before relying on it; off, so nobody else can remove it.
 */
@Composable
fun rememberLockSettings(): LockSettings {
    val container = LocalAppContainer.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val on by container.settings.lockOn.collectAsStateWithLifecycle(initialValue = false)
    var support by remember { mutableStateOf(container.unlocker.support()) }
    LifecycleResumeEffect(Unit) {
        // Coming back from the phone's settings: a screen lock may have been set up or removed there.
        support = container.unlocker.support()
        onPauseOrDispose { }
    }
    return LockSettings(
        ui = LockSettingsUi(on = on, support = support),
        onChange = { wanted ->
            val host = activity
            when {
                host == null -> Unit
                // The phone has nothing to ask for any more: let the owner switch the lock off without it.
                !wanted && container.unlocker.support() != LockSupport.READY -> scope.launch { container.appLock.setOn(false) }
                else -> container.unlocker.ask(host, if (wanted) LockSettingsText.TURN_ON else LockSettingsText.TURN_OFF) { result ->
                    if (result == UnlockResult.UNLOCKED) scope.launch { container.appLock.setOn(wanted) }
                }
            }
        },
    )
}
