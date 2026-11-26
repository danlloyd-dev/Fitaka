package com.pitakaview.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pitakaview.app.ui.icons.PitakaIcons
import com.pitakaview.app.ui.theme.PitakaDimens
import com.pitakaview.app.ui.theme.PitakaTheme

/** Height of the floating tab bar (0 when hidden), provided by the app root so lists can scroll clear of it. */
val LocalBottomBarPadding = compositionLocalOf { 0.dp }

/**
 * The standard screen: a list that scrolls under a frosted top bar (and under the tab bar, when shown).
 * The top bar gains an ink rule once content is underneath it.
 *
 * [pinnedAction] is for forms: a footer that holds the main button (Save) and rides above the on-screen
 * keyboard. It is a real footer, not an overlay: the list ends where the footer begins, so no field or
 * button can ever sit hidden underneath it.
 */
@Composable
fun PitakaScreen(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    pinnedAction: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val c = PitakaTheme.colors
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    var topBarHeight by remember { mutableStateOf(72.dp) }
    val tabBar: Dp = LocalBottomBarPadding.current
    val keyboard: Dp = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    // Without a footer the list itself must scroll clear of the tab bar or the keyboard, whichever is taller.
    val clearBelow = if (pinnedAction != null) 0.dp else maxOf(tabBar, keyboard)

    Column(modifier.fillMaxSize().background(c.background)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().glassSource(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topBarHeight + 8.dp, bottom = clearBelow + 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
            PitakaTopBar(
                title = title,
                subtitle = subtitle,
                navigationIcon = if (onBack != null) PitakaIcons.Back else null,
                onNavigationClick = onBack,
                showDivider = listState.canScrollBackward,
                actions = actions,
                modifier = Modifier.onSizeChanged { topBarHeight = with(density) { it.height.toDp() } },
            )
        }
        if (pinnedAction != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        val w = PitakaDimens.borderThin.toPx()
                        drawLine(c.ink, Offset(0f, w / 2), Offset(size.width, w / 2), w)
                    }
                    .imePadding()
                    .navigationBarsPadding()
                    // Buttons reserve 4dp on the right and bottom for their shadow.
                    .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
            ) {
                pinnedAction()
            }
        }
    }
}

/** A heading between groups on a screen, e.g. "This month". */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = PitakaTheme.colors.ink,
        // Marked as a heading, so a screen reader can jump from group to group.
        modifier = modifier.padding(top = 4.dp).semantics { heading() },
    )
}
