package com.pitakaview.app.ui.lock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pitakaview.app.APP_NAME
import com.pitakaview.app.ui.components.FitakaButton
import com.pitakaview.app.ui.components.FitakaButtonStyle
import com.pitakaview.app.ui.mascot.Alkansya
import com.pitakaview.app.ui.mascot.AlkansyaMood
import com.pitakaview.app.ui.theme.FitakaTheme

/**
 * Covers the whole app while it is locked. It is drawn over the app (which stays as it was underneath,
 * so an unsaved form is still there after unlocking) and lets nothing through: no taps, no Back.
 *
 * [onUnlock] shows the phone's unlock question. It is asked once by itself when the lock screen appears,
 * and again whenever the button is tapped. [onLeave] is what Back does: it puts the app away.
 */
/**
 * Whether the app is locked right now. A dialog is a window of its own, above the activity and so above
 * the lock screen: one left open (a delete question, a restore question, the date picker) stayed readable
 * and could be answered while the app was locked. Dialogs read this and are not composed while it is true;
 * what opened them is remembered, so they are back after unlocking.
 */
val LocalAppLocked = compositionLocalOf { false }

@Composable
fun LockScreen(onUnlock: () -> Unit, onLeave: () -> Unit, modifier: Modifier = Modifier) {
    val c = FitakaTheme.colors
    val type = MaterialTheme.typography
    val focus = LocalFocusManager.current
    LaunchedEffect(Unit) {
        // A text box underneath must not keep the keyboard up over the lock screen.
        focus.clearFocus(force = true)
        onUnlock()
    }
    // Composed above the app's own Back handling, so Back never moves the screens underneath.
    BackHandler(onBack = onLeave)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(c.background)
            // Swallows every tap; nothing underneath can be reached.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = { })
            .safeDrawingPadding()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Alkansya(mood = AlkansyaMood.SLEEPY, width = 160.dp)
        Spacer(Modifier.height(24.dp))
        Text("$APP_NAME is locked", style = type.headlineSmall, color = c.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Use your fingerprint, face or phone PIN to open it.",
            style = type.bodyLarge, color = c.inkMuted, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        FitakaButton("Unlock", onClick = onUnlock, modifier = Modifier.fillMaxWidth(), style = FitakaButtonStyle.Primary)
    }
}
