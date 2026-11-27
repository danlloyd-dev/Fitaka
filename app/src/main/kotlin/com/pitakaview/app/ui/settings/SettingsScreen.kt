package com.pitakaview.app.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pitakaview.app.APP_NAME
import com.pitakaview.app.domain.AppearanceSettings
import com.pitakaview.app.domain.DarkModePref
import com.pitakaview.app.domain.FontChoice
import com.pitakaview.app.domain.UiSize
import com.pitakaview.app.domain.UiStyle
import com.pitakaview.app.domain.ThemeId
import com.pitakaview.app.lock.LockSupport
import com.pitakaview.app.ui.components.DarkModePicker
import com.pitakaview.app.ui.components.FontPicker
import com.pitakaview.app.ui.components.SizePicker
import com.pitakaview.app.ui.components.StylePicker
import com.pitakaview.app.ui.components.FitakaCard
import com.pitakaview.app.ui.components.FitakaDivider
import com.pitakaview.app.ui.components.FitakaListItem
import com.pitakaview.app.ui.components.FitakaScreen
import com.pitakaview.app.ui.components.RevealRequest
import com.pitakaview.app.ui.components.SectionHeader
import com.pitakaview.app.ui.components.ThemePicker
import com.pitakaview.app.ui.icons.FitakaIcons
import com.pitakaview.app.ui.theme.FitakaTheme
import com.pitakaview.app.ui.update.UpdatesCard
import com.pitakaview.app.ui.update.UpdatesUi

/** The credit lines in the About card, here so a test can hold them to the licences' wording. */
object AboutText {
    /**
     * What the app is, in the owner's own words (2026-11-27). README.md carries the same words, and
     * AppNameTest keeps the two the same and the version in it true.
     */
    const val DESCRIPTION = "$APP_NAME (Financial + Pitaka) is an Android app I'm building to keep track of my money in pesos. " +
        "Like Lola's pitaka with a fold for every purpose, it sorts everything into five parts: Kita (income and paydays), " +
        "Baon (allowance), Ipon (savings and goals), Gastos (spending) and Utang (loans, both ways). " +
        "It also shows how much sits in each wallet, such as Cash, GCash, Maya and MariBank. " +
        "You can log an entry in a few taps, bring in entries from a GCash or MariBank statement, get reminders for paydays " +
        "and loan due dates, and see a monthly summary with a chart. " +
        "Your entries stay on your phone, with no account and no sign-up, plus an optional fingerprint lock and a backup file " +
        "you keep yourself. The app goes online only to ask GitHub for a new version, and you can turn that off. " +
        "It has a playful hand-drawn look with six colour themes in light and dark. " +
        "This version (1.2.0) updates itself from inside the app and knows loans paid twice a month, and feedback is very welcome."
    const val FONT = "Fonts: Shantell Sans, Mali, Pangolin, Fuzzy Bubbles, Itim. SIL Open Font License 1.1"
    const val ICONS = "Icons: Streamline Freehand (streamlinehq.com), CC BY 4.0, some redrawn"
}

/** The key of the Updates heading in Settings, which a tapped "new version" notification brings into view. */
const val UPDATES_TITLE_KEY = "updates-title"

/** The version of the app that is installed, as Android knows it: never a number typed into the screen that can go stale. */
@Composable
fun rememberAppVersion(): String {
    val context = LocalContext.current
    return remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
}

