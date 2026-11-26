package com.pitakaview.app.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pitakaview.app.domain.AppearanceSettings
import com.pitakaview.app.domain.DarkModePref
import com.pitakaview.app.domain.ThemeId
import com.pitakaview.app.lock.LockSupport
import com.pitakaview.app.ui.components.DarkModePicker
import com.pitakaview.app.ui.components.PitakaCard
import com.pitakaview.app.ui.components.PitakaDivider
import com.pitakaview.app.ui.components.PitakaListItem
import com.pitakaview.app.ui.components.PitakaScreen
import com.pitakaview.app.ui.components.SectionHeader
import com.pitakaview.app.ui.components.ThemePicker
import com.pitakaview.app.ui.icons.PitakaIcons
import com.pitakaview.app.ui.theme.PitakaTheme

/** The version of the app that is installed, as Android knows it: never a number typed into the screen that can go stale. */
@Composable
fun rememberAppVersion(): String {
    val context = LocalContext.current
    return remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
}

/** Settings: money set-up (wallets, categories), reminders, and the look (theme, light/dark). */
@Composable
fun SettingsScreen(
    appearance: AppearanceSettings,
    onThemeChange: (ThemeId) -> Unit,
    onDarkModeChange: (DarkModePref) -> Unit,
    onBack: () -> Unit,
    onOpenWallets: () -> Unit = {},
    onOpenCategories: () -> Unit = {},
    onOpenBackup: () -> Unit = {},
    onOpenImport: () -> Unit = {},
    reminders: ReminderSettingsUi = ReminderSettingsUi(),
    onRemindersChange: (Boolean) -> Unit = {},
    onOpenPhoneSettings: () -> Unit = {},
    lock: LockSettingsUi = LockSettingsUi(),
    onLockChange: (Boolean) -> Unit = {},
    version: String = rememberAppVersion(),
) {
    val c = PitakaTheme.colors
    val type = MaterialTheme.typography
    PitakaScreen(title = "Settings", onBack = onBack) {
        item(key = "money-title") { SectionHeader("Money set-up") }
        item(key = "money") {
            PitakaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                PitakaListItem(
                    title = "Wallets",
                    subtitle = "Cash, GCash, bank accounts",
                    trailing = { Icon(PitakaIcons.ChevronRight, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(20.dp)) },
                    onClick = onOpenWallets,
                )
                PitakaDivider()
                PitakaListItem(
                    title = "Categories",
                    subtitle = "What you spend on and earn from",
                    trailing = { Icon(PitakaIcons.ChevronRight, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(20.dp)) },
                    onClick = onOpenCategories,
                )
                PitakaDivider()
                PitakaListItem(
                    title = "Backup",
                    subtitle = "Save a copy, bring one back, or export",
                    trailing = { Icon(PitakaIcons.ChevronRight, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(20.dp)) },
                    onClick = onOpenBackup,
                )
                PitakaDivider()
                PitakaListItem(
                    title = "Import",
                    subtitle = "Bring in entries from a file",
                    trailing = { Icon(PitakaIcons.ChevronRight, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(20.dp)) },
                    onClick = onOpenImport,
                )
            }
        }
        item(key = "reminders-title") { SectionHeader("Reminders") }
        item(key = "reminders") { RemindersCard(reminders, onRemindersChange, onOpenPhoneSettings) }
        // Not offered on a phone that cannot do it (before Android 10).
        if (lock.support != LockSupport.TOO_OLD) {
            item(key = "lock-title") { SectionHeader("App lock") }
            item(key = "lock") { LockCard(lock, onLockChange) }
        }
        item(key = "theme-title") { SectionHeader("Theme") }
        item(key = "theme") { ThemePicker(appearance.theme, onThemeChange) }
        item(key = "mode-title") { SectionHeader("Light or dark") }
        item(key = "mode") { DarkModePicker(appearance.darkMode, onDarkModeChange) }
        item(key = "about-title") { SectionHeader("About") }
        item(key = "about") {
            PitakaCard(Modifier.fillMaxWidth()) {
                Text("PitakaView $version", style = type.titleSmall, color = c.ink)
                Text("Your whole wallet, one view.", style = type.bodyMedium, color = c.inkMuted)
                Text("All your data stays on this phone.", style = type.bodyMedium, color = c.inkMuted)
                Text("Font: Mali, SIL Open Font License 1.1", style = type.bodySmall, color = c.inkMuted)
            }
        }
    }
}
