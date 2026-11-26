package com.pitakaview.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pitakaview.app.domain.AppearanceSettings
import com.pitakaview.app.domain.DarkModePref
import com.pitakaview.app.domain.ThemeId
import com.pitakaview.app.ui.components.AmountText
import com.pitakaview.app.ui.components.DarkModePicker
import com.pitakaview.app.ui.components.PitakaBottomBar
import com.pitakaview.app.ui.components.PitakaButton
import com.pitakaview.app.ui.components.PitakaButtonStyle
import com.pitakaview.app.ui.components.PitakaCard
import com.pitakaview.app.ui.components.PitakaDivider
import com.pitakaview.app.ui.components.PitakaIconButton
import com.pitakaview.app.ui.components.PitakaListItem
import com.pitakaview.app.ui.components.PitakaTextField
import com.pitakaview.app.ui.components.PitakaTopBar
import com.pitakaview.app.ui.components.SectionBadge
import com.pitakaview.app.ui.components.SectionTag
import com.pitakaview.app.ui.components.ThemePicker
import com.pitakaview.app.ui.icons.PitakaIcons
import com.pitakaview.app.ui.icons.icon
import com.pitakaview.app.ui.navigation.MainTabs
import com.pitakaview.app.ui.theme.PitakaTheme
import com.pitakaview.app.ui.theme.Section

/** Test fixture: every component on one screen, drawn by ThemeScreenshotTest in each theme and mode. */
@Composable
fun ComponentGallery(
    appearance: AppearanceSettings,
    onThemeChange: (ThemeId) -> Unit,
    onDarkModeChange: (DarkModePref) -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
) {
    val c = PitakaTheme.colors
    val type = MaterialTheme.typography
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val amount = rememberTextFieldState("1,250.00")
    val note = rememberTextFieldState()
    val badAmount = rememberTextFieldState("0")

    Column(modifier.fillMaxSize().background(c.background)) {
        PitakaTopBar(
            title = "PitakaView",
            subtitle = "Your whole wallet, one view.",
            actions = { PitakaIconButton(PitakaIcons.Settings, "Settings", onClick = {}) },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GroupLabel("Theme")
            ThemePicker(appearance.theme, onThemeChange)
            DarkModePicker(appearance.darkMode, onDarkModeChange)

            GroupLabel("Buttons")
            PitakaButton(
                "Add transaction",
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                style = PitakaButtonStyle.Primary,
                icon = PitakaIcons.Add,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PitakaButton("View all", onClick = {}, modifier = Modifier.weight(1f))
                PitakaButton("Disabled", onClick = {}, modifier = Modifier.weight(1f), enabled = false)
            }

            GroupLabel("Cards")
            PitakaCard(Modifier.fillMaxWidth()) {
                Text("Natitira this month", style = type.labelLarge, color = c.inkMuted)
                AmountText(1_234_550, style = type.displaySmall)
                Text("after ₱8,765.50 gastos", style = type.bodyMedium, color = c.inkMuted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionCard(Section.KITA, 2_000_000, Modifier.weight(1f))
                SectionCard(Section.GASTOS, 876_550, Modifier.weight(1f))
            }

            GroupLabel("Text fields")
            PitakaTextField(
                state = amount,
                label = "Amount",
                prefix = "₱",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            PitakaTextField(state = note, label = "Note", placeholder = "e.g. Jollibee with the barkada")
            PitakaTextField(
                state = badAmount,
                label = "Amount",
                prefix = "₱",
                isError = true,
                supportingText = "Enter an amount above ₱0.00",
            )

            GroupLabel("List")
            PitakaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                PitakaListItem(
                    "Food",
                    subtitle = "Gastos · GCash · Today",
                    leading = { SectionBadge(Section.GASTOS, Section.GASTOS.icon) },
                    trailing = { AmountText(-12_500, signed = true) },
                )
                PitakaDivider()
                PitakaListItem(
                    "Salary",
                    subtitle = "Kita · MariBank · Nov 15",
                    leading = { SectionBadge(Section.KITA, Section.KITA.icon) },
                    trailing = { AmountText(2_000_000, signed = true) },
                )
                PitakaDivider()
                PitakaListItem(
                    "Kuya Jun",
                    subtitle = "Utang · owes you · due Dec 15",
                    leading = { SectionBadge(Section.UTANG, Section.UTANG.icon) },
                    trailing = { AmountText(200_000) },
                )
            }

            GroupLabel("Tags")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Section.entries.forEach { SectionTag(it.label, it) }
            }
            Spacer(Modifier.height(8.dp))
        }
        PitakaBottomBar(items = MainTabs, selectedIndex = tab, onSelect = { tab = it })
    }
}

@Composable
private fun SectionCard(section: Section, centavos: Long, modifier: Modifier = Modifier) {
    PitakaCard(modifier, section = section) {
        SectionBadge(section, section.icon, size = 36.dp)
        Spacer(Modifier.height(8.dp))
        Text(section.label, style = MaterialTheme.typography.titleSmall, color = PitakaTheme.colors.ink)
        AmountText(centavos)
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = PitakaTheme.colors.ink, modifier = Modifier.padding(top = 4.dp))
}
