package com.pitakaview.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.pitakaview.app.domain.AppearanceSettings
import com.pitakaview.app.domain.DarkModePref
import com.pitakaview.app.domain.ThemeId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/** App settings in a small on-device DataStore file. A damaged or unreadable file falls back to defaults. */
class SettingsRepository(private val store: DataStore<Preferences>) {

    val appearance: Flow<AppearanceSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            AppearanceSettings(
                theme = prefs[THEME].toEnumOr(ThemeId.MANGO),
                darkMode = prefs[DARK_MODE].toEnumOr(DarkModePref.SYSTEM),
            )
        }
        .distinctUntilChanged()

    suspend fun setTheme(theme: ThemeId) {
        store.edit { it[THEME] = theme.name }
    }

    suspend fun setDarkMode(mode: DarkModePref) {
        store.edit { it[DARK_MODE] = mode.name }
    }

    companion object {
        internal val THEME = stringPreferencesKey("theme")
        internal val DARK_MODE = stringPreferencesKey("dark_mode")

        /** One instance per app (DataStore forbids two on the same file). */
        fun create(context: Context) = SettingsRepository(
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                produceFile = { context.preferencesDataStoreFile("settings") },
            ),
        )
    }
}

/** A stored name that no longer matches (e.g. a removed theme) falls back to [default]. */
private inline fun <reified E : Enum<E>> String?.toEnumOr(default: E): E =
    this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
