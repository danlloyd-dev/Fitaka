package com.pitakaview.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.pitakaview.app.domain.UiSize
import com.pitakaview.app.domain.UiStyle

/** The finish chosen in Settings; [com.pitakaview.app.ui.components.sketchBox] and the bars read it. */
val LocalUiStyle = staticCompositionLocalOf { UiStyle.GLOSSY }

/** The phone's own density, from before the size setting was applied. Null where no size was applied. */
val LocalFullSizeDensity = staticCompositionLocalOf<Density?> { null }

/**
 * Draws [content] at the size chosen in Settings. One thing does it all: a dp and an sp are made
 * smaller, so text, icons, buttons and the room between them shrink together and nothing has to know.
 * Large changes nothing.
 */
@Composable
fun ScaledUi(size: UiSize, content: @Composable () -> Unit) {
    val full = LocalDensity.current
    val scaled = remember(full, size) { Density(full.density * size.scale, full.fontScale) }
    CompositionLocalProvider(LocalFullSizeDensity provides full, LocalDensity provides scaled, content = content)
}

/** Draws [content] at the phone's own size inside [ScaledUi]: the bottom bar, which the size setting leaves alone. */
@Composable
fun FullSize(content: @Composable () -> Unit) {
    val full = LocalFullSizeDensity.current
    if (full == null) content() else CompositionLocalProvider(LocalDensity provides full, content = content)
}
