package com.pitakaview.app.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.pitakaview.app.data.db.FitakaDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Backup and restore through the real screens, on a phone-sized screen. Android's "save a file" and
 * "pick a file" screens belong to the system, so a stand-in answers them with [picked]; everything else
 * (the copy, the checks, the swap of the database file) is the real code.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h850dp-xhdpi")
class BackupFlowTest : FlowTestBase() {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var picked: File

    private val filePicker = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            dispatchResult(requestCode, Activity.RESULT_OK, Intent().setData(Uri.fromFile(picked)))
        }
    }

    @Composable
    override fun Surroundings(content: @Composable () -> Unit) {
        val owner = remember {
            object : ActivityResultRegistryOwner {
                override val activityResultRegistry: ActivityResultRegistry = filePicker
            }
        }
        CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner, content = content)
    }

    private fun openBackupScreen() {
        rule.onNodeWithContentDescription("Settings").performClick()
        tapButton("Backup")
        waitFor("Keep a copy of your data")
    }

    private fun entriesInDatabaseFile(): Long {
        val file = rule.activity.getDatabasePath(FitakaDatabase.FILE_NAME)
        return AndroidSQLiteDriver().open(file.absolutePath).use { connection ->
            connection.prepare("SELECT COUNT(*) FROM transactions").use { it.step(); it.getLong(0) }
        }
    }

    @Test
    fun backUpThenRestoreBringsTheBackedUpDataBack() {
        picked = File(tmp.root, "fitaka-backup.pitaka")
        var restarted = false
        start()
        container.restartApp = { restarted = true }
        addExpense(25_000) // one entry at backup time

        openBackupScreen()
        assertTrue(shows("Never backed up"))
        tapButton("Back up now")
        waitFor("Backup saved")
        waitFor("Last backup: Today")
        assertTrue("the backup file was written", picked.length() > 0)

        addExpense(99_900) // the phone's data moves on: two entries now
        tapButton("Restore from a backup")
        waitFor("Replace everything on this phone?")
        assertTrue("it says what the file holds", shows("The backup holds 1 entry in 1 wallet"))

        rule.onNode(hasText("Yes, restore") and hasClickAction()).assertIsDisplayed().performClick()
        // A plain flag does not make the test run the app's main thread; waitForIdle() does.
        rule.waitUntil(10_000) {
            rule.waitForIdle()
            restarted
        }
        assertEquals("the database file holds the backed-up state: one entry, not two", 1L, entriesInDatabaseFile())
    }

    /**
     * The same through the app's own screens: a backup the app made while it was called PitakaView (commit
     * c116ab9; made-up data: two wallets, one expense, one transfer), under the name that build gave its
     * backups, picked in Settings > Backup and restored.
     */
    @Test
    fun aBackupMadeWhenTheAppWasCalledPitakaViewStillRestores() {
        picked = File(tmp.root, "PitakaView-backup-2026-11-26.pitaka")
        File("src/test/resources/backups/made-by-pitakaview-0.1.0.backup").copyTo(picked)
        var restarted = false
        start()
        container.restartApp = { restarted = true }
        addExpense(99_900) // what is on the phone now: one entry, not the backup's two

        openBackupScreen()
        tapButton("Restore from a backup")
        waitFor("Replace everything on this phone?")
        assertTrue("it says what the old file holds", shows("The backup holds 2 entries in 2 wallets"))

        rule.onNode(hasText("Yes, restore") and hasClickAction()).assertIsDisplayed().performClick()
        rule.waitUntil(10_000) {
            rule.waitForIdle()
            restarted
        }
        assertEquals("the database file now holds the old backup's two entries", 2L, entriesInDatabaseFile())
    }

    @Test
    fun homeAsksForABackupUntilOneIsMade() {
        picked = File(tmp.root, "fitaka-backup.pitaka")
        start()
        assertFalse("an empty app is not nagged", shows("Back up your data"))

        addExpense(25_000)
        waitFor("Back up your data")
        // On a phone-sized screen the reminder is visible without scrolling, and it opens Backup.
        rule.onNode(hasText("Back up your data") and hasClickAction()).assertIsDisplayed().performClick()
        waitFor("Keep a copy of your data")
        tapButton("Back up now")
        waitFor("Backup saved")

        back()
        // "Your whole wallet" is at the top of Home, so it is on screen on any phone. (The "Recent" heading
        // this used to wait for is far down the page and went off-screen when Home gained the Summary link.)
        waitFor("Your whole wallet")
        rule.waitUntil(10_000) { !shows("Back up your data") }
    }

    @Test
    fun exportingASpreadsheetWritesEveryEntryAsATable() {
        picked = File(tmp.root, "fitaka-entries.csv")
        start()
        addExpense(25_000, note = "lunch, with Tita")
        openBackupScreen()
        rule.onNode(hasText("Export to a spreadsheet") and hasClickAction()).assertIsDisplayed().performClick()
        waitFor("Exported 1 entry.")

        val lines = picked.readText().removePrefix("\uFEFF").split("\r\n")
        assertEquals("Date,Type,Category,Amount,In or out,From wallet,To wallet,For,Note", lines[0])
        assertEquals("${today()},Spending,Food,250.00,out,Cash,,,\"lunch, with Tita\"", lines[1])
        assertTrue("exporting is not a backup, and the screen does not claim one", shows("Never backed up"))
    }

    @Test
    fun aFileThatIsNotABackupIsRefusedAndNothingChanges() {
        picked = File(tmp.root, "holiday.jpg").apply { writeBytes(ByteArray(2048) { 7 }) }
        start()
        addExpense(25_000)
        openBackupScreen()
        tapButton("Restore from a backup")
        waitFor("is not a Fitaka backup")
        assertFalse("no restore is offered", shows("Replace everything"))
        assertEquals(1, entries().size)
    }

    @Test
    fun cancellingARestoreKeepsEverything() {
        picked = File(tmp.root, "fitaka-backup.pitaka")
        var restarted = false
        start()
        container.restartApp = { restarted = true }
        openBackupScreen()
        tapButton("Back up now") // a backup of the empty app
        waitFor("Backup saved")

        addExpense(25_000)
        tapButton("Restore from a backup")
        waitFor("Replace everything on this phone?")
        tapButton("Cancel")
        rule.waitUntil(10_000) { !shows("Replace everything") }
        assertFalse(restarted)
        assertEquals("the entry made after the backup is still there", 1, entries().size)
    }
}
