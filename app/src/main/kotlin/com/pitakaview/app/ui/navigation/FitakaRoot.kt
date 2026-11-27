package com.pitakaview.app.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
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
import androidx.compose.runtime.LaunchedEffect
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
import com.pitakaview.app.domain.CategoryKind
import com.pitakaview.app.domain.DarkModePref
import com.pitakaview.app.domain.EntryKind
import com.pitakaview.app.domain.FontChoice
import com.pitakaview.app.domain.ReminderKind
import com.pitakaview.app.domain.ThemeId
import com.pitakaview.app.domain.TxPurpose
import com.pitakaview.app.domain.UiSize
import com.pitakaview.app.domain.UiStyle
import com.pitakaview.app.reminders.ReminderOpen
import com.pitakaview.app.ui.backup.BackupRoute
import com.pitakaview.app.ui.baon.BaonDetailRoute
import com.pitakaview.app.ui.baon.BaonEditorRoute
import com.pitakaview.app.ui.baon.BaonRoute
import com.pitakaview.app.ui.categories.CategoriesRoute
import com.pitakaview.app.ui.categories.CategoryEditorRoute
import com.pitakaview.app.ui.components.FitakaBottomBar
import com.pitakaview.app.ui.components.LocalBottomBarPadding
import com.pitakaview.app.ui.components.LocalHazeState
import com.pitakaview.app.ui.entry.EntryEditorRoute
import com.pitakaview.app.ui.gastos.GastosRoute
import com.pitakaview.app.ui.home.HomeRoute
import com.pitakaview.app.ui.importing.ImportRoute
import com.pitakaview.app.ui.ipon.GoalDetailRoute
import com.pitakaview.app.ui.ipon.GoalEditorRoute
import com.pitakaview.app.ui.ipon.IponRoute
import com.pitakaview.app.ui.kita.KitaRoute
import com.pitakaview.app.ui.kita.PayScheduleEditorRoute
import com.pitakaview.app.ui.motion.FitakaMotion
import com.pitakaview.app.ui.search.SearchRoute
import com.pitakaview.app.ui.sections.MoveEditorRoute
import com.pitakaview.app.ui.settings.SettingsScreen
import com.pitakaview.app.ui.settings.rememberLockSettings
import com.pitakaview.app.ui.settings.rememberReminderSettings
import com.pitakaview.app.ui.summary.SummaryRoute
import com.pitakaview.app.ui.theme.FitakaTheme
import com.pitakaview.app.ui.theme.FullSize
import com.pitakaview.app.ui.theme.Section
import com.pitakaview.app.ui.utang.LoanDetailRoute
import com.pitakaview.app.ui.utang.LoanEditorRoute
import com.pitakaview.app.ui.update.LocalUpdateWaiting
import com.pitakaview.app.ui.update.UpdateQuestionHost
import com.pitakaview.app.ui.update.rememberUpdateControls
import com.pitakaview.app.ui.utang.UtangRoute
import com.pitakaview.app.ui.wallets.WalletEditorRoute
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pitakaview.app.di.LocalAppContainer
import com.pitakaview.app.ui.wallets.WalletsRoute
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.serialization.Serializable

@Serializable data object HomeKey : NavKey
@Serializable data class SectionKey(val section: Section) : NavKey
@Serializable data object SettingsKey : NavKey

/**
 * Quick entry. No id = a new entry starting as [startKind]; an id = edit that entry.
 * With [payScheduleId], a new entry is the pay received for that payday, filled in from it.
 */
@Serializable data class EntryEditorKey(
    val transactionId: Long? = null,
    val startKind: EntryKind = EntryKind.EXPENSE,
    val payScheduleId: Long? = null,
    /** With [baonId], new spending starts charged to that baon. */
    val baonId: Long? = null,
) : NavKey
@Serializable data class LoanKey(val loanId: Long) : NavKey
@Serializable data class LoanEditorKey(val loanId: Long? = null) : NavKey
@Serializable data class GoalKey(val goalId: Long) : NavKey
@Serializable data class GoalEditorKey(val goalId: Long? = null) : NavKey
@Serializable data class BaonKey(val baonId: Long) : NavKey
@Serializable data class BaonEditorKey(val baonId: Long? = null) : NavKey

