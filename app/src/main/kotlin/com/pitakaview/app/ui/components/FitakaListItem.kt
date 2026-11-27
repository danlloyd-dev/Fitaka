package com.pitakaview.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pitakaview.app.domain.Peso
import com.pitakaview.app.ui.icons.sizeFor
import com.pitakaview.app.ui.theme.FitakaDimens
import com.pitakaview.app.ui.theme.FitakaTheme
import com.pitakaview.app.ui.theme.Section

/** One row in a list (usually inside a [FitakaCard]): leading badge, title + subtitle, trailing amount. */
@Composable
fun FitakaListItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = FitakaTheme.colors
    val type = MaterialTheme.typography
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = 64.dp)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(title, style = type.titleSmall, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = type.bodySmall, color = c.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing?.invoke()
    }
}

/** A section's icon on a solid section-colour square with an ink outline. */
@Composable
fun SectionBadge(section: Section, icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val c = FitakaTheme.colors
    Box(
        modifier = modifier
            .size(size)
            .sketchBox(RoundedCornerShape(12.dp), fill = c.fill(section), border = c.ink, borderWidth = FitakaDimens.borderThin, gloss = Gloss.Badge),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = c.onSectionFill, modifier = Modifier.size(icon.sizeFor(size * 0.55f)))
    }
}

/** A small pill label on a pale section tint, e.g. "Gastos". */
@Composable
fun SectionTag(text: String, section: Section, modifier: Modifier = Modifier) {
    val c = FitakaTheme.colors
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = c.ink,
        modifier = modifier
            .sketchBox(RoundedCornerShape(50), fill = c.tint(section), border = c.ink, borderWidth = FitakaDimens.borderThin)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

/** A peso amount. With [signed], positive amounts get "+" so direction never depends on colour. */
@Composable
fun AmountText(
    centavos: Long,
    modifier: Modifier = Modifier,
    signed: Boolean = false,
    style: TextStyle = MaterialTheme.typography.titleMedium,
) {
    val text = if (signed && centavos > 0) "+${Peso.format(centavos)}" else Peso.format(centavos)
    Text(text, modifier = modifier, style = style, color = FitakaTheme.colors.ink, maxLines = 1)
}

/** A hand-drawn-looking dashed divider for between list rows. */
@Composable
fun FitakaDivider(modifier: Modifier = Modifier) {
    val color = FitakaTheme.colors.ink.copy(alpha = 0.35f)
    Canvas(modifier.fillMaxWidth().height(2.dp)) {
        val y = size.height / 2
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx())),
        )
    }
}
