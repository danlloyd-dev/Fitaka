package com.pitakaview.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.pitakaview.app.ui.motion.FitakaMotion
import com.pitakaview.app.ui.motion.FitakaSpring
import com.pitakaview.app.ui.motion.PRESS_SQUISH
import com.pitakaview.app.ui.motion.rememberPressDepth
import com.pitakaview.app.ui.theme.FitakaDimens
import com.pitakaview.app.ui.theme.FitakaTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/** One outlined control split into equal parts; the selected part is filled with ink. */
/** What tests find the choice strip's sliding dark piece by: it has no words of its own. */
internal const val CHOICE_PIECE_TAG = "ChoicePiece"

@Composable
fun FitakaSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = FitakaTheme.colors
    val shape = RoundedCornerShape(FitakaDimens.corner)
    val shadow = FitakaDimens.shadow
    // Where the dark piece is, counted in choices: it slides from one to the next, never jumps.
    val position by animateFloatAsState(selectedIndex.toFloat(), FitakaSpring.smooth(), label = "choice")
    val towardsEnd = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    Box(
        modifier = modifier
            .padding(end = shadow, bottom = shadow)
            .fillMaxWidth()
            .sketchBox(shape, c.card, c.ink, shadow = c.shadow, shadowOffset = shadow, glass = true)
            .clip(shape),
    ) {
        // The chosen one is a single dark piece, one choice wide, behind the words. Nothing else is painted:
        // the strip already has the card colour (or is glass).
        // Where the piece is, read while composing, at the same moment as the words below read it to change colour.
        val at = position
        Box(Modifier.matchParentSize()) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(1f / options.size)
                    // Placed, not redrawn: the piece itself is drawn once and moved as a layer.
                    .layout { measurable, constraints ->
                        val piece = measurable.measure(constraints)
                        layout(piece.width, piece.height) {
                            piece.placeWithLayer((at * piece.width * towardsEnd).roundToInt(), 0)
                        }
                    }
                    .testTag(CHOICE_PIECE_TAG)
                    .background(c.ink)
                    .gloss(Gloss.Segment, on = c.ink),
            )
        }
        Row(Modifier.selectableGroup()) {
            options.forEachIndexed { index, label ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .selectable(selected = index == selectedIndex, role = Role.RadioButton, onClick = { onSelect(index) }),
                    contentAlignment = Alignment.Center,
                ) {
                    // The words turn light as the dark piece arrives under them.
                    val covered = (1f - abs(position - index)).coerceIn(0f, 1f)
                    Text(label, style = MaterialTheme.typography.labelLarge, color = lerp(c.ink, c.card, covered))
                }
            }
        }
    }
}

/**
 * A pill for picking one of a few choices (a category, a wallet, a date). The selected pill is filled
 * with ink, so the choice shows by light/dark, never by hue alone.
 */
@Composable
fun FitakaChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** The words are drawn in this font, not the app's: the font choices in Settings each show themselves. */
    fontFamily: FontFamily? = null,
) {
    val c = FitakaTheme.colors
    val shape = RoundedCornerShape(50)
    val interaction = remember { MutableInteractionSource() }
    val depth by rememberPressDepth(interaction)
    val fill by animateColorAsState(if (selected) c.ink else c.card, tween(FitakaMotion.SHORT), label = "chip")
    val words by animateColorAsState(if (selected) c.card else c.ink, tween(FitakaMotion.SHORT), label = "chip words")
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            // No shadow to sink into, so it shrinks a touch while held. Read while drawing: nothing is laid out again.
            .graphicsLayer {
                val held = 1f - PRESS_SQUISH * depth
                scaleX = held
                scaleY = held
            }
            .sketchBox(shape, fill = fill, border = c.ink, borderWidth = FitakaDimens.borderThin, gloss = if (selected) Gloss.Chip else null, glass = !selected)
            .clip(shape)
            .selectable(selected = selected, interactionSource = interaction, indication = LocalIndication.current, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold)
                .let { if (fontFamily != null) it.copy(fontFamily = fontFamily) else it },
            color = words,
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
    val c = FitakaTheme.colors
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
        color = FitakaTheme.colors.ink,
    )
}
