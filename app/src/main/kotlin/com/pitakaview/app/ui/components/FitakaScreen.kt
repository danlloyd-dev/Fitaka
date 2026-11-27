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
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
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
import com.pitakaview.app.ui.icons.FitakaIcons
import com.pitakaview.app.ui.theme.FitakaDimens
import com.pitakaview.app.ui.theme.FitakaTheme

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
fun FitakaScreen(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    showLogo: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    pinnedAction: (@Composable () -> Unit)? = null,
    /** An item of the list to bring into view: on a form, the first field with a problem after a refused Save. */
    reveal: RevealRequest? = null,
    /** Which cards stand above the page's first item; when it changes, a page still at its top stays there. */
    topCards: Any? = null,
    content: LazyListScope.() -> Unit,
) {
    val c = FitakaTheme.colors
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    var topBarHeight by remember { mutableStateOf(72.dp) }
    val tabBar: Dp = LocalBottomBarPadding.current
    val keyboard: Dp = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    // Without a footer the list itself must scroll clear of the tab bar or the keyboard, whichever is taller.
    val clearBelow = if (pinnedAction != null) 0.dp else maxOf(tabBar, keyboard)
    // The keys of the list's items in order, noted as the content declares them, so [reveal] can find its item.
    val keys = remember { ArrayList<Any?>() }
    // A card that comes in at the top of a page nobody has scrolled (Home's update banner, which arrives a
    // moment after Home opens) is shown there. Left alone, the list keeps its first item where it was, by
    // its key, and the new card lands above it, out of sight under the top bar (found by UpdateFlowTest).
    // Asked only when [topCards] changes: a request like this one stops any scroll under way, such as [reveal]'s.
    val atTop by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 } }
    val topCardsSeen = remember { arrayOf(topCards) }
    SideEffect {
        if (topCardsSeen[0] != topCards) {
            topCardsSeen[0] = topCards
            if (atTop) listState.requestScrollToItem(0)
        }
    }
    if (reveal != null) {
        LaunchedEffect(reveal.tick) {
            if (reveal.tick == 0) return@LaunchedEffect
            val index = keys.indexOf(reveal.key)
            if (index < 0) return@LaunchedEffect
            val layout = listState.layoutInfo
            val shown = layout.visibleItemsInfo.firstOrNull { it.index == index }
            // Offsets count from below the top bar's padding; the end is above the padding at the bottom.
            val whole = shown != null && shown.offset >= 0 && shown.offset + shown.size <= layout.viewportEndOffset - layout.afterContentPadding
            if (!whole) listState.animateScrollToItem(index)
        }
    }

    Column(modifier.fillMaxSize().background(c.background).glassyPage(skipTop = topBarHeight)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().glassSource(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topBarHeight + 8.dp, bottom = clearBelow + 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = {
                    keys.clear()
                    KeyNotingScope(this, keys).content()
                },
            )
            FitakaTopBar(
                title = title,
                subtitle = subtitle,
                navigationIcon = if (onBack != null) FitakaIcons.Back else null,
                onNavigationClick = onBack,
                showDivider = listState.canScrollBackward,
                showLogo = showLogo,
                onGlassyPage = true,
                actions = actions,
                modifier = Modifier.onSizeChanged { topBarHeight = with(density) { it.height.toDp() } },
            )
        }
        if (pinnedAction != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        val w = FitakaDimens.borderThin.toPx()
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
        color = FitakaTheme.colors.ink,
        // Marked as a heading, so a screen reader can jump from group to group.
        modifier = modifier.padding(top = 4.dp).semantics { heading() },
    )
}

/** Bring the list item with [key] into view; asked again each time [tick] goes up (0: not yet). */
data class RevealRequest(val key: Any, val tick: Int) {
    companion object {
        /** The first of [keys] that is not null (the fields in the order they are shown), or null when none is. */
        fun firstOf(tick: Int, vararg keys: Any?): RevealRequest? = keys.firstOrNull { it != null }?.let { RevealRequest(it, tick) }
    }
}

/** Passes everything on to the list, and notes each item's key in order. */
private class KeyNotingScope(private val list: LazyListScope, private val keys: MutableList<Any?>) : LazyListScope by list {
    override fun item(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) {
        keys += key
        list.item(key, contentType, content)
    }

    override fun items(
        count: Int,
        key: ((index: Int) -> Any)?,
        contentType: (index: Int) -> Any?,
        itemContent: @Composable LazyItemScope.(index: Int) -> Unit,
    ) {
        repeat(count) { keys += key?.invoke(it) }
        list.items(count, key, contentType, itemContent)
    }
}
