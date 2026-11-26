package com.pitakaview.app.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.pitakaview.app.domain.AppearanceSettings
import com.pitakaview.app.domain.DarkModePref
import com.pitakaview.app.domain.ThemeId
import com.pitakaview.app.ui.components.LocalBottomBarPadding
import com.pitakaview.app.ui.components.LocalHazeState
import com.pitakaview.app.ui.components.PitakaBottomBar
import com.pitakaview.app.ui.gallery.MainTabs
import com.pitakaview.app.ui.home.HomeRoute
import com.pitakaview.app.ui.motion.PitakaMotion
import com.pitakaview.app.ui.placeholder.AddTransactionComingSoon
import com.pitakaview.app.ui.placeholder.SectionComingSoon
import com.pitakaview.app.ui.settings.SettingsScreen
import com.pitakaview.app.ui.theme.PitakaTheme
import com.pitakaview.app.ui.theme.Section
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.serialization.Serializable

@Serializable data object HomeKey : NavKey
@Serializable data class SectionKey(val section: Section) : NavKey
@Serializable data object SettingsKey : NavKey
@Serializable data class AddTransactionKey(val section: Section? = null) : NavKey

/** Tab index for a key (Home = 0, then the sections in order), or null for pushed screens. */
fun NavKey.tabIndex(): Int? = when (this) {
    HomeKey -> 0
    is SectionKey -> 1 + section.ordinal
    else -> null
}

/** Home stays at the root; picking a tab replaces whatever sits above it. */
fun NavBackStack<NavKey>.goToTab(index: Int) {
    val key: NavKey = if (index == 0) HomeKey else SectionKey(Section.entries[index - 1])
    if (lastOrNull() == key) return
    removeAll { it != HomeKey }
    if (key != HomeKey) add(key)
}

// Pushed screens slide in from the right over a slightly shifted, fading page; popping reverses it.
private val push: ContentTransform =
    (slideInHorizontally(tween(PitakaMotion.MEDIUM, easing = PitakaMotion.Emphasized)) { it / 3 } +
        fadeIn(tween(PitakaMotion.MEDIUM))) togetherWith
        (slideOutHorizontally(tween(PitakaMotion.MEDIUM, easing = PitakaMotion.Emphasized)) { -it / 10 } +
            fadeOut(tween(PitakaMotion.SHORT)))

private val pop: ContentTransform =
    (slideInHorizontally(tween(PitakaMotion.MEDIUM, easing = PitakaMotion.Emphasized)) { -it / 10 } +
        fadeIn(tween(PitakaMotion.MEDIUM))) togetherWith
        (slideOutHorizontally(tween(PitakaMotion.MEDIUM, easing = PitakaMotion.Exit)) { it / 3 } +
            fadeOut(tween(PitakaMotion.SHORT)))

// Tabs are peers, so they fade through instead of sliding.
private val fadeThrough: ContentTransform =
    fadeIn(tween(PitakaMotion.MEDIUM - 60, delayMillis = 60, easing = PitakaMotion.Emphasized)) togetherWith
        fadeOut(tween(PitakaMotion.SHORT - 50))

private val tabMetadata = NavDisplay.transitionSpec { fadeThrough } +
    NavDisplay.popTransitionSpec { fadeThrough } +
    NavDisplay.predictivePopTransitionSpec { _ -> fadeThrough }

@Composable
fun PitakaRoot(
    appearance: AppearanceSettings,
    onThemeChange: (ThemeId) -> Unit,
    onDarkModeChange: (DarkModePref) -> Unit,
) {
    val c = PitakaTheme.colors
    val backStack = rememberNavBackStack(HomeKey)
    val haze = rememberHazeState()
    val density = LocalDensity.current
    var barHeight by remember { mutableStateOf(0.dp) }
    val currentTab = backStack.lastOrNull()?.tabIndex()
    var lastTab by remember { mutableIntStateOf(0) }
    if (currentTab != null) lastTab = currentTab

    CompositionLocalProvider(
        LocalHazeState provides haze,
        LocalBottomBarPadding provides if (currentTab != null) barHeight else 0.dp,
    ) {
        Box(Modifier.fillMaxSize().background(c.background)) {
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                transitionSpec = { push },
                popTransitionSpec = { pop },
                predictivePopTransitionSpec = { pop },
                entryProvider = entryProvider {
                    entry<HomeKey>(metadata = tabMetadata) {
                        HomeRoute(
                            onOpenSection = { backStack.goToTab(1 + it.ordinal) },
                            onAddTransaction = { backStack.add(AddTransactionKey()) },
                            onOpenSettings = { backStack.add(SettingsKey) },
                        )
                    }
                    entry<SectionKey>(metadata = tabMetadata) { key ->
                        SectionComingSoon(key.section, onAddTransaction = { backStack.add(AddTransactionKey(key.section)) })
                    }
                    entry<SettingsKey> {
                        SettingsScreen(appearance, onThemeChange, onDarkModeChange, onBack = { backStack.removeLastOrNull() })
                    }
                    entry<AddTransactionKey> {
                        AddTransactionComingSoon(onBack = { backStack.removeLastOrNull() })
                    }
                },
            )
            AnimatedVisibility(
                visible = currentTab != null,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(tween(PitakaMotion.MEDIUM, easing = PitakaMotion.Emphasized)) { it } +
                    fadeIn(tween(PitakaMotion.MEDIUM)),
                exit = slideOutVertically(tween(PitakaMotion.SHORT, easing = PitakaMotion.Exit)) { it } +
                    fadeOut(tween(PitakaMotion.SHORT)),
            ) {
                PitakaBottomBar(
                    items = MainTabs,
                    selectedIndex = currentTab ?: lastTab,
                    onSelect = { backStack.goToTab(it) },
                    modifier = Modifier.onSizeChanged { barHeight = with(density) { it.height.toDp() } },
                )
            }
        }
    }
}
