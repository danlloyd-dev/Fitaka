package com.pitakaview.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.pitakaview.app.data.db.dao.WalletWithBalance
import com.pitakaview.app.data.db.entity.WalletEntity
import com.pitakaview.app.domain.DarkModePref
import com.pitakaview.app.domain.ThemeId
import com.pitakaview.app.domain.UiStyle
import com.pitakaview.app.domain.WalletType
import com.pitakaview.app.ui.home.HomeContent
import com.pitakaview.app.ui.home.HomeState
import com.pitakaview.app.ui.theme.FitakaTheme
import com.pitakaview.app.ui.update.LocalUpdateWaiting
import com.pitakaview.app.ui.update.UpdateBanner
import com.pitakaview.app.ui.update.UpdatesCard
import com.pitakaview.app.ui.update.UpdatesUi
import com.pitakaview.app.update.UpdateSamples
import com.pitakaview.app.update.UpdateStep
import com.pitakaview.app.update.UpdateText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate

/**
 * What the owner sees of a new version, at the size of their phone (made-up amounts): Home with its banner
 * and the red dot, the banner during the download animation, and the Settings card in each of its states.
 *
 * On a clock the test moves by hand: the download arrow moves for as long as the download runs, so a capture
 * that waits for the screen to come to rest would wait for ever (the first version of this test did). The
 * clock is stopped, moved [MID_DROP] on, and the picture taken with the arrow half way into its tray.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h850dp-hdpi")
class UpdateScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val release = UpdateSamples.release("1.2.0", ByteArray(2_864_829))
    private val waiting = UpdatesUi(installed = "1.1.0", waiting = release)
    private val home = HomeState(wallets = listOf(WalletWithBalance(WalletEntity(id = 1, name = "Cash", type = WalletType.CASH, createdAt = Instant.EPOCH), 85_000)))

    private fun shot(name: String, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent(content)
        rule.mainClock.advanceTimeBy(MID_DROP)
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/screen_update_$name.png")
    }

    private fun homeShot(name: String, ui: UpdatesUi, theme: ThemeId = ThemeId.MANGO, mode: DarkModePref = DarkModePref.LIGHT) = shot("home_$name") {
        FitakaTheme(theme, mode) {
            CompositionLocalProvider(LocalUpdateWaiting provides true) {
                HomeContent(home, LocalDate.of(2026, 11, 28), "Magandang umaga!", {}, {}, {}, update = ui)
            }
        }
    }

    private fun parts(name: String, theme: ThemeId = ThemeId.MANGO, mode: DarkModePref = DarkModePref.LIGHT, content: @Composable () -> Unit) = shot(name) {
        FitakaTheme(theme, mode) {
            Column(
                Modifier.fillMaxWidth().background(FitakaTheme.colors.background).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) { content() }
        }
    }

    @Composable
    private fun Card(ui: UpdatesUi) = UpdatesCard(ui, {}, {}, {}, {}, {})

    @Test fun homeWithTheBannerAndTheDot() = homeShot("banner_mango_light", waiting)
    @Test fun homeWithTheBannerInTheDark() = homeShot("banner_ube_dark", waiting, ThemeId.UBE, DarkModePref.DARK)
    @Test fun homeWhileDownloading() = homeShot("downloading_mango_light", waiting.copy(step = UpdateStep.Downloading(45)))

    @Test
    @Config(qualifiers = "w390dp-h1500dp-hdpi")
    fun theBannerFromStartToFinish() = parts("banner_steps_mango_light") {
        UpdateBanner(waiting.copy(step = UpdateStep.Downloading(12)), {}, {}, {})
        UpdateBanner(waiting.copy(step = UpdateStep.Downloading(80)), {}, {}, {})
        UpdateBanner(waiting.copy(step = UpdateStep.Installing), {}, {}, {})
        UpdateBanner(waiting.copy(step = UpdateStep.NeedsPermission), {}, {}, {})
        UpdateBanner(waiting.copy(step = UpdateStep.Stopped(UpdateText.DAMAGED)), {}, {}, {})
    }

    @Test
    fun theBannerDownloadingInEveryStyle() = parts("banner_styles_ube_dark", ThemeId.UBE, DarkModePref.DARK) {
        for (style in UiStyle.entries) {
            FitakaTheme(ThemeId.UBE, DarkModePref.DARK, style = style) { UpdateBanner(waiting.copy(step = UpdateStep.Downloading(60)), {}, {}, {}) }
        }
    }

    @Test
    @Config(qualifiers = "w390dp-h1500dp-hdpi")
    fun theSettingsCardInEachState() = parts("settings_card_mango_light") {
        Card(UpdatesUi(installed = "1.1.0", lastCheck = "Last checked today at 9:02 AM."))
        Card(waiting)
        Card(waiting.copy(step = UpdateStep.Downloading(45)))
        Card(UpdatesUi(installed = "1.1.0", note = UpdateText.OFFLINE, notificationsOff = true))
    }

    @Test
    fun theSettingsCardInTheDark() = parts("settings_card_rosas_dark", ThemeId.ROSAS, DarkModePref.DARK) {
        Card(waiting)
        Card(waiting.copy(step = UpdateStep.Verifying))
    }

    private companion object {
        /** Half of one drop of the arrow (900 ms), plus the bar's own move to its share (300 ms). */
        const val MID_DROP = 450L
    }
}
