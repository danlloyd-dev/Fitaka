package com.pitakaview.app.ui.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import com.pitakaview.app.ui.components.sketchBox
import com.pitakaview.app.ui.icons.strokeIcon
import com.pitakaview.app.ui.motion.FitakaMotion
import kotlin.math.PI
import kotlin.math.sin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pitakaview.app.di.LocalAppContainer
import com.pitakaview.app.ui.components.FitakaButton
import com.pitakaview.app.ui.components.FitakaCard
import com.pitakaview.app.ui.components.FitakaToggleRow
import com.pitakaview.app.ui.components.ProblemText
import com.pitakaview.app.ui.lock.LocalAppLocked
import com.pitakaview.app.ui.settings.rememberAppVersion
import com.pitakaview.app.ui.theme.FitakaDimens
import com.pitakaview.app.ui.theme.FitakaTheme
import com.pitakaview.app.update.LatestRelease
import com.pitakaview.app.update.UpdateStep
import com.pitakaview.app.update.UpdateText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** True while a newer version is waiting: the Settings button on the main pages then wears a red dot. */
val LocalUpdateWaiting = compositionLocalOf { false }

/** What tests find the red dot by: it is a mark on a button, with no words of its own. */
const val UPDATE_DOT_TAG = "UpdateDot"

/** The dot's red, the same in every theme; its ink outline keeps it apart from a red or pink theme colour. */
private val DotRed = Color(0xFFE53935)

/** The small red dot that says "a new version is out". */
@Composable
fun UpdateDot(modifier: Modifier = Modifier, size: Dp = 12.dp) {
    val c = FitakaTheme.colors
    Box(modifier.testTag(UPDATE_DOT_TAG).size(size).background(DotRed, CircleShape).border(FitakaDimens.borderThin, c.ink, CircleShape))
}

/** What the Settings card and Home's banner show about updates. */
data class UpdatesUi(
    val on: Boolean = true,
    val installed: String = "",
    val waiting: LatestRelease? = null,
    val step: UpdateStep = UpdateStep.Idle,
    /** What the last check the owner asked for found out. */
    val note: String? = null,
    /** When GitHub last answered, in words. */
    val lastCheck: String? = null,
    val notificationsOff: Boolean = false,
) {
    /** Something is under way: no buttons, so nothing can be started twice. */
    val busy: Boolean
        get() = step is UpdateStep.Checking || downloadShown

    /** From the first byte until Android has it: the download animation shows. */
    val downloadShown: Boolean
        get() = step is UpdateStep.Downloading || step is UpdateStep.Verifying || step is UpdateStep.Installing ||
            step is UpdateStep.Question || step is UpdateStep.WithTheOwner

    /** The line that says how the update is getting on, or null when nothing is under way. */
    val progress: String?
        get() = when (val s = step) {
            UpdateStep.Checking -> UpdateText.CHECKING
            is UpdateStep.Downloading -> UpdateText.downloading(s.percent)
            UpdateStep.Verifying -> UpdateText.VERIFYING
            UpdateStep.Installing -> UpdateText.INSTALLING
            is UpdateStep.Question, UpdateStep.WithTheOwner -> UpdateText.WITH_THE_OWNER
            UpdateStep.NeedsPermission -> UpdateText.NEEDS_PERMISSION
            UpdateStep.Idle, is UpdateStep.Stopped -> null
        }

    val problem: String? get() = (step as? UpdateStep.Stopped)?.message
}

object UpdateWords {
    private val TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val DAY = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

    /** "Last checked today at 9:02 AM." */
    fun lastCheck(at: Instant, today: LocalDate, zone: ZoneId): String {
        val time = at.atZone(zone)
        val day = when (time.toLocalDate()) {
            today -> "today"
            today.minusDays(1) -> "yesterday"
            else -> "on ${DAY.format(time)}"
        }
        return "Last checked $day at ${TIME.format(time)}."
    }
}

