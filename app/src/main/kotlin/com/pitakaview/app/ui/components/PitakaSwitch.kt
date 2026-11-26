package com.pitakaview.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pitakaview.app.ui.motion.PitakaMotion
import com.pitakaview.app.ui.theme.PitakaDimens
import com.pitakaview.app.ui.theme.PitakaTheme

private val TrackWidth = 52.dp
private val TrackHeight = 30.dp
private val Knob = 18.dp
private val KnobInset = 6.dp

/**
 * The on/off knob by itself, drawn like the rest of the app: an ink outline with a round knob that
 * slides. On is a filled track with the knob on the right, so it reads without relying on colour.
 * It is only the picture; [PitakaToggleRow] is the thing to tap.
 */
@Composable
fun PitakaSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    val c = PitakaTheme.colors
    val knobAt by animateDpAsState(
        targetValue = if (checked) TrackWidth - Knob - KnobInset else KnobInset,
        animationSpec = tween(PitakaMotion.SHORT, easing = PitakaMotion.Emphasized),
        label = "knob",
    )
    Box(
        modifier = modifier
            .size(TrackWidth, TrackHeight)
            .sketchBox(RoundedCornerShape(50), fill = if (checked) c.ink else c.card, border = c.ink, borderWidth = PitakaDimens.borderThin),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.offset(x = knobAt).size(Knob).background(if (checked) c.card else c.ink, CircleShape))
    }
}

/**
 * A setting that is on or off: its name, an optional line of explanation, and the switch. The whole
 * row is the tap target and is announced as one switch.
 */
@Composable
fun PitakaToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val c = PitakaTheme.colors
    val type = MaterialTheme.typography
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .heightIn(min = 56.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = type.titleSmall, color = c.ink)
            if (subtitle != null) Text(subtitle, style = type.bodyMedium, color = c.inkMuted)
        }
        Spacer(Modifier.width(12.dp))
        PitakaSwitch(checked)
    }
}
