package com.pitakaview.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pitakaview.app.ui.theme.PitakaDimens
import com.pitakaview.app.ui.theme.PitakaTheme
import com.pitakaview.app.ui.theme.Section

/**
 * A card with a thick outline and solid offset shadow. Pass [section] for a section-tinted background
 * (e.g. the Kita summary card), which also gets a streak of light along its top edge; otherwise it uses
 * the theme's card colour and stays plain.
 *
 * Small text on a tinted card takes [com.pitakaview.app.ui.theme.PitakaColors.captionOnTint], not muted ink.
 */
@Composable
fun PitakaCard(
    modifier: Modifier = Modifier,
    section: Section? = null,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = PitakaTheme.colors
    val shape = RoundedCornerShape(PitakaDimens.cornerLarge)
    val shadow = PitakaDimens.shadow
    Column(
        modifier = modifier
            .padding(end = shadow, bottom = shadow)
            .sketchBox(shape, fill = section?.let(c::tint) ?: c.card, border = c.ink, shadow = c.shadow, shadowOffset = shadow)
            .then(if (section != null) Modifier.tileShine(c.isDark) else Modifier)
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}
