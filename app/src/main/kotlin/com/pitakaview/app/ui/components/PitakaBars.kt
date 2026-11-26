package com.pitakaview.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.pitakaview.app.ui.icons.sizeFor
import com.pitakaview.app.ui.motion.PitakaMotion
import com.pitakaview.app.ui.theme.PitakaDimens
import com.pitakaview.app.ui.theme.PitakaTheme
import com.pitakaview.app.ui.theme.Section

/**
 * A top bar on frosted glass (content scrolls under it): optional back button, big handwritten title,
 * actions. [showDivider] draws an ink rule once content has scrolled beneath.
 */
@Composable
fun PitakaTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: ImageVector? = null,
    navigationLabel: String = "Back",
    onNavigationClick: (() -> Unit)? = null,
    showDivider: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = PitakaTheme.colors
    val type = MaterialTheme.typography
    val divider by animateFloatAsState(if (showDivider) 1f else 0f, tween(PitakaMotion.SHORT), label = "divider")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .glassBackground(c.background)
            .drawBehind {
                if (divider > 0f) {
                    val w = PitakaDimens.borderThin.toPx()
                    drawLine(c.ink.copy(alpha = divider), Offset(0f, size.height - w / 2), Offset(size.width, size.height - w / 2), w)
                }
            }
            .windowInsetsPadding(WindowInsets.statusBars)
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (navigationIcon != null && onNavigationClick != null) {
            PitakaIconButton(navigationIcon, navigationLabel, onNavigationClick)
        }
        Column(Modifier.weight(1f)) {
            // Marked as a heading, so a screen reader can jump from title to title.
            Text(
                title, style = type.headlineSmall, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            // Full ink on glass: muted ink drops below 4.5:1 when dark content scrolls under (ThemeContrastTest).
            if (subtitle != null) Text(subtitle, style = type.bodyMedium, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

data class BottomBarItem(val label: String, val icon: ImageVector, val section: Section? = null)

/** How far the chosen tab's tile stands above the row of dabs. */
private val TabLift = 10.dp
private val DabSize = DpSize(42.dp, 32.dp)
private val TileSize = DpSize(48.dp, 44.dp)

/** A dab of colour behind a tab's icon: an oval that is not quite regular, like one stroke of a marker. */
private val DabShape = GenericShape { size, _ ->
    addRoundRect(
        RoundRect(
            Rect(Offset.Zero, size),
            topLeft = CornerRadius(size.width * 0.47f, size.height * 0.56f),
            topRight = CornerRadius(size.width * 0.53f, size.height * 0.50f),
            bottomRight = CornerRadius(size.width * 0.50f, size.height * 0.50f),
            bottomLeft = CornerRadius(size.width * 0.50f, size.height * 0.44f),
        ),
    )
}

/**
 * Bottom navigation as a floating bar: outlined and shadowed like a card, clear of the screen's edges,
 * with the list scrolling behind and around it. Every section's icon sits on a dab of its own colour;
 * the section you are in jumps out of the top of the bar as a solid, outlined tile. Every item keeps its
 * text label, and the chosen one is bold, so selection never relies on colour.
 */
@Composable
fun PitakaBottomBar(
    items: List<BottomBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PitakaTheme.colors
    val type = MaterialTheme.typography
    val shadow = PitakaDimens.shadow
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            // In line with the cards above it. The top leaves room for the tile that stands above the bar.
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
    ) {
        Row(
            Modifier
                .padding(end = shadow, bottom = shadow)
                .fillMaxWidth()
                .sketchBox(RoundedCornerShape(24.dp), fill = c.card, border = c.ink, shadow = c.shadow, shadowOffset = shadow)
                .selectableGroup()
                .padding(start = 11.5.dp, end = 11.5.dp, top = 8.5.dp, bottom = 9.5.dp),
        ) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                val shown by animateFloatAsState(
                    targetValue = if (selected) 1f else 0f,
                    animationSpec = tween(PitakaMotion.MEDIUM, easing = PitakaMotion.Emphasized),
                    label = "tile",
                )
                val onTile = if (item.section != null) c.onSectionFill else c.onPrimary
                val iconTint by animateColorAsState(if (selected) onTile else c.ink, tween(PitakaMotion.MEDIUM), label = "icon")
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Box(Modifier.fillMaxWidth().height(DabSize.height), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .size(DabSize)
                                .graphicsLayer { alpha = 1f - shown }
                                .background(item.section?.let(c::tint) ?: c.homeTint, DabShape),
                        )
                        if (shown > 0f) {
                            // Taller than the dab and lifted, so its top stands above the bar and its
                            // shadow stays clear of the word below.
                            Box(
                                Modifier
                                    .requiredSize(TileSize)
                                    .offset { IntOffset(0, -(TabLift * shown).roundToPx()) }
                                    .graphicsLayer {
                                        scaleX = 0.7f + 0.3f * shown
                                        scaleY = 0.7f + 0.3f * shown
                                        alpha = shown
                                    }
                                    .sketchBox(
                                        RoundedCornerShape(16.dp),
                                        fill = item.section?.let(c::fill) ?: c.primary,
                                        border = c.ink,
                                        shadow = c.shadow,
                                        shadowOffset = 3.dp,
                                        gloss = Gloss.Tab,
                                    ),
                            )
                        }
                        Icon(
                            item.icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier
                                .offset { IntOffset(0, -(TabLift * shown).roundToPx()) }
                                .size(item.icon.sizeFor(22.dp, more = 2.dp)),
                        )
                    }
                    // Labels sit on the bar's own card colour, in full ink; the chosen one is bold.
                    Text(
                        item.label,
                        style = type.labelSmall.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
                        color = c.ink,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
