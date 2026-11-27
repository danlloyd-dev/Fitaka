package com.pitakaview.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pitakaview.app.domain.DarkModePref
import com.pitakaview.app.domain.FontChoice
import com.pitakaview.app.domain.ThemeId
import com.pitakaview.app.domain.UiSize
import com.pitakaview.app.domain.UiStyle
import com.pitakaview.app.ui.motion.FitakaSpring
import com.pitakaview.app.ui.theme.FitakaDimens
import com.pitakaview.app.ui.theme.FitakaTheme
import com.pitakaview.app.ui.theme.family
import com.pitakaview.app.ui.theme.fitakaColors

/** How far the chosen theme's swatch stands above the others. */
internal val SwatchLift = 6.dp
private val SwatchWidth = 88.dp
private val SwatchGap = 10.dp

/**
 * Theme swatches, each drawn in its own colours for the current light/dark mode. The chosen one pops
 * up: it rises on its shadow with a thicker outline. (It carried a tick badge until 2026-11-26; the
 * owner found it in the way.)
 */
@Composable
fun ThemePicker(selected: ThemeId, onSelect: (ThemeId) -> Unit, modifier: Modifier = Modifier) {
    val c = FitakaTheme.colors
    // The raised swatch is all that says which theme is on, so the row starts with it in view: two
    // swatches back from it, which leaves the first themes where they were and brings the last ones in.
    val step = with(LocalDensity.current) { (SwatchWidth + SwatchGap).roundToPx() }
    val scroll = rememberScrollState(initial = (ThemeId.entries.indexOf(selected) - 2).coerceAtLeast(0) * step)
    Row(
        modifier = modifier.horizontalScroll(scroll).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(SwatchGap),
    ) {
        ThemeId.entries.forEach { theme ->
            val isSelected = theme == selected
            val swatch = fitakaColors(theme, c.isDark)
            val shadow = FitakaDimens.shadow
            val rise by animateDpAsState(if (isSelected) SwatchLift else 0.dp, FitakaSpring.bouncy(), label = "swatch")
            Column(
                modifier = Modifier
                    .width(SwatchWidth)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(theme) }),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        // Room above for the chosen one to rise into, so the row does not jump as the choice moves.
                        .padding(top = SwatchLift, end = shadow, bottom = shadow)
                        .offset(y = -rise)
                        .fillMaxWidth()
                        .height(60.dp)
                        .sketchBox(
                            RoundedCornerShape(FitakaDimens.corner),
                            fill = swatch.background,
                            border = c.ink,
                            borderWidth = if (isSelected) 3.dp else FitakaDimens.borderThin,
                            shadow = c.shadow,
                            shadowOffset = (shadow * (rise / SwatchLift)).coerceAtLeast(0.dp),
                            gloss = Gloss.Swatch,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Dot(swatch.primary)
                        Dot(swatch.card)
                        Dot(swatch.ink)
                    }
                }
                Text(theme.label, style = MaterialTheme.typography.labelMedium, color = c.ink)
            }
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(Modifier.size(16.dp).sketchBox(CircleShape, color, FitakaTheme.colors.ink, 1.5.dp))
}

private val DarkModeOptions = listOf(DarkModePref.LIGHT to "Light", DarkModePref.DARK to "Dark", DarkModePref.SYSTEM to "System")

/** Light / Dark / Follow system. */
@Composable
fun DarkModePicker(selected: DarkModePref, onSelect: (DarkModePref) -> Unit, modifier: Modifier = Modifier) {
    FitakaSegmented(
        options = DarkModeOptions.map { it.second },
        selectedIndex = DarkModeOptions.indexOfFirst { it.first == selected },
        onSelect = { onSelect(DarkModeOptions[it].first) },
        modifier = modifier,
    )
}

/** Small / Medium / Large: how large everything but the bottom bar is drawn. */
@Composable
fun SizePicker(selected: UiSize, onSelect: (UiSize) -> Unit, modifier: Modifier = Modifier) {
    FitakaSegmented(
        options = UiSize.entries.map { it.label },
        selectedIndex = selected.ordinal,
        onSelect = { onSelect(UiSize.entries[it]) },
        modifier = modifier,
    )
}

/** Plain / Glossy / Glassy / Sketch: how the parts are finished. */
@Composable
fun StylePicker(selected: UiStyle, onSelect: (UiStyle) -> Unit, modifier: Modifier = Modifier) {
    FitakaSegmented(
        options = UiStyle.entries.map { it.label },
        selectedIndex = selected.ordinal,
        onSelect = { onSelect(UiStyle.entries[it]) },
        modifier = modifier,
    )
}

/** The five handwritten fonts, each name written in its own font. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FontPicker(selected: FontChoice, onSelect: (FontChoice) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FontChoice.entries.forEach { font ->
            FitakaChip(font.label, selected = font == selected, onClick = { onSelect(font) }, fontFamily = font.family())
        }
    }
}
