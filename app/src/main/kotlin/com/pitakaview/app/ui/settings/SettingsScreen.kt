package com.pitakaview.app.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pitakaview.app.domain.AppearanceSettings
import com.pitakaview.app.domain.DarkModePref
import com.pitakaview.app.domain.ThemeId
import com.pitakaview.app.ui.components.DarkModePicker
import com.pitakaview.app.ui.components.PitakaCard
import com.pitakaview.app.ui.components.PitakaDivider
import com.pitakaview.app.ui.components.PitakaListItem
import com.pitakaview.app.ui.components.PitakaScreen
import com.pitakaview.app.ui.components.SectionHeader
import com.pitakaview.app.ui.components.ThemePicker
import com.pitakaview.app.ui.icons.PitakaIcons
import com.pitakaview.app.ui.theme.PitakaTheme

/** Settings: money set-up (wallets, categories) and the look (theme, light/dark). Reminders arrive in M6. */
@Composable
fun SettingsScreen(
    appearance: AppearanceSettings,
    onThemeChange: (ThemeId) -> Unit,
    onDarkModeChange: (DarkModePref) -> Unit,
    onBack: () -> Unit,
    onOpenWallets: () -> Unit = {},
    onOpenCategories: () -> Unit = {},
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
            }
        }
        item(key = "theme-title") { SectionHeader("Theme") }
        item(key = "theme") { ThemePicker(appearance.theme, onThemeChange) }
        item(key = "mode-title") { SectionHeader("Light or dark") }
        item(key = "mode") { DarkModePicker(appearance.darkMode, onDarkModeChange) }
        item(key = "about-title") { SectionHeader("About") }
        item(key = "about") {
            PitakaCard(Modifier.fillMaxWidth()) {
                Text("PitakaView 0.1.0", style = type.titleSmall, color = c.ink)
                Text("Your whole wallet, one view.", style = type.bodyMedium, color = c.inkMuted)
                Text("All your data stays on this phone.", style = type.bodyMedium, color = c.inkMuted)
                Text("Font: Mali, SIL Open Font License 1.1", style = type.bodySmall, color = c.inkMuted)
            }
        }
    }
}
