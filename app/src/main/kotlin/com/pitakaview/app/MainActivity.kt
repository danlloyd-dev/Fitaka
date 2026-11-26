package com.pitakaview.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.pitakaview.app.di.LocalAppContainer
import com.pitakaview.app.reminders.ReminderIntents
import com.pitakaview.app.reminders.ReminderOpen
import com.pitakaview.app.ui.navigation.PitakaRoot
import com.pitakaview.app.ui.theme.PitakaTheme
import com.pitakaview.app.ui.theme.resolve
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    /** The screen a tapped reminder asked for, until the app has gone there. */
    private var reminderToOpen by mutableStateOf<ReminderOpen?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as PitakaApp).container
        val settings = container.settings
        // Only on a fresh start: after a rotation the same intent is still attached, and the owner has
        // already been taken where the reminder pointed.
        if (savedInstanceState == null) reminderToOpen = ReminderIntents.read(intent)

        // Keep the app's idea of "today" fresh: re-read it whenever the app comes to the front, and again
        // just after each midnight while it stays open. One sleeping coroutine; no polling.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    container.dayClock.refresh()
                    delay(container.dayClock.millisUntilTomorrow())
                }
            }
        }
        // Each time the app comes to the front: with reminders on, make sure the daily check is still lined
        // up and catch up on a check the phone held back. With them off this reads one setting and stops.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                withContext(Dispatchers.Default) { container.reminders.onAppOpened() }
            }
        }

        setContent {
            // Nothing is drawn until settings load, so a saved theme never flashes the default first.
            val appearance by settings.appearance.collectAsStateWithLifecycle(initialValue = null)
            val current = appearance
            if (current != null) {
                val dark = current.darkMode.resolve()
                // Status-bar icons follow the app's light/dark choice, not just the phone's.
                LaunchedEffect(dark) {
                    val style = if (dark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    }
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                val scope = rememberCoroutineScope()
                CompositionLocalProvider(LocalAppContainer provides container) {
                    PitakaTheme(current.theme, current.darkMode) {
                        PitakaRoot(
                            appearance = current,
                            onThemeChange = { scope.launch { settings.setTheme(it) } },
                            onDarkModeChange = { scope.launch { settings.setDarkMode(it) } },
                            reminderToOpen = reminderToOpen,
                            onReminderOpened = { reminderToOpen = null },
                        )
                    }
                }
            }
        }
    }

    /** A reminder tapped while the app is already open. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        ReminderIntents.read(intent)?.let { reminderToOpen = it }
    }
}