/** The Updates card in Settings: the switch, where things stand, and the one button that moves them on. */
@Composable
fun UpdatesCard(
    ui: UpdatesUi,
    onChecksChange: (Boolean) -> Unit,
    onCheckNow: () -> Unit,
    onUpdate: () -> Unit,
    onAllow: () -> Unit,
    onAllowNotifications: () -> Unit,
) {
    val c = FitakaTheme.colors
    val type = MaterialTheme.typography
    FitakaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        FitakaToggleRow(title = UpdateText.CHECKS_TITLE, subtitle = UpdateText.CHECKS_SUBTITLE, checked = ui.on, onCheckedChange = onChecksChange)
        val waiting = ui.waiting
        if (waiting != null) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UpdateDot(size = 10.dp)
                Text("${UpdateText.out(waiting.version)} (${UpdateText.megabytes(waiting.apk.size)}).", style = type.titleSmall, color = c.ink)
            }
        } else {
            val upToDate = ui.note ?: if (ui.lastCheck != null) "${UpdateText.UP_TO_DATE} ${ui.lastCheck}" else null
            if (upToDate != null) Text(upToDate, style = type.bodySmall, color = c.inkMuted)
        }
        Text("This phone has version ${ui.installed}.", style = type.bodySmall, color = c.inkMuted)
        if (ui.downloadShown) {
            Spacer(Modifier.height(8.dp))
            DownloadProgress(ui.step)
            Spacer(Modifier.height(4.dp))
        }
        ui.progress?.let { Text(it, style = type.bodyMedium, color = c.ink) }
        ui.problem?.let { ProblemText(it) }
        if (ui.on && ui.notificationsOff) Text(UpdateText.NOTIFICATIONS_OFF, style = type.bodySmall, color = c.inkMuted)
        Spacer(Modifier.height(12.dp))
        when {
            ui.step == UpdateStep.NeedsPermission -> FitakaButton(UpdateText.ALLOW, onClick = onAllow, modifier = Modifier.fillMaxWidth())
            ui.busy -> Unit
            waiting != null -> FitakaButton(
                if (ui.problem != null) UpdateText.TRY_AGAIN else UpdateText.updateTo(waiting.version), onClick = onUpdate, modifier = Modifier.fillMaxWidth(),
            )
            else -> FitakaButton(UpdateText.CHECK_NOW, onClick = onCheckNow, modifier = Modifier.fillMaxWidth())
        }
        if (ui.on && ui.notificationsOff && !ui.busy) {
            Spacer(Modifier.height(8.dp))
            FitakaButton(UpdateText.ALLOW_NOTIFICATIONS, onClick = onAllowNotifications, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Home's banner about a new version: first on the page, with Later and Update. */
@Composable
fun UpdateBanner(ui: UpdatesUi, onUpdate: () -> Unit, onLater: () -> Unit, onAllow: () -> Unit) {
    val release = ui.waiting ?: return
    val c = FitakaTheme.colors
    val type = MaterialTheme.typography
    FitakaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UpdateDot(size = 10.dp)
            Text(UpdateText.out(release.version), style = type.titleSmall, color = c.ink)
        }
        if (ui.downloadShown) {
            Spacer(Modifier.height(8.dp))
            DownloadProgress(ui.step)
            Spacer(Modifier.height(4.dp))
        }
        Text(ui.progress ?: UpdateText.bannerBody(release.apk.size), style = type.bodyMedium, color = c.ink)
        ui.problem?.let { ProblemText(it) }
        when {
            ui.step == UpdateStep.NeedsPermission -> {
                Spacer(Modifier.height(10.dp))
                FitakaButton(UpdateText.ALLOW, onClick = onAllow, modifier = Modifier.fillMaxWidth())
            }
            ui.busy -> Unit
            else -> {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FitakaButton(UpdateText.LATER, onClick = onLater, modifier = Modifier.weight(1f))
                    FitakaButton(if (ui.problem != null) UpdateText.TRY_AGAIN else UpdateText.UPDATE, onClick = onUpdate, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** What tests find the download animation's bar by. */
const val DOWNLOAD_BAR_TAG = "DownloadBar"

// The arrow and its tray are two pictures, so the arrow can drop into the tray while the tray stays put.
private val DropArrow by lazy { strokeIcon("DownloadArrow", "M12 3.5 V13", "M7.5 8.5 L12 13 L16.5 8.5") }
private val Tray by lazy { strokeIcon("DownloadTray", "M5 14.5 V19 H19 V14.5") }

/** One drop of the arrow into the tray, in milliseconds. */
private const val DROP_MS = 900

/** How far apart the stripes on the bar are, and how far they lean. */
private val StripeGap = 14.dp

/**
 * The download animation (the owner asked for one, 2026-11-27): an arrow that keeps dropping into a tray while
 * bytes arrive, beside a bar that fills to the share downloaded, its stripes running along as it fills. Once the
 * download is in, the bar is full and the arrow rests in its tray while the file is checked and installed.
 */
@Composable
fun DownloadProgress(step: UpdateStep, modifier: Modifier = Modifier) {
    val c = FitakaTheme.colors
    val downloading = step is UpdateStep.Downloading
    val share = if (step is UpdateStep.Downloading) step.percent / 100f else 1f
    // Each new share is reached smoothly, not in jumps of the 64 KB pieces the download comes in.
    val filled by animateFloatAsState(share, tween(FitakaMotion.MEDIUM, easing = FitakaMotion.Emphasized), label = "download-bar")
    val motion = rememberInfiniteTransition(label = "download")
    val fall by motion.animateFloat(0f, 1f, infiniteRepeatable(tween(DROP_MS, easing = LinearEasing), RepeatMode.Restart), label = "arrow")
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .padding(end = 3.dp, bottom = 3.dp)
                .size(44.dp)
                .sketchBox(CircleShape, c.iconButton, c.ink, FitakaDimens.borderThin, c.shadow, 3.dp, glass = true)
                .clip(CircleShape),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            Icon(
                DropArrow, contentDescription = null, tint = c.ink,
                modifier = Modifier.size(24.dp).graphicsLayer {
                    if (downloading) {
                        // From above the circle down into the tray, fading in at the top and out at the bottom.
                        translationY = (fall - 0.45f) * 18.dp.toPx()
                        alpha = sin(PI.toFloat() * fall).coerceIn(0f, 1f)
                    }
                },
            )
            Icon(Tray, contentDescription = null, tint = c.ink, modifier = Modifier.size(24.dp))
        }
        val bar = RoundedCornerShape(9.dp)
        Box(
            Modifier
                .weight(1f)
                .padding(end = 2.dp, bottom = 2.dp)
                .height(18.dp)
                .testTag(DOWNLOAD_BAR_TAG)
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo(share, 0f..1f) }
                .sketchBox(bar, c.card, c.ink, FitakaDimens.borderThin, c.shadow, 2.dp, glass = true)
                .clip(bar),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(filled)
                    // The stripes lean, so the last one reaches past the filled part: kept inside it.
                    .clipToBounds()
                    .background(c.primary)
                    .drawBehind {
                        if (!downloading) return@drawBehind
                        // Leaning stripes, a shade lighter, that run along the filled part.
                        val gap = StripeGap.toPx()
                        val run = fall * gap
                        val light = c.onPrimary.copy(alpha = 0.22f)
                        var x = -size.height + run - gap
                        while (x < size.width) {
                            drawLine(light, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = gap / 3)
                            x += gap
                        }
                    },
            )
        }
    }
}

/** The card's state and what its controls do, for Settings and Home alike. */
class UpdateControls(
    val ui: UpdatesUi,
    /** The waiting version for Home's banner: null when there is none or it was put off today. */
    val banner: LatestRelease?,
    val onChecksChange: (Boolean) -> Unit,
    val onCheckNow: () -> Unit,
    val onUpdate: () -> Unit,
    val onLater: () -> Unit,
    val onAllow: () -> Unit,
    val onAllowNotifications: () -> Unit,
)

/** Wires the update parts to the app: what is stored, what is under way, and the phone's two settings pages. */
@Composable
fun rememberUpdateControls(): UpdateControls {
    val container = LocalAppContainer.current
    val updates = container.updates
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val on by updates.on.collectAsStateWithLifecycle(initialValue = true)
    val waiting by updates.waiting.collectAsStateWithLifecycle(initialValue = null)
    val banner by remember(updates) { updates.banner(container.dayClock.today) }.collectAsStateWithLifecycle(initialValue = null)
    val step by updates.step.collectAsStateWithLifecycle()
    val note by updates.note.collectAsStateWithLifecycle()
    val lastCheckedAt by updates.lastCheckedAt.collectAsStateWithLifecycle(initialValue = null)
    val today by container.dayClock.today.collectAsStateWithLifecycle()
    var notificationsOff by remember { mutableStateOf(!container.updateNotifier.canNotify()) }
    LifecycleResumeEffect(Unit) {
        // Back from one of the phone's settings pages: what was allowed there may have changed.
        notificationsOff = !container.updateNotifier.canNotify()
        updates.onReturned()
        onPauseOrDispose { }
    }
    return UpdateControls(
        ui = UpdatesUi(
            on = on,
            installed = rememberAppVersion(),
            waiting = waiting,
            step = step,
            note = note,
            lastCheck = lastCheckedAt?.let { UpdateWords.lastCheck(it, today, ZoneId.systemDefault()) },
            notificationsOff = notificationsOff,
        ),
        banner = banner,
        onChecksChange = { wanted -> scope.launch(Dispatchers.Default) { updates.setOn(wanted) } },
        onCheckNow = updates::checkNow,
        onUpdate = updates::startUpdate,
        onLater = { banner?.let { release -> scope.launch(Dispatchers.Default) { updates.later(release, today) } } },
        onAllow = { context.openOrAppPage(container.updateInstaller.allowIntent()) },
        onAllowNotifications = {
            context.openOrAppPage(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        },
    )
}

/** Opens one of the phone's settings pages for this app, or the app's general page on a phone that lacks it. */
private fun Context.openOrAppPage(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }
}

/**
 * Puts Android's "Do you want to update this app?" on screen as soon as it is ready, from the app's own screen
 * (a start from the background the phone may hold back), and never over the app lock.
 */
@Composable
fun UpdateQuestionHost() {
    val updates = LocalAppContainer.current.updates
    val step by updates.step.collectAsStateWithLifecycle()
    val locked = LocalAppLocked.current
    val context = LocalContext.current
    val question = step as? UpdateStep.Question
    LaunchedEffect(question, locked) {
        if (question == null || locked) return@LaunchedEffect
        try {
            context.startActivity(question.ask)
            updates.questionShown()
        } catch (e: ActivityNotFoundException) {
            updates.onInstallStopped(cancelled = false, why = null)
        }
    }
}
