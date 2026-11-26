package com.pitakaview.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pitakaview.app.ui.icons.sizeFor
import com.pitakaview.app.ui.theme.ButtonLabelStyle
import com.pitakaview.app.ui.theme.PitakaDimens
import com.pitakaview.app.ui.theme.PitakaTheme

enum class PitakaButtonStyle {
    /** The one main action on a screen (e.g. "Add transaction"). */
    Primary,

    /** Everything else. */
    Secondary,
}

/**
 * A chunky outlined button with a solid offset shadow. Pressing pushes it down into its shadow.
 * Defaults to [PitakaButtonStyle.Secondary]: primary colour is opt-in, for the main action only.
 */
@Composable
fun PitakaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PitakaButtonStyle = PitakaButtonStyle.Secondary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val c = PitakaTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shadow = PitakaDimens.shadow
    val push by animateDpAsState(if (pressed && enabled) shadow else 0.dp, tween(durationMillis = 70), label = "push")
    val fill = if (style == PitakaButtonStyle.Primary) c.primary else c.card
    val content = if (style == PitakaButtonStyle.Primary) c.onPrimary else c.ink

    Box(
        modifier = modifier
            .padding(end = shadow, bottom = shadow)
            .offset(x = push, y = push)
            .alpha(if (enabled) 1f else 0.45f)
            .sketchBox(
                shape = RoundedCornerShape(PitakaDimens.corner),
                fill = fill,
                border = c.ink,
                shadow = c.shadow,
                shadowOffset = if (enabled) shadow - push else 0.dp,
                gloss = Gloss.Button,
                glass = style != PitakaButtonStyle.Primary,
            )
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(icon.sizeFor(22.dp, more = 2.dp)))
            Text(text, style = ButtonLabelStyle.copy(fontFamily = MaterialTheme.typography.labelLarge.fontFamily), color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * A round outlined icon button (48dp touch target) for top bars and small actions. Its fill is a pale
 * share of the primary colour, not the card colour: gloss on white would show nothing.
 */
@Composable
fun PitakaIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val c = PitakaTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shadow = 3.dp
    val push by animateDpAsState(if (pressed) shadow else 0.dp, tween(durationMillis = 70), label = "push")
    Box(
        modifier = modifier
            .padding(end = shadow, bottom = shadow)
            .offset(x = push, y = push)
            .size(size)
            .sketchBox(CircleShape, c.iconButton, c.ink, PitakaDimens.borderThin, c.shadow, shadow - push, gloss = Gloss.RoundButton, glass = true)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = c.ink, modifier = Modifier.size(icon.sizeFor(22.dp, more = 2.dp)))
    }
}
