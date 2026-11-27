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
import com.pitakaview.app.domain.FontChoice
import com.pitakaview.app.domain.UiSize
import com.pitakaview.app.domain.UiStyle
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
                size = it[UI_SIZE].toEnumOr(UiSize.LARGE),
                font = it[FONT].toEnumOr(FontChoice.SHANTELL),
                style = it[UI_STYLE].toEnumOr(UiStyle.GLOSSY),
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

    suspend fun setUiSize(size: UiSize) {
        store.edit { it[UI_SIZE] = size.name }
    }

    suspend fun setFont(font: FontChoice) {
        store.edit { it[FONT] = font.name }
    }

    suspend fun setUiStyle(style: UiStyle) {
        store.edit { it[UI_STYLE] = style.name }
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

    /** Whether the first-run welcome has been finished or skipped. It is shown at most once. */
    val onboardingDone: Flow<Boolean> = prefs.map { it[ONBOARDING_DONE] ?: false }.distinctUntilChanged()

    suspend fun setOnboardingDone() {
        store.edit { it[ONBOARDING_DONE] = true }
    }

    /** Whether opening the app asks for the phone's fingerprint, face or PIN. */
    val lockOn: Flow<Boolean> = prefs.map { it[LOCK_ON] ?: false }.distinctUntilChanged()

    suspend fun setLockOn(on: Boolean) {
        store.edit { it[LOCK_ON] = on }
    }

    /**
     * True from the moment a restore broke off after the database was closed until the owner has been
     * told. The app has to restart in between, so this is the only thing that survives to say what happened.
     */
    val restoreFailed: Flow<Boolean> = prefs.map { it[RESTORE_FAILED] ?: false }.distinctUntilChanged()

    suspend fun setRestoreFailed(failed: Boolean) {
        store.edit { if (failed) it[RESTORE_FAILED] = true else it.remove(RESTORE_FAILED) }
    }

    /** Whether the app asks GitHub for a new version (once a day, and when it opens). On until the owner switches it off. */
    val updateChecksOn: Flow<Boolean> = prefs.map { it[UPDATE_CHECKS_ON] ?: true }.distinctUntilChanged()

    /** Switching off also forgets a version found before, so nothing about updates shows any more. */
    suspend fun setUpdateChecksOn(on: Boolean) {
        store.edit {
            it[UPDATE_CHECKS_ON] = on
            if (!on) {
                it.remove(UPDATE_FOUND)
                it.remove(UPDATE_LATER)
                it.remove(UPDATE_NOTIFIED)
            }
        }
    }

    /** When GitHub last answered a look for a new version, or null if it never has. */
    val updateCheckedAt: Flow<Instant?> = prefs.map { it[UPDATE_CHECKED_AT]?.let(Instant::ofEpochMilli) }.distinctUntilChanged()

    /** A newer version GitHub said is out, in the form [com.pitakaview.app.update.ReleaseJson.toStored] writes; null when there is none. */
    val updateFound: Flow<String?> = prefs.map { it[UPDATE_FOUND] }.distinctUntilChanged()

    /** Records one answer from GitHub in a single write: when, and the newer version it named (null for none). */
    suspend fun recordUpdateCheck(at: Instant, found: String?) {
        store.edit {
            it[UPDATE_CHECKED_AT] = at.toEpochMilli()
            if (found != null) it[UPDATE_FOUND] = found else it.remove(UPDATE_FOUND)
        }
    }

    /** Which version's banner on Home was put off, and on which day ("v1.1.0 2026-11-27"). */
    val updateLater: Flow<String?> = prefs.map { it[UPDATE_LATER] }.distinctUntilChanged()

    suspend fun setUpdateLater(mark: String) {
        store.edit { it[UPDATE_LATER] = mark }
    }

    /** The tag of the version the phone's notifications last told about, so each version is told once. */
    val updateNotified: Flow<String?> = prefs.map { it[UPDATE_NOTIFIED] }.distinctUntilChanged()

    suspend fun setUpdateNotified(tag: String) {
        store.edit { it[UPDATE_NOTIFIED] = tag }
    }

    /**
     * The tag of the version the app handed to Android to install, until the new version has started: then
     * it tells the owner the update is done. It has to be stored, as installing ends the app that asked.
     */
    val updateInstalling: Flow<String?> = prefs.map { it[UPDATE_INSTALLING] }.distinctUntilChanged()

    suspend fun setUpdateInstalling(tag: String?) {
        store.edit { if (tag != null) it[UPDATE_INSTALLING] = tag else it.remove(UPDATE_INSTALLING) }
    }

    companion object {
        internal val UPDATE_CHECKS_ON = booleanPreferencesKey("update_checks_on")
        internal val UPDATE_CHECKED_AT = longPreferencesKey("update_checked_at")
        internal val UPDATE_FOUND = stringPreferencesKey("update_found")
        internal val UPDATE_LATER = stringPreferencesKey("update_later")
        internal val UPDATE_NOTIFIED = stringPreferencesKey("update_notified")
        internal val UPDATE_INSTALLING = stringPreferencesKey("update_installing")
        internal val RESTORE_FAILED = booleanPreferencesKey("restore_failed")
        internal val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        internal val LOCK_ON = booleanPreferencesKey("lock_on")
        internal val THEME = stringPreferencesKey("theme")
        internal val DARK_MODE = stringPreferencesKey("dark_mode")
        internal val UI_SIZE = stringPreferencesKey("ui_size")
        internal val FONT = stringPreferencesKey("font")
        internal val UI_STYLE = stringPreferencesKey("ui_style")
        internal val LAST_WALLET = longPreferencesKey("last_wallet_id")
        internal val LAST_BACKUP = longPreferencesKey("last_backup_at")
        internal val REMINDERS_ON = booleanPreferencesKey("reminders_on")
        internal val REMINDER_CHECK = longPreferencesKey("reminder_check_at")
        internal val REMINDER_CHECK_BACKGROUND = booleanPreferencesKey("reminder_check_in_background")
        internal val REMINDERS_SHOWN = stringSetPreferencesKey("reminders_shown")

        /**
         * The file every choice in Settings is kept in (files/datastore/settings.preferences_pb). It has been
         * on the owner's phone since the app was called PitakaView: under another name the theme, the
         * reminders and the app lock would all start again from nothing. StoredNamesTest holds it.
         */
        internal const val SETTINGS_FILE = "settings"

        /** One instance per app (DataStore forbids two on the same file). */
        fun create(context: Context) = SettingsRepository(
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                produceFile = { context.preferencesDataStoreFile(SETTINGS_FILE) },
            ),
        )
    }
}

/** A stored name that no longer matches (e.g. a removed theme) falls back to [default]. */
private inline fun <reified E : Enum<E>> String?.toEnumOr(default: E): E =
    this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
