package com.pitakaview.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pitakaview.app.ui.icons.PitakaIcons
import com.pitakaview.app.ui.theme.PitakaTheme

/** Height of the floating tab bar (0 when hidden), provided by the app root so lists can scroll clear of it. */
val LocalBottomBarPadding = compositionLocalOf { 0.dp }

/**
 * The standard screen: a list that scrolls under a frosted top bar (and under the tab bar, when shown).
 * The top bar gains an ink rule once content is underneath it.
 */
@Composable
fun PitakaScreen(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val c = PitakaTheme.colors
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    var topBarHeight by remember { mutableStateOf(72.dp) }
    val bottom: Dp = LocalBottomBarPadding.current
    Box(modifier.fillMaxSize().background(c.background)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().glassSource(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topBarHeight + 8.dp, bottom = bottom + 24.dp),
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
}

/** A heading between groups on a screen, e.g. "This month". */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = PitakaTheme.colors.ink,
        modifier = modifier.padding(top = 4.dp),
    )
}
