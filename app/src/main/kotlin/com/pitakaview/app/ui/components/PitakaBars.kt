package com.pitakaview.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
            Text(title, style = type.headlineSmall, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // Full ink on glass: muted ink drops below 4.5:1 when dark content scrolls under (ThemeContrastTest).
            if (subtitle != null) Text(subtitle, style = type.bodyMedium, color = c.ink, maxLines = 1)
        }
        actions()
    }
}

data class BottomBarItem(val label: String, val icon: ImageVector, val section: Section? = null)

/**
 * Bottom navigation on frosted glass. The selected item's outlined pill (its section colour; Home uses
 * the page background) scales in, and every item keeps its text label, so selection never relies on colour.
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .glassBackground(c.card)
            .drawBehind {
                val y = PitakaDimens.border.toPx() / 2
                drawLine(c.ink, Offset(0f, y), Offset(size.width, y), strokeWidth = PitakaDimens.border.toPx())
            }
            .windowInsetsPadding(WindowInsets.navigationBars)
            .selectableGroup()
            .padding(horizontal = 4.dp, vertical = 8.dp),
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            val shown by animateFloatAsState(
                targetValue = if (selected) 1f else 0f,
                animationSpec = tween(PitakaMotion.MEDIUM, easing = PitakaMotion.Emphasized),
                label = "pill",
            )
            val iconTint by animateColorAsState(
                targetValue = when {
                    !selected -> c.inkMuted
                    item.section != null -> c.onSectionFill
                    else -> c.ink
                },
                animationSpec = tween(PitakaMotion.MEDIUM),
                label = "icon",
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) })
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(Modifier.size(width = 52.dp, height = 32.dp), contentAlignment = Alignment.Center) {
                    if (shown > 0f) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .graphicsLayer {
                                    scaleX = 0.6f + 0.4f * shown
                                    scaleY = 0.6f + 0.4f * shown
                                    alpha = shown
                                }
                                .sketchBox(
                                    RoundedCornerShape(16.dp),
                                    fill = item.section?.let(c::fill) ?: c.background,
                                    border = c.ink,
                                    borderWidth = PitakaDimens.borderThin,
                                ),
                        )
                    }
                    Icon(item.icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
                }
                // Labels are text on glass, so always full ink; the selected one is bolder (and has the pill).
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
