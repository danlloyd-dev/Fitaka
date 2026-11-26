package com.pitakaview.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.pitakaview.app.domain.AppearanceSettings
import com.pitakaview.app.domain.DarkModePref
import com.pitakaview.app.domain.FontChoice
import com.pitakaview.app.domain.ThemeId
import com.pitakaview.app.domain.UiSize
import com.pitakaview.app.domain.UiStyle
import com.pitakaview.app.lock.LockSupport
import com.pitakaview.app.ui.components.ConfirmCard
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
 * Pictures of the choices added to Settings on 2026-11-26, for looking at: the four styles, the smaller
 * size, the four other fonts, the pink theme's Settings, and the question card.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NewLookOptionsScreenshotTest {
    private fun gallery(name: String, theme: ThemeId, mode: DarkModePref, appearance: AppearanceSettings) =
        captureRoboImage("build/outputs/roborazzi/$name.png") {
            PitakaTheme(theme, mode, appearance.font, appearance.style, appearance.size) {
                ComponentGallery(appearance.copy(theme = theme, darkMode = mode), onThemeChange = {}, onDarkModeChange = {}, scrollable = false)
            }
        }

    @Test
    @Config(qualifiers = "w400dp-h1820dp-hdpi")
    fun theStyles() {
        for (style in listOf(UiStyle.PLAIN, UiStyle.GLASSY, UiStyle.SKETCH)) {
            gallery("style_${style.name.lowercase()}_light", ThemeId.MANGO, DarkModePref.LIGHT, AppearanceSettings(style = style))
        }
        gallery("style_glassy_dark", ThemeId.DAGAT, DarkModePref.DARK, AppearanceSettings(style = UiStyle.GLASSY))
        gallery("style_sketch_dark", ThemeId.UBE, DarkModePref.DARK, AppearanceSettings(style = UiStyle.SKETCH))
    }

    @Test
    @Config(qualifiers = "w400dp-h1820dp-hdpi")
    fun theSmallSize() = gallery("size_small", ThemeId.MANGO, DarkModePref.LIGHT, AppearanceSettings(size = UiSize.SMALL))

    @Test
    @Config(qualifiers = "w400dp-h1000dp-hdpi")
    fun theOtherFonts() {
        for (font in FontChoice.entries - FontChoice.SHANTELL) {
            gallery("font_${font.name.lowercase()}", ThemeId.MANGO, DarkModePref.LIGHT, AppearanceSettings(font = font))
        }
    }

    @Test
    @Config(qualifiers = "w390dp-h2000dp-xhdpi")
    fun settingsInThePinkTheme() = captureRoboImage("build/outputs/roborazzi/screen_settings_rosas_light.png") {
        PitakaTheme(ThemeId.ROSAS, DarkModePref.LIGHT) {
            SettingsScreen(
                AppearanceSettings(ThemeId.ROSAS, DarkModePref.LIGHT), {}, {}, onBack = {},
                reminders = ReminderSettingsUi(on = true), lock = LockSettingsUi(on = false, support = LockSupport.READY), version = "0.1.0",
            )
        }
    }

    @Test
    @Config(qualifiers = "w390dp-h520dp-xhdpi")
    fun theQuestionCard() = captureRoboImage("build/outputs/roborazzi/question_card.png") {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            for ((theme, mode) in listOf(ThemeId.MANGO to DarkModePref.LIGHT, ThemeId.ROSAS to DarkModePref.DARK)) {
                PitakaTheme(theme, mode) {
                    Column(Modifier.background(PitakaTheme.colors.background).padding(24.dp)) {
                        ConfirmCard(
                            "Delete this entry?", listOf("It is removed from your totals. This cannot be undone."),
                            "Yes, delete", "Keep", onConfirm = {}, onDismiss = {},
                        )
                    }
                }
            }
        }
    }
}
