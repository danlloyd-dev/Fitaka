package com.pitakaview.app.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.pitakaview.app.domain.AppearanceSettings
import com.pitakaview.app.domain.DarkModePref
import com.pitakaview.app.domain.ThemeId
import com.pitakaview.app.lock.LockSupport
import com.pitakaview.app.ui.lock.LockScreen
import com.pitakaview.app.ui.onboarding.OnboardingContent
import com.pitakaview.app.ui.onboarding.OnboardingWallets
import com.pitakaview.app.ui.settings.LockSettingsUi
import com.pitakaview.app.ui.settings.ReminderSettingsUi
import com.pitakaview.app.ui.settings.SettingsScreen
import com.pitakaview.app.ui.theme.PitakaTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screens with the phone's text size turned up to 1.5 times, at the size of the owner's phone: the
 * setting people with weaker eyes use. Looked at for words cut off or pushed off the screen.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h850dp-hdpi")
class LargeTextScreenshotTest {
    private fun shot(name: String, content: @Composable () -> Unit) = captureRoboImage("build/outputs/roborazzi/large_text_$name.png") {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.5f)) {
            PitakaTheme(ThemeId.MANGO, DarkModePref.LIGHT) { content() }
        }
    }

    @Test
    fun settings() = shot("settings") {
        SettingsScreen(
            AppearanceSettings(ThemeId.MANGO, DarkModePref.LIGHT), {}, {}, onBack = {},
            reminders = ReminderSettingsUi(on = true, lastCheck = "Last checked today at 8:02 AM, in the background.", phoneTip = true),
            lock = LockSettingsUi(on = false, support = LockSupport.READY),
        )
    }

    @Test
    fun welcome() = shot("welcome") {
        OnboardingContent(
            page = 1, cash = TextFieldState("1500"), amounts = OnboardingWallets.associateWith { TextFieldState() },
            picked = setOf(OnboardingWallets.first()), problems = emptySet(),
            onToggle = {}, onNext = {}, onBack = {}, onFinish = {}, onSkip = {},
        )
    }

    @Test
    fun lock() = shot("lock") { LockScreen(onUnlock = {}, onLeave = {}) }
}
