package com.pitakaview.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.pitakaview.app.domain.AppearanceSettings
import com.pitakaview.app.domain.DarkModePref
import com.pitakaview.app.domain.ThemeId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.Instant

/** One look for reminders: when it ran, and whether the app did it by itself in the background. */
data class ReminderCheck(val at: Instant, val inBackground: Boolean)

/** App settings in a small on-device DataStore file. A damaged or unreadable file falls back to defaults. */
class SettingsRepository(private val store: DataStore<Preferences>) {

    private val prefs: Flow<Preferences> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    val appearance: Flow<AppearanceSettings> = prefs
        .map {
            AppearanceSettings(
                theme = it[THEME].toEnumOr(ThemeId.MANGO),
                darkMode = it[DARK_MODE].toEnumOr(DarkModePref.SYSTEM),
            )
        }
        .distinctUntilChanged()

    /** The wallet used for the last saved entry; quick entry offers it first next time. */
    val lastWalletId: Flow<Long?> = prefs.map { it[LAST_WALLET] }.distinctUntilChanged()

    suspend fun setTheme(theme: ThemeId) {
        store.edit { it[THEME] = theme.name }
    }

    suspend fun setDarkMode(mode: DarkModePref) {
        store.edit { it[DARK_MODE] = mode.name }
    }

    suspend fun setLastWalletId(id: Long) {
        store.edit { it[LAST_WALLET] = id }
    }

    /** When the last backup file was written, or null if there has never been one. */
    val lastBackupAt: Flow<Instant?> = prefs.map { it[LAST_BACKUP]?.let(Instant::ofEpochMilli) }.distinctUntilChanged()

    suspend fun setLastBackupAt(at: Instant) {
        store.edit { it[LAST_BACKUP] = at.toEpochMilli() }
    }

    /** Whether the owner asked for notifications about paydays and payments due. Off until they say so. */
    val remindersOn: Flow<Boolean> = prefs.map { it[REMINDERS_ON] ?: false }.distinctUntilChanged()

    suspend fun setRemindersOn(on: Boolean) {
        store.edit { it[REMINDERS_ON] = on }
    }

    /** The last time reminders were looked for, or null if never. Shown in Settings as proof the check runs. */
    val lastReminderCheck: Flow<ReminderCheck?> = prefs
        .map { p -> p[REMINDER_CHECK]?.let { ReminderCheck(Instant.ofEpochMilli(it), p[REMINDER_CHECK_BACKGROUND] ?: false) } }
        .distinctUntilChanged()

    /** The keys of reminders already shown, so each is shown once. */
    val remindersShown: Flow<Set<String>> = prefs.map { it[REMINDERS_SHOWN] ?: emptySet() }.distinctUntilChanged()

    /** Records one finished check in a single write: when and how it ran, and everything shown so far. */
    suspend fun recordReminderCheck(at: Instant, inBackground: Boolean, shown: Set<String>) {
        store.edit {
            it[REMINDER_CHECK] = at.toEpochMilli()
            it[REMINDER_CHECK_BACKGROUND] = inBackground
            it[REMINDERS_SHOWN] = shown
        }
    }

    companion object {
        internal val THEME = stringPreferencesKey("theme")
        internal val DARK_MODE = stringPreferencesKey("dark_mode")
        internal val LAST_WALLET = longPreferencesKey("last_wallet_id")
        internal val LAST_BACKUP = longPreferencesKey("last_backup_at")
        internal val REMINDERS_ON = booleanPreferencesKey("reminders_on")
        internal val REMINDER_CHECK = longPreferencesKey("reminder_check_at")
        internal val REMINDER_CHECK_BACKGROUND = booleanPreferencesKey("reminder_check_in_background")
        internal val REMINDERS_SHOWN = stringSetPreferencesKey("reminders_shown")

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
