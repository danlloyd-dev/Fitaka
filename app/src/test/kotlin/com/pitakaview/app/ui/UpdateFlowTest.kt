package com.pitakaview.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import com.pitakaview.app.ui.components.SETTINGS_WITH_UPDATE
import com.pitakaview.app.ui.update.DOWNLOAD_BAR_TAG
import com.pitakaview.app.ui.update.UPDATE_DOT_TAG
import com.pitakaview.app.update.FakeInstaller
import com.pitakaview.app.update.FakeNotifier
import com.pitakaview.app.update.FakePackages
import com.pitakaview.app.update.FakeReleaseSource
import com.pitakaview.app.update.UpdateSamples
import com.pitakaview.app.update.UpdateText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A new version on GitHub, as the owner meets it in the running app at the size of their phone: the banner on
 * Home, the red dot on the Settings button, Later, Update with its download animation, the Settings card with
 * its button and switch, and a tapped notification. GitHub and Android's installer are stand-ins.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h850dp-xhdpi")
class UpdateFlowTest : FlowTestBase() {
    private val github = FakeReleaseSource()
    private val installer = FakeInstaller()
    private val apk = UpdateSamples.apkBytes()

    @Before
    fun newVersionOnGithub() {
        container.releaseSource = github
        container.updateInstaller = installer
        container.updateNotifier = FakeNotifier()
        container.packageReader = FakePackages().apply { archive = UpdateSamples.identity(4, "1.2.0") }
        github.publish("1.2.0", apk)
        // What opening the app does (MainActivity): it asks GitHub, which has 1.2.0.
        runBlocking { container.updates.onAppOpened() }
    }

    private val out = UpdateText.out(com.pitakaview.app.update.AppVersion.parse("1.2.0")!!)
    private fun dots() = rule.onAllNodesWithTag(UPDATE_DOT_TAG, useUnmergedTree = true).fetchSemanticsNodes().size
    private fun settingsWithDot() = rule.onAllNodesWithContentDescription(SETTINGS_WITH_UPDATE).fetchSemanticsNodes().size
    private fun scrollTo(text: String) = rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text, substring = true))

    @Test
    fun aNewVersionShowsAsABannerOnHomeAndARedDotOnTheSettingsButton() {
        start()
        waitFor(out)
        assertEquals("the Settings button says so to a screen reader", 1, settingsWithDot())
        assertEquals("one dot on the button, one in the banner", 2, dots())
    }

    /**
     * The banner comes in a moment after Home is on screen (GitHub's answer takes that long on the phone too). It
     * must then be in view, under the top bar: it used to land above the wallet card, hidden under the bar, and a
     * tap on its Update button opened Search, which sits there.
     */
    @Test
    fun theBannerThatComesInLateIsInViewBelowTheTopBar() {
        start()
        waitFor(out)
        rule.waitForIdle()
        val bar = rule.onNodeWithContentDescription("Search").fetchSemanticsNode().boundsInRoot
        val update = rule.onNode(hasText(UpdateText.UPDATE) and hasClickAction()).fetchSemanticsNode().boundsInRoot
        assertTrue("Update ($update) is below the top bar's buttons ($bar)", update.top >= bar.bottom)
    }

    @Test
    fun laterPutsTheBannerAwayAndTheDotStays() {
        start()
        waitFor(out)
        tapButton(UpdateText.LATER)
        rule.waitUntil(5_000) { !shows(out) }
        assertEquals(1, settingsWithDot())
        assertEquals(1, dots())
    }

    @Test
    fun updateOnTheBannerDownloadsWithItsAnimationAndInstalls() {
        start()
        waitFor(out)
        tapButton(UpdateText.UPDATE)
        rule.waitUntil(5_000) { installer.handedOver.isNotEmpty() }
        assertTrue("Android got the file that was checked", installer.handedOver.single().contentEquals(apk))
        waitFor("Installing")
        assertEquals("the download animation is on screen", 1, rule.onAllNodesWithTag(DOWNLOAD_BAR_TAG).fetchSemanticsNodes().size)
    }

    @Test
    fun settingsHasTheUpdateButton() {
        start()
        waitFor(out)
        tapDescribed(SETTINGS_WITH_UPDATE)
        waitFor("Money set-up")
        scrollTo(UpdateText.updateTo(github.release!!.version))
        tapButton(UpdateText.updateTo(github.release!!.version))
        rule.waitUntil(5_000) { installer.handedOver.isNotEmpty() }
    }

    @Test
    fun switchingChecksOffTakesTheBannerAndTheDotAway() {
        start()
        waitFor(out)
        tapDescribed(SETTINGS_WITH_UPDATE)
        waitFor("Money set-up")
        scrollTo(UpdateText.CHECKS_SUBTITLE)
        tapButton(UpdateText.CHECKS_TITLE)
        rule.waitUntil(5_000) { runBlocking { !container.settings.updateChecksOn.first() } }
        rule.waitUntil(5_000) { dots() == 0 }
        back()
        rule.waitUntil(5_000) { !shows(out) }
        assertEquals(0, settingsWithDot())
    }

    @Test
    fun aTappedNotificationOpensSettingsAtTheUpdatesCard() {
        start()
        waitFor(out)
        openUpdates = true
        // Settings opens already scrolled to its Updates card (the top of Settings is out of view by then).
        waitFor(UpdateText.updateTo(github.release!!.version))
        rule.waitUntil(5_000) { !openUpdates }
        rule.waitForIdle()
        rule.onNodeWithText(UpdateText.updateTo(github.release!!.version)).assertIsDisplayed()
        assertTrue("on Settings", shows("Updates"))
    }

    @Test
    fun withoutTheOkToInstallAppsTheButtonOpensThePhonesPageForIt() {
        installer.allowed = false
        start()
        waitFor(out)
        tapButton(UpdateText.UPDATE)
        waitFor(UpdateText.NEEDS_PERMISSION)
        tapButton(UpdateText.ALLOW)
        assertEquals(FakeInstaller.ALLOW, shadowOf(rule.activity).nextStartedActivity?.action)
        assertTrue("nothing downloaded without the OK", github.asked.none { it.startsWith("download") })
    }
}