/** A new entry of [purpose] for the loan, goal or baon [linkId]. Existing ones open through [EntryEditorKey]. */
@Serializable data class MoveEditorKey(val purpose: TxPurpose, val linkId: Long) : NavKey
@Serializable data object SearchKey : NavKey
@Serializable data object SummaryKey : NavKey
@Serializable data class PayScheduleEditorKey(val scheduleId: Long? = null) : NavKey
@Serializable data object WalletsKey : NavKey
@Serializable data class WalletEditorKey(val walletId: Long? = null) : NavKey
@Serializable data object BackupKey : NavKey
@Serializable data object ImportKey : NavKey
@Serializable data object CategoriesKey : NavKey
@Serializable data class CategoryEditorKey(val categoryId: Long? = null, val kind: CategoryKind = CategoryKind.EXPENSE) : NavKey

/** Tab index for a key (Home = 0, then the sections in order), or null for pushed screens. */
fun NavKey.tabIndex(): Int? = when (this) {
    HomeKey -> 0
    is SectionKey -> 1 + section.ordinal
    else -> null
}

/** Home stays at the root; picking a tab replaces whatever sits above it. */
/** Opens [screen] on top, unless it is on top already: a second tap on the same button must not open it twice. */
fun NavBackStack<NavKey>.open(screen: NavKey) {
    if (lastOrNull() != screen) add(screen)
}

/**
 * Closes [screen] and only it: nothing happens unless it is the screen on top, and the first screen is
 * never closed. Every screen used to close "whatever is on top", so a second tap on Back while a screen
 * was on its way out closed the one under it too, and from Home that emptied the stack, which stops the
 * app (found in the review before 1.0 and proven by a test, 2026-11-27).
 */
fun NavBackStack<NavKey>.close(screen: NavKey) {
    if (size > 1 && last() == screen) removeAt(lastIndex)
}

fun NavBackStack<NavKey>.goToTab(index: Int) {
    val key: NavKey = if (index == 0) HomeKey else SectionKey(Section.entries[index - 1])
    if (lastOrNull() == key) return
    removeAll { it != HomeKey }
    if (key != HomeKey) add(key)
}

// Pushed screens slide in from the right over a slightly shifted, fading page; popping reverses it.
private val push: ContentTransform =
    (slideInHorizontally(tween(FitakaMotion.MEDIUM, easing = FitakaMotion.Emphasized)) { it / 3 } +
        fadeIn(tween(FitakaMotion.MEDIUM))) togetherWith
        (slideOutHorizontally(tween(FitakaMotion.MEDIUM, easing = FitakaMotion.Emphasized)) { -it / 10 } +
            fadeOut(tween(FitakaMotion.SHORT)))

private val pop: ContentTransform =
    (slideInHorizontally(tween(FitakaMotion.MEDIUM, easing = FitakaMotion.Emphasized)) { -it / 10 } +
        fadeIn(tween(FitakaMotion.MEDIUM))) togetherWith
        (slideOutHorizontally(tween(FitakaMotion.MEDIUM, easing = FitakaMotion.Exit)) { it / 3 } +
            fadeOut(tween(FitakaMotion.SHORT)))

// Tabs are peers, so they fade through instead of sliding.
private val fadeThrough: ContentTransform =
    (fadeIn(tween(FitakaMotion.MEDIUM - 60, delayMillis = 60, easing = FitakaMotion.Emphasized)) +
        // The screen coming in grows the last little bit, so a tab change has a direction: towards you.
        scaleIn(tween(FitakaMotion.MEDIUM - 60, delayMillis = 60, easing = FitakaMotion.Emphasized), initialScale = 0.97f)) togetherWith
        fadeOut(tween(FitakaMotion.SHORT - 50))

private val tabMetadata = NavDisplay.transitionSpec { fadeThrough } +
    NavDisplay.popTransitionSpec { fadeThrough } +
    NavDisplay.predictivePopTransitionSpec { _ -> fadeThrough }

