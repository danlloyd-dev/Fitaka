package com.pitakaview.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.pitakaview.app.domain.AppearanceSettings
import com.pitakaview.app.domain.DarkModePref
import com.pitakaview.app.domain.ThemeId
import com.pitakaview.app.lock.LockSupport
import com.pitakaview.app.ui.settings.LockSettingsText
import com.pitakaview.app.ui.settings.LockSettingsUi
import com.pitakaview.app.ui.settings.ReminderSettingsUi
import com.pitakaview.app.ui.settings.SettingsScreen
import com.pitakaview.app.ui.theme.PitakaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * On the owner's phone (2026-11-26) the Reminders switch was found off after the App lock switch had
 * been used: the two sat one above the other, and switching reminders off asks for nothing. They are
 * now kept apart. The screen is drawn very tall here so both are laid out and can be measured.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h2400dp-xhdpi")
class SettingsLayoutTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theRemindersSwitchAndTheLockSwitchAreNotNextToEachOther() {
        rule.setContent {
            PitakaTheme(ThemeId.MANGO, DarkModePref.LIGHT) {
                SettingsScreen(
                    AppearanceSettings(), {}, {}, onBack = {},
                    reminders = ReminderSettingsUi(on = true),
                    lock = LockSettingsUi(on = false, support = LockSupport.READY),
                    version = "test",
                )
            }
        }
        val reminders = rule.onNode(hasText("Remind me") and isToggleable()).fetchSemanticsNode().boundsInRoot
        val lock = rule.onNode(hasText(LockSettingsText.TITLE) and isToggleable()).fetchSemanticsNode().boundsInRoot
        val gap = with(rule.density) { (lock.top - reminders.bottom).toDp() }
        // Three thumb-widths of other things (the theme and light-or-dark pickers) lie between them.
        assertTrue("only $gap between the two switches", gap >= 150.dp)
    }
}
