package com.pitakaview.app.ui.components

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.pitakaview.app.domain.UiStyle
import com.pitakaview.app.ui.theme.LocalUiStyle
import com.pitakaview.app.ui.theme.PitakaTheme

/** How dark the screen behind a question goes, and how far it is blurred where the phone can blur. */
internal const val QUESTION_DIM = 0.35f
private val QuestionBlur = 18.dp

/**
 * A yes-or-no question about something that cannot be undone, as a card over the whole screen. The
 * screen behind is dimmed, and blurred on phones that can (Android 12 and later, unless the phone has
 * blur switched off to save power; then it is only dimmed).
 *
 * It is a real dialog: Back and a tap outside it answer no, and a screen reader stays inside it.
 * [lines] are separate short paragraphs under the title.
 */
@Composable
fun PitakaConfirmDialog(
    title: String,
    lines: List<String>,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    enabled: Boolean = true,
) {
    Dialog(onDismissRequest = { if (enabled) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BlurWhatIsBehind()
        Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            ConfirmCard(title, lines, confirmLabel, dismissLabel, onConfirm, onDismiss, enabled)
        }
    }
}

/** The card itself, apart from the dialog around it, so a screenshot test can draw it. */
@Composable
internal fun ConfirmCard(
    title: String,
    lines: List<String>,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    enabled: Boolean = true,
) {
    val c = PitakaTheme.colors
    val type = MaterialTheme.typography
    // Never see-through: in the Glassy style a card shows what is behind it, and behind a question is a
    // dimmed screen of anything at all. The words of a question that cannot be undone must be plain to read.
    val style = LocalUiStyle.current
    CompositionLocalProvider(LocalUiStyle provides if (style == UiStyle.GLASSY) UiStyle.PLAIN else style) {
        PitakaCard(Modifier.fillMaxWidth()) {
            Text(title, style = type.titleMedium, color = c.ink, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(4.dp))
            lines.forEach { Text(it, style = type.bodyMedium, color = c.ink) }
            Spacer(Modifier.height(16.dp))
            // One above the other, each the card's full width: side by side, "Yes, delete" was cut short
            // on a phone-sized screen, and the wider fonts and "Yes, restore" need more room still.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PitakaButton(confirmLabel, onClick = onConfirm, modifier = Modifier.fillMaxWidth(), enabled = enabled)
                PitakaButton(dismissLabel, onClick = onDismiss, modifier = Modifier.fillMaxWidth(), enabled = enabled)
            }
        }
    }
}

/** Dims the window behind this dialog and, where Android offers it, blurs it. */
@Composable
private fun BlurWhatIsBehind() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return
    val radius = with(LocalDensity.current) { QuestionBlur.roundToPx() }
    SideEffect {
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setDimAmount(QUESTION_DIM)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply { blurBehindRadius = radius }
        }
    }
}
