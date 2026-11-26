package com.pitakaview.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pitakaview.app.PitakaApp
import com.pitakaview.app.di.LocalAppContainer
import com.pitakaview.app.domain.AppearanceSettings
import com.pitakaview.app.domain.DarkModePref
import com.pitakaview.app.domain.ThemeId
import com.pitakaview.app.ui.navigation.PitakaRoot
import com.pitakaview.app.ui.theme.PitakaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Drives the real app shell with simulated taps on the JVM (Robolectric): the real navigation, the real
 * database and the real frosted bars. This is the pattern every later screen-flow test follows.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h800dp-xhdpi")
class NavigationFlowTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    /** A bottom-bar tab (the Home tiles carry the same words but are buttons, not tabs). */
    private fun tab(label: String): SemanticsMatcher = hasText(label) and isSelectable()

    @Composable
    private fun App() {
        val container = (LocalContext.current.applicationContext as PitakaApp).container
        CompositionLocalProvider(LocalAppContainer provides container) {
            PitakaTheme(ThemeId.MANGO, DarkModePref.LIGHT) {
                PitakaRoot(AppearanceSettings(), onThemeChange = {}, onDarkModeChange = {})
            }
        }
    }

    @Test
    fun tabsSettingsAndBackAllWork() {
        rule.setContent { App() }
        rule.onNodeWithText("Your whole wallet").assertIsDisplayed()
        rule.onNode(tab("Home")).assertIsSelected()

        rule.onNode(tab("Gastos")).performClick()
        rule.onNodeWithText("Being built next").assertIsDisplayed()
        rule.onNode(tab("Gastos")).assertIsSelected()

        // System back from a tab returns to Home.
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithText("Your whole wallet").assertIsDisplayed()
        rule.onNode(tab("Home")).assertIsSelected()

        // Settings is a pushed screen: the tab bar goes away, and its own back button returns.
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Light or dark").assertIsDisplayed()
        rule.onNode(tab("Home")).assertDoesNotExist()

        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithText("Your whole wallet").assertIsDisplayed()
        rule.onNode(tab("Home")).assertIsSelected()
    }

    @Test
    fun theOpenTabSurvivesTheAppBeingClosedAndRestored() {
        val tester = StateRestorationTester(rule)
        tester.setContent { App() }
        rule.onNode(tab("Utang")).performClick()
        rule.onNodeWithText("Being built next").assertIsDisplayed()

        // What Android does when it reclaims the app in the background and brings it back.
        tester.emulateSavedInstanceStateRestore()

        rule.onNodeWithText("Being built next").assertIsDisplayed()
        rule.onNode(tab("Utang")).assertIsSelected()
    }
}
