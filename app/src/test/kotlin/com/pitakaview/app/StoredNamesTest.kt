package com.pitakaview.app

import android.content.Context
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import com.pitakaview.app.data.db.FitakaDatabase
import com.pitakaview.app.data.prefs.SettingsRepository
import com.pitakaview.app.reminders.AndroidReminderNotifier
import com.pitakaview.app.reminders.ReminderIntents
import com.pitakaview.app.reminders.ReminderWorker
import com.pitakaview.app.data.db.FITAKA_DB_IDENTITY
import com.pitakaview.app.reminders.WorkManagerReminderAlarm
import com.pitakaview.app.ui.backup.backupFileName
import com.pitakaview.app.ui.backup.sheetFileName
import com.pitakaview.app.update.AndroidUpdateNotifier
import com.pitakaview.app.update.UpdateIntents
import com.pitakaview.app.update.UpdateWorker
import com.pitakaview.app.update.WorkManagerUpdateSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.LocalDate

/**
 * The names the app has put on phones, which stay as they were when it was called PitakaView (it became
 * Fitaka on 2026-11-27). Each one keys something that is already stored; under a new name the app would
 * not find it, or would make a second one beside it. Whoever changes one of these must first write the
 * code that carries the old thing over, and a test that it does.
 */
@RunWith(RobolectricTestRunner::class)
class StoredNamesTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** Android knows the app by this. Another id is another app: it installs beside the old one, empty. */
    @Test
    fun theApplicationIdIsTheOneOnThePhone() = assertEquals("com.pitakaview.app", context.packageName)

    /** The home-screen icon and a tapped reminder both point at this class by its full name. */
    @Test
    fun theScreenTheLauncherOpensKeepsItsFullName() = assertEquals("com.pitakaview.app.MainActivity", MainActivity::class.java.name)

    /** Every entry, wallet, goal and loan is in this file. Under another name the app opens an empty one. */
    @Test
    fun theDatabaseFileKeepsItsName() {
        assertEquals("pitakaview.db", FitakaDatabase.FILE_NAME)
        assertTrue(context.getDatabasePath(FitakaDatabase.FILE_NAME).path.endsWith("/databases/pitakaview.db"))
    }

    /**
     * The layout of that file, as Room fingerprints it. Room refuses to open a file whose fingerprint is
     * not the one the code expects, so version 1's must stay what it was on 2026-11-26; a changed layout
     * is a new version with a migration, never a changed version 1.
     */
    @Test
    fun theDatabaseLayoutOfVersionOneIsUnchanged() {
        val layout = File("schemas/com.pitakaview.app.data.db.FitakaDatabase/1.json").readText()
        assertTrue(layout.contains("\"identityHash\": \"dcffe3a34ad6eab4e0ceef41a2738b77\""))
        // And the fingerprint a restore checks a backup against is that same one.
        assertTrue(layout.contains("\"identityHash\": \"$FITAKA_DB_IDENTITY\""))
    }

    /** The theme, size, style, font, reminders and the app lock's switch. Under another name they all reset, and the lock turns off. */
    @Test
    fun theSettingsFileKeepsItsName() {
        assertEquals("settings", SettingsRepository.SETTINGS_FILE)
        assertTrue(context.preferencesDataStoreFile(SettingsRepository.SETTINGS_FILE).path.endsWith("/files/datastore/settings.preferences_pb"))
    }

    /**
     * The morning check is lined up under this name, and stored with the full name of the class that runs
     * it. Under another job name a second check would be lined up beside the first (two reminders); under
     * another class name the one already lined up could not be started.
     */
    @Test
    fun theReminderJobKeepsItsNameAndItsClass() {
        assertEquals("reminder-check", WorkManagerReminderAlarm.NAME)
        assertEquals("reminder-check", WorkManagerReminderAlarm.TAG)
        assertEquals("com.pitakaview.app.reminders.ReminderWorker", ReminderWorker::class.java.name)
    }

    /** The phone keeps the owner's choices for reminders (sound, importance) under the channel's id. Its visible name is "Reminders". */
    @Test
    fun theNotificationChannelKeepsItsId() = assertEquals("reminders", AndroidReminderNotifier.CHANNEL)

    /** A reminder already on the phone carries these labels; the app must still read them after an update. */
    @Test
    fun aReminderAlreadyShowingCanStillBeRead() {
        assertEquals("com.pitakaview.app.reminder.KIND", ReminderIntents.EXTRA_KIND)
        assertEquals("com.pitakaview.app.reminder.ID", ReminderIntents.EXTRA_ID)
    }

    /**
     * New in 1.1.0 (updates). The daily look is lined up under this name and stored with its class's full name;
     * the phone keeps the owner's choices for update notifications under the channel's id, and a notification
     * already showing carries the extra.
     */
    @Test
    fun theUpdatePartsKeepTheirNames() {
        assertEquals("update-check", WorkManagerUpdateSchedule.NAME)
        assertEquals("com.pitakaview.app.update.UpdateWorker", UpdateWorker::class.java.name)
        assertEquals("updates", AndroidUpdateNotifier.CHANNEL)
        assertEquals("com.pitakaview.app.update.OPEN", UpdateIntents.EXTRA_OPEN)
        // And it is not the reminders' name: one would replace the other.
        assertTrue(WorkManagerUpdateSchedule.NAME != WorkManagerReminderAlarm.NAME)
    }

    /** New files take the new name. The ending stays .pitaka, as on every backup made so far. */
    @Test
    fun savedFilesTakeTheNewNameAndBackupsKeepTheirEnding() {
        val day = LocalDate.of(2026, 11, 27)
        assertEquals("fitaka-backup-2026-11-27.pitaka", backupFileName(day))
        assertEquals("fitaka-entries-2026-11-27.csv", sheetFileName(day))
    }
}