/** Settings: money set-up (wallets, categories), reminders, the look (theme, light/dark), and the app lock. */
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
    onSizeChange: (UiSize) -> Unit = {},
    onStyleChange: (UiStyle) -> Unit = {},
    onFontChange: (FontChoice) -> Unit = {},
    updates: UpdatesUi = UpdatesUi(installed = version),
    onUpdateChecksChange: (Boolean) -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    onUpdate: () -> Unit = {},
    onAllowInstalling: () -> Unit = {},
    onAllowUpdateNotifications: () -> Unit = {},
    /** Counts up each time the Updates card is to be brought into view (a tapped "new version" notification). */
    revealUpdates: Int = 0,
) {
    val c = FitakaTheme.colors
    val type = MaterialTheme.typography
    FitakaScreen(title = "Settings", onBack = onBack, reveal = if (revealUpdates > 0) RevealRequest(UPDATES_TITLE_KEY, revealUpdates) else null) {
        item(key = "money-title") { SectionHeader("Money set-up") }
        item(key = "money") {
            FitakaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                FitakaListItem(
                    title = "Wallets",
                    subtitle = "Cash, GCash, bank accounts",
                    trailing = { Icon(FitakaIcons.ChevronRight, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(20.dp)) },
                    onClick = onOpenWallets,
                )
                FitakaDivider()
                FitakaListItem(
                    title = "Categories",
                    subtitle = "What you spend on and earn from",
                    trailing = { Icon(FitakaIcons.ChevronRight, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(20.dp)) },
                    onClick = onOpenCategories,
                )
                FitakaDivider()
                FitakaListItem(
                    title = "Backup",
                    subtitle = "Save a copy, bring one back, or export",
                    trailing = { Icon(FitakaIcons.ChevronRight, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(20.dp)) },
                    onClick = onOpenBackup,
                )
                FitakaDivider()
                FitakaListItem(
                    title = "Import",
                    subtitle = "Bring in entries from a file",
                    trailing = { Icon(FitakaIcons.ChevronRight, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(20.dp)) },
                    onClick = onOpenImport,
                )
            }
        }
        item(key = "reminders-title") { SectionHeader("Reminders") }
        item(key = "reminders") { RemindersCard(reminders, onRemindersChange, onOpenPhoneSettings) }
        item(key = "theme-title") { SectionHeader("Theme") }
        item(key = "theme") { ThemePicker(appearance.theme, onThemeChange) }
        item(key = "mode-title") { SectionHeader("Light or dark") }
        item(key = "mode") { DarkModePicker(appearance.darkMode, onDarkModeChange) }
        item(key = "size-title") { SectionHeader("Size") }
        item(key = "size") { SizePicker(appearance.size, onSizeChange) }
        item(key = "style-title") { SectionHeader("Style") }
        item(key = "style") { StylePicker(appearance.style, onStyleChange) }
        item(key = "font-title") { SectionHeader("Font") }
        item(key = "font") { FontPicker(appearance.font, onFontChange) }
        // Kept well apart from the Reminders switch: with the two one above the other, a thumb aiming
        // at one could flip the other, and switching reminders off asks for nothing.
        // Not offered on a phone that cannot do it (before Android 10).
        if (lock.support != LockSupport.TOO_OLD) {
            item(key = "lock-title") { SectionHeader("App lock") }
            item(key = "lock") { LockCard(lock, onLockChange) }
        }
        // Beside About, which also says which version this is.
        item(key = UPDATES_TITLE_KEY) { SectionHeader("Updates") }
        item(key = "updates") {
            UpdatesCard(updates, onUpdateChecksChange, onCheckForUpdates, onUpdate, onAllowInstalling, onAllowUpdateNotifications)
        }
        item(key = "about-title") { SectionHeader("About") }
        item(key = "about") {
            FitakaCard(Modifier.fillMaxWidth()) {
                Text("$APP_NAME $version", style = type.titleSmall, color = c.ink)
                Text(AboutText.DESCRIPTION, style = type.bodyMedium, color = c.inkMuted)
                // A little air between the description and the small print under it.
                Spacer(Modifier.height(8.dp))
                Text(AboutText.FONT, style = type.bodySmall, color = c.inkMuted)
                // The icon set's licence (CC BY 4.0) asks for this line: who made them, where, and that some were changed.
                Text(AboutText.ICONS, style = type.bodySmall, color = c.inkMuted)
            }
        }
    }
}
