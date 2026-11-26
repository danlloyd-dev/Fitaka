package com.pitakaview.app.data.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import com.pitakaview.app.domain.AppearanceSettings
import com.pitakaview.app.domain.DarkModePref
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
        firstJob.cancelAndJoin()

        val (second, secondJob) = open(file)
        assertEquals(AppearanceSettings(ThemeId.UBE, DarkModePref.DARK), second.appearance.first())
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
