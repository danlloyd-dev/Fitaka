package com.pitakaview.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pitakaview.app.di.LocalAppContainer
import com.pitakaview.app.ui.navigation.PitakaRoot
import com.pitakaview.app.ui.theme.PitakaTheme
import com.pitakaview.app.ui.theme.resolve
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as PitakaApp).container
        val settings = container.settings

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
                        )
                    }
                }
            }
        }
    }
}
