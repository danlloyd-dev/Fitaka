package com.pitakaview.app.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pitakaview.app.APP_NAME
import com.pitakaview.app.data.prefs.ReminderCheck
import com.pitakaview.app.di.LocalAppContainer
import com.pitakaview.app.ui.components.FitakaButton
import com.pitakaview.app.ui.components.FitakaCard
import com.pitakaview.app.ui.components.FitakaToggleRow
import com.pitakaview.app.ui.components.ProblemText
import com.pitakaview.app.ui.theme.FitakaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Shown under a "Remind me" switch on a payday or a loan while reminders as a whole are off. */
const val REMINDERS_OFF_HINT = "Reminders are off. Turn them on in Settings."

/** What the Reminders card in Settings shows. */
data class ReminderSettingsUi(
    val on: Boolean = false,
    /** The phone is not letting the app notify: asked just now and refused, or switched off in the phone's settings. */
    val blocked: Boolean = false,
    /** When reminders were last looked for, in words; null before the first time. */
    val lastCheck: String? = null,
    /** Whether this is a phone known to hold background work back unless told otherwise. */
    val phoneTip: Boolean = false,
)

object ReminderSettingsText {
    private val TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val DAY = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

    const val SUBTITLE = "Paydays and payments due, from 8 in the morning"
    const val BLOCKED = "This phone is not letting $APP_NAME show notifications. Allow them in the phone's settings."
    const val PHONE_TIP =
        "If reminders do not arrive: in the phone's settings for $APP_NAME, turn on Autostart and set Battery saver to No restrictions."

    /** "Last checked today at 8:02 AM, in the background." Says how, because that tells whether the phone lets the app run by itself. */
    fun lastCheck(check: ReminderCheck, today: LocalDate, zone: ZoneId): String {
        val at = check.at.atZone(zone)
        val day = when (at.toLocalDate()) {
            today -> "today"
            today.minusDays(1) -> "yesterday"
            else -> "on ${DAY.format(at)}"
        }
        val how = if (check.inBackground) "in the background" else "with the app open"
        return "Last checked $day at ${TIME.format(at)}, $how."
    }

    /** Xiaomi, Redmi and POCO phones stop background work unless Autostart is on (see dontkillmyapp.com/xiaomi). */
    fun needsPhoneTip(manufacturer: String): Boolean = manufacturer.equals("xiaomi", ignoreCase = true)
}

@Composable
fun RemindersCard(ui: ReminderSettingsUi, onChange: (Boolean) -> Unit, onOpenPhoneSettings: () -> Unit) {
    val c = FitakaTheme.colors
    val type = MaterialTheme.typography
    FitakaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        FitakaToggleRow(title = "Remind me", subtitle = ReminderSettingsText.SUBTITLE, checked = ui.on, onCheckedChange = onChange)
        if (ui.blocked) ProblemText(ReminderSettingsText.BLOCKED)
        if (ui.on && ui.lastCheck != null) Text(ui.lastCheck, style = type.bodySmall, color = c.inkMuted)
        val tip = ui.on && ui.phoneTip
        if (tip) {
            Spacer(Modifier.height(4.dp))
            Text(ReminderSettingsText.PHONE_TIP, style = type.bodySmall, color = c.inkMuted)
        }
        if (ui.blocked || tip) {
            Spacer(Modifier.height(12.dp))
            FitakaButton("Open phone settings", onClick = onOpenPhoneSettings, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** What a tap on the Reminders switch, or the phone's answer to "allow notifications?", leads to. */
enum class ReminderSwitchStep { TURN_OFF, ASK_THE_PHONE, TURN_ON, SAY_BLOCKED }

object ReminderSwitch {
    /**
     * [mustAsk]: the phone has not been asked yet (Android 13 and later ask once per app).
     * [phoneAllows]: the phone is showing this app's notifications right now.
     */
    fun onTap(wanted: Boolean, mustAsk: Boolean, phoneAllows: Boolean): ReminderSwitchStep = when {
        !wanted -> ReminderSwitchStep.TURN_OFF
        mustAsk -> ReminderSwitchStep.ASK_THE_PHONE
        phoneAllows -> ReminderSwitchStep.TURN_ON
        // Allowed once, then switched off in the phone's settings: only the owner can undo that, there.
        else -> ReminderSwitchStep.SAY_BLOCKED
    }

    /** Reminders that could never be shown are not switched on. */
    fun onAnswer(granted: Boolean): ReminderSwitchStep = if (granted) ReminderSwitchStep.TURN_ON else ReminderSwitchStep.SAY_BLOCKED
}

/** The card's state and what its controls do. */
class ReminderSettings(val ui: ReminderSettingsUi, val onChange: (Boolean) -> Unit, val onOpenPhoneSettings: () -> Unit)

/**
 * Wires the Reminders card to the app: the saved choice, Android's "allow notifications?" question
 * (asked only when the owner switches reminders on), and the phone's own settings page for the app.
 */
@Composable
fun rememberReminderSettings(): ReminderSettings {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val on by container.settings.remindersOn.collectAsStateWithLifecycle(initialValue = false)
    val lastCheck by container.settings.lastReminderCheck.collectAsStateWithLifecycle(initialValue = null)
    val today by container.dayClock.today.collectAsStateWithLifecycle()

    // Asked and refused. Kept across a rotation; cleared as soon as the phone allows notifications.
    var refused by rememberSaveable { mutableStateOf(false) }
    var allowed by remember { mutableStateOf(container.reminderNotifier.canNotify()) }
    LifecycleResumeEffect(Unit) {
        // Coming back from the phone's settings: the answer may have changed there.
        allowed = container.reminderNotifier.canNotify()
        if (allowed) refused = false
        onPauseOrDispose { }
    }
    // The switch's work (settings file, WorkManager, database) runs off the main thread.
    fun settle(step: ReminderSwitchStep) {
        when (step) {
            ReminderSwitchStep.TURN_ON -> {
                allowed = true
                refused = false
                scope.launch(Dispatchers.Default) { container.reminders.turnOn() }
            }
            ReminderSwitchStep.SAY_BLOCKED -> refused = true
            ReminderSwitchStep.TURN_OFF -> scope.launch(Dispatchers.Default) { container.reminders.turnOff() }
            ReminderSwitchStep.ASK_THE_PHONE -> Unit // only a tap asks; see onChange
        }
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        settle(ReminderSwitch.onAnswer(granted))
    }

    return ReminderSettings(
        ui = ReminderSettingsUi(
            on = on,
            blocked = refused || (on && !allowed),
            lastCheck = lastCheck?.let { ReminderSettingsText.lastCheck(it, today, ZoneId.systemDefault()) },
            phoneTip = ReminderSettingsText.needsPhoneTip(Build.MANUFACTURER),
        ),
        onChange = { wanted ->
            val mustAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            when (val step = ReminderSwitch.onTap(wanted, mustAsk, phoneAllows = container.reminderNotifier.canNotify())) {
                ReminderSwitchStep.ASK_THE_PHONE -> ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                else -> settle(step)
            }
        },
        onOpenPhoneSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
    )
}