@Composable
fun FitakaRoot(
    appearance: AppearanceSettings,
    onThemeChange: (ThemeId) -> Unit,
    onDarkModeChange: (DarkModePref) -> Unit,
    onSizeChange: (UiSize) -> Unit = {},
    onStyleChange: (UiStyle) -> Unit = {},
    onFontChange: (FontChoice) -> Unit = {},
    /** The screen a tapped reminder asked for; [onReminderOpened] is called once the app is there. */
    reminderToOpen: ReminderOpen? = null,
    onReminderOpened: () -> Unit = {},
    /** A tapped "new version" notification: open Settings at its Updates card; [onUpdatesOpened] once there. */
    openUpdates: Boolean = false,
    onUpdatesOpened: () -> Unit = {},
) {
    val c = FitakaTheme.colors
    val container = LocalAppContainer.current
    val backStack = rememberNavBackStack(HomeKey)
    // Whether a newer version is waiting: the Settings button on every main page then wears a red dot.
    val updateWaiting by container.updates.waiting.collectAsStateWithLifecycle(initialValue = null)
    var revealUpdates by remember { mutableIntStateOf(0) }
    LaunchedEffect(openUpdates) {
        if (!openUpdates) return@LaunchedEffect
        backStack.open(SettingsKey)
        revealUpdates++
        onUpdatesOpened()
    }
    val haze = rememberHazeState()
    val density = LocalDensity.current
    var barHeight by remember { mutableStateOf(0.dp) }
    val currentTab = backStack.lastOrNull()?.tabIndex()
    var lastTab by remember { mutableIntStateOf(0) }
    if (currentTab != null) lastTab = currentTab

    // A tapped reminder: go where it points, starting again from Home so Back behaves as it always does.
    LaunchedEffect(reminderToOpen) {
        val request = reminderToOpen ?: return@LaunchedEffect
        when (request.kind) {
            ReminderKind.PAYDAY -> backStack.goToTab(1 + Section.KITA.ordinal)
            ReminderKind.LOAN -> {
                backStack.goToTab(1 + Section.UTANG.ordinal)
                // A reminder can outlive its loan. The loan screen goes back by itself when its loan is
                // gone, which leaves the owner on the list of loans.
                backStack.open(LoanKey(request.rowId))
            }
        }
        onReminderOpened()
    }

    CompositionLocalProvider(
        LocalHazeState provides haze,
        LocalBottomBarPadding provides if (currentTab != null) barHeight else 0.dp,
        LocalUpdateWaiting provides (updateWaiting != null),
    ) {
        // Android's "Do you want to update this app?", when it asks after all, goes on screen from here.
        UpdateQuestionHost()
        Box(Modifier.fillMaxSize().background(c.background)) {
            NavDisplay(
                backStack = backStack,
                // The phone's own Back. Never the first screen: an empty stack stops the app.
                onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
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
                            onAddTransaction = { backStack.open(EntryEditorKey()) },
                            onOpenSettings = { backStack.open(SettingsKey) },
                            onOpenEntry = { backStack.open(EntryEditorKey(transactionId = it)) },
                            onOpenWallets = { backStack.open(WalletsKey) },
                            onOpenBackup = { backStack.open(BackupKey) },
                            onSearch = { backStack.open(SearchKey) },
                            onOpenSummary = { backStack.open(SummaryKey) },
                        )
                    }
                    entry<SummaryKey> { key ->
                        SummaryRoute(onBack = { backStack.close(key) })
                    }
                    entry<SearchKey> { key ->
                        SearchRoute(
                            onBack = { backStack.close(key) },
                            onOpenEntry = { backStack.open(EntryEditorKey(transactionId = it)) },
                        )
                    }
                    entry<PayScheduleEditorKey> { key ->
                        PayScheduleEditorRoute(key.scheduleId, onDone = { backStack.close(key) })
                    }
                    entry<LoanKey> { key ->
                        LoanDetailRoute(
                            key.loanId,
                            onBack = { backStack.close(key) },
                            onEdit = { backStack.open(LoanEditorKey(key.loanId)) },
                            onAddPayment = { backStack.open(MoveEditorKey(TxPurpose.LOAN_PAYMENT, key.loanId)) },
                            onOpenEntry = { backStack.open(EntryEditorKey(transactionId = it)) },
                        )
                    }
                    entry<LoanEditorKey> { key ->
                        LoanEditorRoute(key.loanId, onDone = { backStack.close(key) })
                    }
                    entry<GoalKey> { key ->
                        GoalDetailRoute(
                            key.goalId,
                            onBack = { backStack.close(key) },
                            onEdit = { backStack.open(GoalEditorKey(key.goalId)) },
                            onAdd = { backStack.open(MoveEditorKey(TxPurpose.SAVINGS_DEPOSIT, key.goalId)) },
                            onTakeOut = { backStack.open(MoveEditorKey(TxPurpose.SAVINGS_WITHDRAWAL, key.goalId)) },
                            onOpenEntry = { backStack.open(EntryEditorKey(transactionId = it)) },
                        )
                    }
                    entry<GoalEditorKey> { key ->
                        GoalEditorRoute(key.goalId, onDone = { backStack.close(key) })
                    }
                    entry<BaonKey> { key ->
                        BaonDetailRoute(
                            key.baonId,
                            onBack = { backStack.close(key) },
                            onEdit = { backStack.open(BaonEditorKey(key.baonId)) },
                            onReceived = { backStack.open(MoveEditorKey(TxPurpose.BAON_RECEIVED, key.baonId)) },
                            onSpend = { backStack.open(EntryEditorKey(startKind = EntryKind.EXPENSE, baonId = key.baonId)) },
                            onGive = { backStack.open(MoveEditorKey(TxPurpose.BAON_GIVEN, key.baonId)) },
                            onOpenEntry = { backStack.open(EntryEditorKey(transactionId = it)) },
                        )
                    }
                    entry<BaonEditorKey> { key ->
                        BaonEditorRoute(key.baonId, onDone = { backStack.close(key) })
                    }
                    entry<MoveEditorKey> { key ->
                        MoveEditorRoute(transactionId = null, purpose = key.purpose, linkId = key.linkId, onDone = { backStack.close(key) })
                    }
                    entry<BackupKey> { key ->
                        BackupRoute(onBack = { backStack.close(key) })
                    }
                    entry<ImportKey> { key ->
                        ImportRoute(onBack = { backStack.close(key) })
                    }
                    entry<SectionKey>(metadata = tabMetadata) { key ->
                        val openEntry: (Long) -> Unit = { backStack.open(EntryEditorKey(transactionId = it)) }
                        when (key.section) {
                            Section.GASTOS -> GastosRoute(
                                onAdd = { backStack.open(EntryEditorKey(startKind = EntryKind.EXPENSE)) },
                                onOpenEntry = openEntry,
                                onSearch = { backStack.open(SearchKey) },
                                onOpenSettings = { backStack.open(SettingsKey) },
                            )
                            Section.KITA -> KitaRoute(
                                onAdd = { backStack.open(EntryEditorKey(startKind = EntryKind.INCOME)) },
                                onOpenEntry = openEntry,
                                onSearch = { backStack.open(SearchKey) },
                                onOpenSettings = { backStack.open(SettingsKey) },
                                onAddPayday = { backStack.open(PayScheduleEditorKey()) },
                                onOpenPayday = { backStack.open(PayScheduleEditorKey(it)) },
                                onReceived = { backStack.open(EntryEditorKey(startKind = EntryKind.INCOME, payScheduleId = it)) },
                            )
                            Section.UTANG -> UtangRoute(
                                onAdd = { backStack.open(LoanEditorKey()) },
                                onOpen = { backStack.open(LoanKey(it)) },
                                onSearch = { backStack.open(SearchKey) },
                                onOpenSettings = { backStack.open(SettingsKey) },
                            )
                            Section.IPON -> IponRoute(
                                onAdd = { backStack.open(GoalEditorKey()) },
                                onOpen = { backStack.open(GoalKey(it)) },
                                onSearch = { backStack.open(SearchKey) },
                                onOpenSettings = { backStack.open(SettingsKey) },
                            )
                            Section.BAON -> BaonRoute(
                                onAdd = { backStack.open(BaonEditorKey()) },
                                onOpen = { backStack.open(BaonKey(it)) },
                                onReceived = { backStack.open(MoveEditorKey(TxPurpose.BAON_RECEIVED, it)) },
                                onSpend = { backStack.open(EntryEditorKey(startKind = EntryKind.EXPENSE, baonId = it)) },
                                onGive = { backStack.open(MoveEditorKey(TxPurpose.BAON_GIVEN, it)) },
                                onSearch = { backStack.open(SearchKey) },
                                onOpenSettings = { backStack.open(SettingsKey) },
                            )
                        }
                    }
                    entry<SettingsKey> { key ->
                        val reminders = rememberReminderSettings()
                        val lock = rememberLockSettings()
                        val update = rememberUpdateControls()
                        SettingsScreen(
                            appearance, onThemeChange, onDarkModeChange,
                            onBack = { backStack.close(key) },
                            onOpenWallets = { backStack.open(WalletsKey) },
                            onOpenCategories = { backStack.open(CategoriesKey) },
                            onOpenBackup = { backStack.open(BackupKey) },
                            onOpenImport = { backStack.open(ImportKey) },
                            reminders = reminders.ui,
                            onRemindersChange = reminders.onChange,
                            onOpenPhoneSettings = reminders.onOpenPhoneSettings,
                            lock = lock.ui,
                            onLockChange = lock.onChange,
                            onSizeChange = onSizeChange,
                            onStyleChange = onStyleChange,
                            onFontChange = onFontChange,
                            updates = update.ui,
                            onUpdateChecksChange = update.onChecksChange,
                            onCheckForUpdates = update.onCheckNow,
                            onUpdate = update.onUpdate,
                            onAllowInstalling = update.onAllow,
                            onAllowUpdateNotifications = update.onAllowNotifications,
                            revealUpdates = revealUpdates,
                        )
                    }
                    entry<EntryEditorKey> { key ->
                        EntryEditorRoute(
                            key.transactionId, key.startKind, key.payScheduleId, key.baonId,
                            onDone = { backStack.close(key) },
                            onOpenWallets = { backStack.open(WalletsKey) },
                        )
                    }
                    entry<WalletsKey> { key ->
                        WalletsRoute(
                            onBack = { backStack.close(key) },
                            onAdd = { backStack.open(WalletEditorKey()) },
                            onOpen = { backStack.open(WalletEditorKey(it)) },
                        )
                    }
                    entry<WalletEditorKey> { key ->
                        WalletEditorRoute(key.walletId, onDone = { backStack.close(key) })
                    }
                    entry<CategoriesKey> { key ->
                        CategoriesRoute(
                            onBack = { backStack.close(key) },
                            onAdd = { backStack.open(CategoryEditorKey(kind = it)) },
                            onOpen = { backStack.open(CategoryEditorKey(it.id, it.kind)) },
                        )
                    }
                    entry<CategoryEditorKey> { key ->
                        CategoryEditorRoute(key.categoryId, key.kind, onDone = { backStack.close(key) })
                    }
                },
            )
            AnimatedVisibility(
                visible = currentTab != null,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(tween(FitakaMotion.MEDIUM, easing = FitakaMotion.Emphasized)) { it } +
                    fadeIn(tween(FitakaMotion.MEDIUM)),
                exit = slideOutVertically(tween(FitakaMotion.SHORT, easing = FitakaMotion.Exit)) { it } +
                    fadeOut(tween(FitakaMotion.SHORT)),
            ) {
                // The size setting leaves the bar alone: it is drawn at the phone's own size.
                FullSize {
                    FitakaBottomBar(
                        items = MainTabs,
                        selectedIndex = currentTab ?: lastTab,
                        onSelect = { backStack.goToTab(it) },
                        modifier = Modifier.onSizeChanged { barHeight = with(density) { it.height.toDp() } },
                    )
                }
            }
        }
    }
}
