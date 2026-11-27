package com.pitakaview.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.pitakaview.app.R
import com.pitakaview.app.ui.icons.FitakaIcons
import com.pitakaview.app.ui.icons.sizeFor
import com.pitakaview.app.ui.motion.FitakaMotion
import com.pitakaview.app.ui.motion.FitakaSpring
import com.pitakaview.app.ui.theme.FitakaDimens
import com.pitakaview.app.ui.theme.FitakaTheme
import com.pitakaview.app.ui.theme.Section
import com.pitakaview.app.ui.update.LocalUpdateWaiting
import com.pitakaview.app.ui.update.UpdateDot

/**
 * A top bar on frosted glass (content scrolls under it): optional back button, big handwritten title,
 * actions. [showDivider] draws an ink rule once content has scrolled beneath.
 */
@Composable
fun FitakaTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: ImageVector? = null,
    navigationLabel: String = "Back",
    onNavigationClick: (() -> Unit)? = null,
    showDivider: Boolean = false,
    /** The app's logo at the far left, where a screen with no way back has room for it (Home). */
    showLogo: Boolean = false,
    /** The page under this bar carries the Glassy style's patches of colour ([glassyPage]), which then run on over the bar. */
    onGlassyPage: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = FitakaTheme.colors
    val type = MaterialTheme.typography
    val divider by animateFloatAsState(if (showDivider) 1f else 0f, tween(FitakaMotion.SHORT), label = "divider")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .glassBackground(c.background)
            .glassyBar(onGlassyPage)
            .drawBehind {
                if (divider > 0f) {
                    val w = FitakaDimens.borderThin.toPx()
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
            FitakaIconButton(navigationIcon, navigationLabel, onNavigationClick)
        } else if (showLogo) {
            FitakaLogo()
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

/** What tests find the logo by: it is a picture beside the app's name, so it has no words of its own. */
internal const val LOGO_TAG = "FitakaLogo"

/** The paper colour of the logo's own picture, so the tile around it has no seam. */
private val LogoPaper = Color(0xFFF9F1DE)

/**
 * The app's logo, the owner's drawing of the wallet, as a small outlined tile. It is the picture the
 * launcher icon uses (already in the app), drawn larger inside the tile: the launcher keeps the drawing
 * to the middle two thirds of its picture.
 */
@Composable
fun FitakaLogo(modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val c = FitakaTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .testTag(LOGO_TAG)
            .padding(end = 3.dp, bottom = 3.dp)
            .size(size)
            .sketchBox(shape, fill = LogoPaper, border = c.ink, borderWidth = FitakaDimens.borderThin, shadow = c.shadow, shadowOffset = 3.dp)
            .clip(shape),
    ) {
        Image(
            painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.45f; scaleY = 1.45f },
        )
    }
}

/**
 * The two round buttons at the top right of every main page (Home and the five sections), so their
 * headers are the same: the logo at the left, Search and Settings at the right.
 */
@Composable
fun MainPageActions(onSearch: () -> Unit, onOpenSettings: () -> Unit) {
    FitakaIconButton(FitakaIcons.Search, "Search", onSearch)
    // A newer version is waiting: a small red dot on the button's top right corner, and words to say so.
    val updateWaiting = LocalUpdateWaiting.current
    Box {
        FitakaIconButton(FitakaIcons.Settings, if (updateWaiting) SETTINGS_WITH_UPDATE else "Settings", onOpenSettings)
        if (updateWaiting) UpdateDot(Modifier.align(Alignment.TopEnd))
    }
}

/** What a screen reader says for the Settings button while a new version is waiting. */
const val SETTINGS_WITH_UPDATE = "Settings, a new version is out"

data class BottomBarItem(val label: String, val icon: ImageVector, val section: Section? = null)

/** How far the chosen tab's tile stands above the row of dabs. */
private val TabLift = 10.dp

/** Above the bar: the chosen tab's tile stands 7.5 above its top edge (16 above the dabs, which sit 8.5 down). */
private val BarHeadroom = 10.dp
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
 * Bottom navigation, fixed to the bottom of the screen: the card colour from edge to edge and on down under
 * the phone's own navigation bar, with an ink line along its top like the line under the top bar. Until
 * 2026-11-27 it was a floating card, clear of the screen's edges; the owner asked for it to sit on the
 * bottom. Every section's icon sits on a dab of its own colour; the section you are in jumps out of the top
 * of the bar as a solid, outlined tile. Every item keeps its text label, and the chosen one is bold, so
 * selection never relies on colour.
 */
@Composable
fun FitakaBottomBar(
    items: List<BottomBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = FitakaTheme.colors
    val type = MaterialTheme.typography
    Box(
        modifier
            .fillMaxWidth()
            // Room above the bar for the chosen tab's tile, which stands out of its top edge.
            .padding(top = BarHeadroom),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                // Drawn before the tabs: the chosen tab's tile covers the line where it crosses it.
                .drawBehind {
                    drawRect(c.card)
                    val line = FitakaDimens.border.toPx()
                    drawLine(c.ink, Offset(0f, line / 2), Offset(size.width, line / 2), line)
                }
                .selectableGroup()
                // The colour runs on under the phone's navigation bar; the tabs stay above it.
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 8.dp, end = 8.dp, top = 8.5.dp, bottom = 9.5.dp),
        ) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                val shown by animateFloatAsState(
                    targetValue = if (selected) 1f else 0f,
                    // On a spring: the tile pops up with one small bounce, and a second tap mid-way never jumps.
                    animationSpec = FitakaSpring.bouncy(),
                    label = "tile",
                )
                val onTile = if (item.section != null) c.onSectionFill else c.onPrimary
                val iconTint by animateColorAsState(if (selected) onTile else c.ink, tween(FitakaMotion.MEDIUM), label = "icon")
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
                                        alpha = shown.coerceIn(0f, 1f)
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
