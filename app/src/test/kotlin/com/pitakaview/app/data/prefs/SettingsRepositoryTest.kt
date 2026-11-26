package com.pitakaview.app.data.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import com.pitakaview.app.domain.AppearanceSettings
import com.pitakaview.app.domain.DarkModePref
import com.pitakaview.app.domain.FontChoice
import com.pitakaview.app.domain.UiSize
import com.pitakaview.app.domain.UiStyle
import com.pitakaview.app.domain.ThemeId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    /** Opens a DataStore on [file]; cancel the returned job to close it (like the app process ending). */
    private fun open(file: File): Pair<SettingsRepository, Job> {
        val job = Job()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        return SettingsRepository(store) to job
    }

    @Test
    fun defaultsThenChoicesSurviveARestart() = runBlocking {
        val file = File(tmp.root, "settings.preferences_pb")

        val (first, firstJob) = open(file)
        assertEquals(AppearanceSettings(ThemeId.MANGO, DarkModePref.SYSTEM), first.appearance.first())
        first.setTheme(ThemeId.UBE)
        first.setDarkMode(DarkModePref.DARK)
        assertEquals(AppearanceSettings(ThemeId.UBE, DarkModePref.DARK), first.appearance.first())
        // The size, the font and the style are kept the same way; until chosen they are Large, Shantell Sans and Glossy.
        assertEquals(AppearanceSettings(ThemeId.UBE, DarkModePref.DARK, UiSize.LARGE, FontChoice.SHANTELL, UiStyle.GLOSSY), first.appearance.first())
        first.setUiSize(UiSize.SMALL)
        first.setFont(FontChoice.ITIM)
        first.setUiStyle(UiStyle.SKETCH)
        first.setTheme(ThemeId.ROSAS)
        firstJob.cancelAndJoin()

        val (second, secondJob) = open(file)
        assertEquals(AppearanceSettings(ThemeId.ROSAS, DarkModePref.DARK, UiSize.SMALL, FontChoice.ITIM, UiStyle.SKETCH), second.appearance.first())
        secondJob.cancelAndJoin()
    }

    @Test
    fun theLastUsedWalletIsRemembered() = runBlocking {
        val (repo, job) = open(File(tmp.root, "wallet.preferences_pb"))
        assertEquals(null, repo.lastWalletId.first())
        repo.setLastWalletId(7)
        assertEquals(7L, repo.lastWalletId.first())
        job.cancelAndJoin()
    }

    @Test
    fun theLastBackupTimeIsRemembered() = runBlocking {
        val (repo, job) = open(File(tmp.root, "backup.preferences_pb"))
        assertEquals(null, repo.lastBackupAt.first())
        val at = java.time.Instant.parse("2026-11-26T03:00:00Z")
        repo.setLastBackupAt(at)
        assertEquals(at, repo.lastBackupAt.first())
        job.cancelAndJoin()
    }

    @Test
    fun theReminderChoiceAndWhatWasAlreadyShownSurviveARestart() = runBlocking {
        val file = File(tmp.root, "reminders.preferences_pb")
        val at = java.time.Instant.parse("2026-11-27T00:02:00Z")

        val (first, firstJob) = open(file)
        assertEquals("off until the owner switches it on", false, first.remindersOn.first())
        assertEquals(null, first.lastReminderCheck.first())
        assertEquals(emptySet<String>(), first.remindersShown.first())
        first.setRemindersOn(true)
        first.recordReminderCheck(at, inBackground = true, shown = setOf("loan:1:2026-11-27:day", "payday:7:2026-11-27"))
        firstJob.cancelAndJoin()

        val (second, secondJob) = open(file)
        assertEquals(true, second.remindersOn.first())
        assertEquals(ReminderCheck(at, inBackground = true), second.lastReminderCheck.first())
        assertEquals(setOf("loan:1:2026-11-27:day", "payday:7:2026-11-27"), second.remindersShown.first())
        // The next check replaces the list in one write, which is how old keys are dropped.
        second.recordReminderCheck(at.plusSeconds(86_400), inBackground = false, shown = setOf("payday:7:2026-11-28"))
        assertEquals(setOf("payday:7:2026-11-28"), second.remindersShown.first())
        assertEquals(false, second.lastReminderCheck.first()?.inBackground)
        secondJob.cancelAndJoin()
    }

    @Test
    fun unknownStoredNamesFallBackToDefaults() = runBlocking {
        val file = File(tmp.root, "old.preferences_pb")
        val job = Job()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        store.edit {
            it[SettingsRepository.THEME] = "PAPAYA"
            it[SettingsRepository.DARK_MODE] = "SEPIA"
        }
        assertEquals(AppearanceSettings(ThemeId.MANGO, DarkModePref.SYSTEM), SettingsRepository(store).appearance.first())
        job.cancelAndJoin()
    }
}
