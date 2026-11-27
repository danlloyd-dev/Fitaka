package com.pitakaview.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.pitakaview.app.di.LocalAppContainer
import com.pitakaview.app.lock.LockSupport
import com.pitakaview.app.lock.UnlockResult
import com.pitakaview.app.reminders.ReminderIntents
import com.pitakaview.app.reminders.ReminderOpen
import com.pitakaview.app.ui.lock.LocalAppLocked
import com.pitakaview.app.ui.lock.LockScreen
import com.pitakaview.app.ui.navigation.FitakaRoot
import com.pitakaview.app.ui.onboarding.OnboardingLogic
import com.pitakaview.app.ui.onboarding.OnboardingRoute
import com.pitakaview.app.ui.theme.FitakaTheme
import com.pitakaview.app.ui.theme.resolve
import com.pitakaview.app.update.UpdateIntents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    /** The screen a tapped reminder asked for, until the app has gone there. */
    private var reminderToOpen by mutableStateOf<ReminderOpen?>(null)

    /** A tapped "new version" notification, until the app has opened Settings at its Updates card. */
    private var updatesToOpen by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as FitakaApp).container
        val settings = container.settings
        val appLock = container.appLock
        // Only on a fresh start: after a rotation the same intent is still attached, and the owner has
        // already been taken where the reminder pointed.
        if (savedInstanceState == null) {
            reminderToOpen = ReminderIntents.read(intent)
            updatesToOpen = UpdateIntents.read(intent)
        }

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
        // And for updates, apart, so waiting on GitHub never holds the reminders up: with checks on, keep the
        // daily look lined up and ask GitHub if a day has passed since its last answer.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                withContext(Dispatchers.Default) { container.updates.onAppOpened() }
            }
        }

        // The app lock. With it on, the app starts locked and locks again after a while out of sight.
        lifecycleScope.launch { appLock.load() }
        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    // A rotation also stops and starts the screen; that is not leaving.
                    Lifecycle.Event.ON_STOP -> if (!isChangingConfigurations) appLock.onLeft()
                    Lifecycle.Event.ON_START -> appLock.onReturned()
                    else -> Unit
                }
            },
        )
        // While the lock is on, the phone keeps the app out of screenshots and shows it blank in the list
        // of recent apps: the lock would mean little if the last screen could be read from there.
        lifecycleScope.launch {
            settings.lockOn.collect { on ->
                if (on) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }

        setContent {
            // Nothing is drawn until settings load, so a saved theme never flashes the default first and
            // a locked app never shows its content first.
            val appearance by settings.appearance.collectAsStateWithLifecycle(initialValue = null)
            val locked by appLock.locked.collectAsStateWithLifecycle()
            // Whether to show the first-run welcome. Decided once and kept across a rotation, so turning
            // the phone in the middle of it neither restarts it nor ends it.
            var welcome by rememberSaveable { mutableStateOf<Boolean?>(null) }
            LaunchedEffect(Unit) {
                if (welcome == null) welcome = OnboardingLogic.isNeeded(settings, container.database)
            }
            val current = appearance
            val isLocked = locked
            val showWelcome = welcome
            if (current != null && isLocked != null && showWelcome != null) {
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
                CompositionLocalProvider(LocalAppContainer provides container, LocalAppLocked provides isLocked) {
                    FitakaTheme(current.theme, current.darkMode, current.font, current.style, current.size) {
                        Box(Modifier.fillMaxSize()) {
                            // The app stays as it was under the lock screen (an unsaved form is still there
                            // after unlocking) but is hidden from screen readers while locked.
                            Box(if (isLocked) Modifier.fillMaxSize().clearAndSetSemantics { } else Modifier.fillMaxSize()) {
                                if (showWelcome) {
                                    OnboardingRoute(onDone = { welcome = false })
                                } else {
                                    FitakaRoot(
                                        appearance = current,
                                        onThemeChange = { scope.launch { settings.setTheme(it) } },
                                        onDarkModeChange = { scope.launch { settings.setDarkMode(it) } },
                                        onSizeChange = { scope.launch { settings.setUiSize(it) } },
                                        onStyleChange = { scope.launch { settings.setUiStyle(it) } },
                                        onFontChange = { scope.launch { settings.setFont(it) } },
                                        reminderToOpen = reminderToOpen,
                                        onReminderOpened = { reminderToOpen = null },
                                        openUpdates = updatesToOpen,
                                        onUpdatesOpened = { updatesToOpen = false },
                                    )
                                }
                            }
                            if (isLocked) LockScreen(onUnlock = ::askToUnlock, onLeave = { moveTaskToBack(true) })
                        }
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
        if (UpdateIntents.read(intent)) updatesToOpen = true
    }

    private fun askToUnlock() {
        val container = (application as FitakaApp).container
        // The phone's own screen lock was removed since: there is nothing left to ask for, and keeping the
        // app shut would lock the owner out of their own data.
        if (container.unlocker.support() != LockSupport.READY) {
            container.appLock.onUnlocked()
            return
        }
        container.unlocker.ask(this, "Unlock $APP_NAME") { result ->
            if (result == UnlockResult.UNLOCKED) container.appLock.onUnlocked()
        }
    }
}
