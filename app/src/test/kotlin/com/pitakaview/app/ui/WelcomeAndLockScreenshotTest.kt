package com.pitakaview.app.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.captureRoboImage
import com.pitakaview.app.domain.AppearanceSettings
import com.pitakaview.app.domain.DarkModePref
import com.pitakaview.app.domain.ThemeId
import com.pitakaview.app.lock.LockSupport
import com.pitakaview.app.ui.lock.LockScreen
import com.pitakaview.app.ui.onboarding.OnboardingContent
import com.pitakaview.app.ui.onboarding.OnboardingWallets
import com.pitakaview.app.ui.settings.LockSettingsUi
import com.pitakaview.app.ui.settings.SettingsScreen
import com.pitakaview.app.ui.theme.FitakaTheme
import com.pitakaview.app.ui.wallets.WalletPreset
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The first-run welcome, the lock screen and the App lock card, at the size of the owner's phone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h850dp-hdpi")
class WelcomeAndLockScreenshotTest {
    @Composable
    private fun Welcome(page: Int, picked: Set<WalletPreset> = emptySet(), problems: Set<WalletPreset> = emptySet()) {
        OnboardingContent(
            page = page,
            cash = TextFieldState(if (page == 1) "1500" else ""),
            amounts = OnboardingWallets.associateWith { TextFieldState(if (it == WalletPreset.GCASH) "250.50" else "") },
            picked = picked, problems = problems,
            onToggle = {}, onNext = {}, onBack = {}, onFinish = {}, onSkip = {},
        )
    }

    private fun shot(name: String, theme: ThemeId, mode: DarkModePref, content: @Composable () -> Unit) =
        captureRoboImage("build/outputs/roborazzi/screen_$name.png") { FitakaTheme(theme, mode) { content() } }

    @Test
    fun welcomeStepOne() = shot("welcome_1_mango_light", ThemeId.MANGO, DarkModePref.LIGHT) { Welcome(0) }

    @Test
    fun welcomeStepTwo() = shot("welcome_2_ube_dark", ThemeId.UBE, DarkModePref.DARK) {
        Welcome(1, picked = setOf(WalletPreset.GCASH, WalletPreset.MARIBANK), problems = setOf(WalletPreset.MARIBANK))
    }

    @Test
    fun welcomeStepThree() = shot("welcome_3_calamansi_light", ThemeId.CALAMANSI, DarkModePref.LIGHT) { Welcome(2) }

    @Test
    fun lockScreen() = shot("lock_dagat_dark", ThemeId.DAGAT, DarkModePref.DARK) { LockScreen(onUnlock = {}, onLeave = {}) }

    // Taller than a phone: the lock card now sits below the theme pickers, and this picture is of that card.
    @Test
    @Config(qualifiers = "w390dp-h1200dp-hdpi")
    fun settingsWithTheAppLock() = shot("settings_lock_kape_light", ThemeId.KAPE, DarkModePref.LIGHT) {
        SettingsScreen(
            AppearanceSettings(ThemeId.KAPE, DarkModePref.LIGHT), {}, {}, onBack = {},
            lock = LockSettingsUi(on = true, support = LockSupport.READY),
        )
    }
}
