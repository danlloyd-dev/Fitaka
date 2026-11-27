package com.pitakaview.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pitakaview.app.ui.settings.AboutText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * The app is called Fitaka (since 2026-11-27; PitakaView before). The name is written in two places, one
 * for the screens and one for the launcher, and the description in two, the About card and the README:
 * this keeps each pair the same, and keeps the old name and the old tagline out of what ships.
 */
@RunWith(RobolectricTestRunner::class)
class AppNameTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun theLauncherAndTheScreensUseTheSameName() {
        assertEquals("Fitaka", APP_NAME)
        assertEquals(APP_NAME, context.getString(R.string.app_name))
        assertEquals("what the launcher shows under the icon", APP_NAME, context.applicationInfo.loadLabel(context.packageManager).toString())
    }

    @Test
    fun theDescriptionIsTheOwnersAndItsVersionIsTheInstalledOne() {
        assertTrue(AboutText.DESCRIPTION.startsWith("Fitaka (Financial + Pitaka) is an Android app"))
        // Since 1.1.0 the app goes online for updates, so it no longer says "Everything stays on your phone"
        // (the owner agreed to the new words, 2026-11-27): it says what stays and what goes online.
        for (part in listOf("Kita", "Baon", "Ipon", "Gastos", "Utang", "Your entries stay on your phone", "goes online only to ask GitHub", "six colour themes")) {
            assertTrue(part, AboutText.DESCRIPTION.contains(part))
        }
        // It names a version. When the version moves on, the sentence must be looked at again.
        val installed = context.packageManager.getPackageInfo(context.packageName, 0).versionName
        assertTrue("the description says which version this is: ($installed)", AboutText.DESCRIPTION.contains("($installed)"))
    }

    @Test
    fun theOldPromiseIsGoneNowThatTheAppGoesOnline() {
        assertTrue(!AboutText.DESCRIPTION.contains("Everything stays"))
    }

    @Test
    fun theReadmeCarriesTheSameDescription() {
        val readme = File("../README.md").readText()
        assertTrue(readme.startsWith("# Fitaka\n"))
        assertTrue("README.md has the About card's description, word for word", readme.contains(AboutText.DESCRIPTION))
    }

    /**
     * Nothing that ships says the old name or the old tagline, apart from the notes that explain why a
     * stored name was kept, and one note that points at the owner's logo file by its file name.
     */
    @Test
    fun theOldNameAndTaglineAreGoneFromWhatShips() {
        val mayMentionTheOldName = setOf("AppName.kt", "SettingsRepository.kt", "BackupScreen.kt", "ic_launcher.xml")
        val shipped = File("src/main").walkTopDown().filter { it.isFile && it.extension in setOf("kt", "xml", "txt") }.toList()
        assertTrue("found only ${shipped.size} files to read", shipped.size > 100)
        val withOldName = shipped.filter { it.readText().contains("PitakaView") }.map { it.name }.toSet()
        assertEquals("files that say PitakaView", mayMentionTheOldName, withOldName)
        val withTagline = shipped.filter { it.readText().contains("one view", ignoreCase = true) }.map { it.name }
        assertEquals("files that carry the old tagline", emptyList<String>(), withTagline)
        // And where the old name is still said, it is in a note, never in words on a screen.
        for (file in shipped.filter { it.name in mayMentionTheOldName && it.extension == "kt" }) {
            for (line in file.readLines().filter { it.contains("PitakaView") }) {
                assertTrue("${file.name}: the old name outside a note: $line", line.trimStart().startsWith("*") || line.trimStart().startsWith("//"))
            }
        }
    }
}
