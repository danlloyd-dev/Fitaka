package com.pitakaview.app.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pitakaview.app.domain.DarkModePref
import com.pitakaview.app.domain.ThemeId
import com.pitakaview.app.ui.icons.PitakaIcons
import com.pitakaview.app.ui.theme.PitakaDimens
import com.pitakaview.app.ui.theme.PitakaTheme
import com.pitakaview.app.ui.theme.pitakaColors

/** Theme swatches, each drawn in its own colours for the current light/dark mode. */
@Composable
fun ThemePicker(selected: ThemeId, onSelect: (ThemeId) -> Unit, modifier: Modifier = Modifier) {
    val c = PitakaTheme.colors
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ThemeId.entries.forEach { theme ->
            val isSelected = theme == selected
            val swatch = pitakaColors(theme, c.isDark)
            val shadow = PitakaDimens.shadow
            Column(
                modifier = Modifier
                    .width(88.dp)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(theme) }),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .padding(end = shadow, bottom = shadow)
                        .fillMaxWidth()
                        .height(60.dp)
                        .sketchBox(
                            RoundedCornerShape(PitakaDimens.corner),
                            fill = swatch.background,
                            border = c.ink,
                            borderWidth = if (isSelected) 3.dp else PitakaDimens.borderThin,
                            shadow = c.shadow,
                            shadowOffset = if (isSelected) shadow else 0.dp,
                            gloss = Gloss.Swatch,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Dot(swatch.primary)
                        Dot(swatch.card)
                        Dot(swatch.ink)
                    }
                    if (isSelected) {
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp)
                                .sketchBox(CircleShape, c.ink, c.ink, 1.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(PitakaIcons.Check, contentDescription = null, tint = c.card, modifier = Modifier.size(14.dp))
                        }
                    }
                }
                Text(theme.label, style = MaterialTheme.typography.labelMedium, color = c.ink)
            }
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(Modifier.size(16.dp).sketchBox(CircleShape, color, PitakaTheme.colors.ink, 1.5.dp))
}

private val DarkModeOptions = listOf(DarkModePref.LIGHT to "Light", DarkModePref.DARK to "Dark", DarkModePref.SYSTEM to "System")

/** Light / Dark / Follow system. */
@Composable
fun DarkModePicker(selected: DarkModePref, onSelect: (DarkModePref) -> Unit, modifier: Modifier = Modifier) {
    PitakaSegmented(
        options = DarkModeOptions.map { it.second },
        selectedIndex = DarkModeOptions.indexOfFirst { it.first == selected },
        onSelect = { onSelect(DarkModeOptions[it].first) },
        modifier = modifier,
    )
}
