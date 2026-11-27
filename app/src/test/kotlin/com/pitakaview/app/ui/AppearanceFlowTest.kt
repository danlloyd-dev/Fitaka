package com.pitakaview.app.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.pitakaview.app.domain.FontChoice
import com.pitakaview.app.domain.ThemeId
import com.pitakaview.app.domain.UiSize
import com.pitakaview.app.domain.UiStyle
import com.pitakaview.app.ui.components.LOGO_TAG
import com.pitakaview.app.ui.theme.family
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The choices added to Settings on 2026-11-26, in the whole app on a phone-sized screen: tapping one
 * changes the app, the size leaves the bottom bar alone, and the logo is at the top left of Home.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h850dp-xhdpi")
class AppearanceFlowTest : FlowTestBase() {
    private fun openSettings() {
        rule.onNodeWithContentDescription("Settings").performClick()
        waitFor("Money set-up")
    }

    /** Scrolls Settings to a choice and taps it. */
    private fun choose(text: String) {
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
        rule.onNode(hasText(text) and hasClickAction()).performClick()
        rule.waitForIdle()
    }

    private fun homeTab(): Rect = rule.onNode(hasText("Home") and isSelectable()).fetchSemanticsNode().boundsInRoot

    private fun cog(): Rect = rule.onNodeWithContentDescription("Settings").fetchSemanticsNode().boundsInRoot

    @Test
    fun theSizeChosenInSettingsShrinksTheAppAndLeavesTheBottomBarItsSize() {
        start()
        val bar = homeTab()
        val button = cog()
        for (size in listOf(UiSize.SMALL, UiSize.MEDIUM, UiSize.LARGE)) {
            openSettings()
            choose(size.label)
            assertEquals(size, appearance.size)
            back()
            waitFor("Cash")
            rule.waitForIdle()
            assertEquals("at ${size.label} the bottom bar's tab is where and as large as it was", bar, homeTab())
            assertEquals("at ${size.label} a button on the page", button.width * size.scale, cog().width, 1.5f)
        }
    }

    @Test
    fun theStyleFontAndPinkThemeAreChosenInSettings() {
        start()
        openSettings()
        for (style in listOf(UiStyle.PLAIN, UiStyle.GLASSY, UiStyle.SKETCH, UiStyle.GLOSSY)) {
            choose(style.label)
            assertEquals(style, appearance.style)
        }
        for (font in FontChoice.entries.reversed()) {
            choose(font.label)
            assertEquals(font, appearance.font)
        }
        choose(FontChoice.ITIM.label)
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Theme"))
        rule.onNode(hasText(ThemeId.ROSAS.label) and hasClickAction()).performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(ThemeId.ROSAS, appearance.theme)

        // Back on Home the words are in the chosen font.
        back()
        waitFor("Cash")
        val drawn = mutableListOf<TextLayoutResult>()
        rule.onNode(hasText("Fitaka"), useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)!!.action!!.invoke(drawn)
        assertEquals(FontChoice.ITIM.family(), drawn.single().layoutInput.style.fontFamily)
    }

    /**
     * Every main page has Home's header (the owner, 2026-11-27: "make the header on each page has logo and
     * the settings so thats its uniform to home"): the logo at the top left, Search and Settings at the top
     * right, and Settings opens from each of them. A page with a way back has the Back button there instead.
     */
    @Test
    fun everyMainPageHasTheLogoAndSettingsAndAPageWithAWayBackHasNeither() {
        start()
        fun logos() = rule.onAllNodes(androidx.compose.ui.test.hasTestTag(LOGO_TAG), useUnmergedTree = true).fetchSemanticsNodes()
        for (tab in listOf("Home", "Kita", "Baon", "Ipon", "Gastos", "Utang")) {
            tapTab(tab)
            rule.waitForIdle()
            val found = logos()
            assertEquals("$tab: one logo", 1, found.size)
            val logo = found.single().boundsInRoot
            with(rule.density) {
                assertTrue("$tab: the logo is at the left edge: $logo", logo.left <= 24.dp.toPx())
                assertTrue("$tab: the logo is at the top: $logo", logo.top <= 80.dp.toPx())
                assertTrue("$tab: it is large enough to make out", logo.width >= 40.dp.toPx())
            }
            rule.onNodeWithContentDescription("Search").assertIsDisplayed()
            rule.onNodeWithContentDescription("Settings").assertIsDisplayed().performClick()
            waitFor("Money set-up")
            rule.waitForIdle()
            assertEquals("$tab > Settings: a page with a way back has the back button there, not the logo", 0, logos().size)
            back()
            rule.waitForIdle()
        }
        // On Home the app's name comes right after the logo.
        tapTab("Home")
        rule.waitForIdle()
        val name = rule.onNode(hasText("Fitaka"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("the app's name comes after the logo", logos().single().boundsInRoot.right <= name.left)
    }
}
