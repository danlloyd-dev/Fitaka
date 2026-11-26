package com.pitakaview.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pitakaview.app.ui.theme.PitakaDimens
import com.pitakaview.app.ui.theme.PitakaTheme

/** One outlined control split into equal parts; the selected part is filled with ink. */
@Composable
fun PitakaSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PitakaTheme.colors
    val shape = RoundedCornerShape(PitakaDimens.corner)
    val shadow = PitakaDimens.shadow
    Row(
        modifier = modifier
            .padding(end = shadow, bottom = shadow)
            .fillMaxWidth()
            .sketchBox(shape, c.card, c.ink, shadow = c.shadow, shadowOffset = shadow)
            .clip(shape)
            .selectableGroup(),
    ) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .background(if (isSelected) c.ink else c.card)
                    .then(if (isSelected) Modifier.gloss(Gloss.Segment, on = c.ink) else Modifier)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(index) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (isSelected) c.card else c.ink)
            }
        }
    }
}

/**
 * A pill for picking one of a few choices (a category, a wallet, a date). The selected pill is filled
 * with ink, so the choice shows by light/dark, never by hue alone.
 */
@Composable
fun PitakaChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PitakaTheme.colors
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .sketchBox(shape, fill = if (selected) c.ink else c.card, border = c.ink, borderWidth = PitakaDimens.borderThin, gloss = if (selected) Gloss.Chip else null)
            .clip(shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold),
            color = if (selected) c.card else c.ink,
            maxLines = 1,
        )
    }
}

/**
 * What a list shows when it has nothing yet: a short title, one line of help, and room above for the
 * mascot, which the main lists pass in.
 */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    mascot: (@Composable () -> Unit)? = null,
) {
    val c = PitakaTheme.colors
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (mascot != null) {
            mascot()
            Spacer(Modifier.height(8.dp))
        }
        Text(title, style = MaterialTheme.typography.titleSmall, color = c.ink, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = c.inkMuted, textAlign = TextAlign.Center)
    }
}

/** A short problem line under a field or a group of chips. The "!" carries the meaning, not colour. */
@Composable
fun ProblemText(text: String, modifier: Modifier = Modifier) {
    Text(
        "! $text",
        modifier = modifier.padding(start = 4.dp, top = 4.dp),
        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
        color = PitakaTheme.colors.ink,
    )
}
