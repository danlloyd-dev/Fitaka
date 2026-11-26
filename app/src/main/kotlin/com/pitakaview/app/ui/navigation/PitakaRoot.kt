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
import com.pitakaview.app.domain.ReminderKind
import com.pitakaview.app.domain.ThemeId
import com.pitakaview.app.domain.TxPurpose
import com.pitakaview.app.reminders.ReminderOpen
import com.pitakaview.app.ui.backup.BackupRoute
import com.pitakaview.app.ui.baon.BaonDetailRoute
import com.pitakaview.app.ui.baon.BaonEditorRoute
import com.pitakaview.app.ui.baon.BaonRoute
import com.pitakaview.app.ui.categories.CategoriesRoute
import com.pitakaview.app.ui.categories.CategoryEditorRoute
import com.pitakaview.app.ui.components.LocalBottomBarPadding
import com.pitakaview.app.ui.components.LocalHazeState
import com.pitakaview.app.ui.components.PitakaBottomBar
import com.pitakaview.app.ui.entry.EntryEditorRoute
import com.pitakaview.app.ui.gastos.GastosRoute
import com.pitakaview.app.ui.home.HomeRoute
import com.pitakaview.app.ui.ipon.GoalDetailRoute
import com.pitakaview.app.ui.ipon.GoalEditorRoute
import com.pitakaview.app.ui.ipon.IponRoute
import com.pitakaview.app.ui.kita.KitaRoute
import com.pitakaview.app.ui.kita.PayScheduleEditorRoute
import com.pitakaview.app.ui.motion.PitakaMotion
import com.pitakaview.app.ui.search.SearchRoute
import com.pitakaview.app.ui.sections.MoveEditorRoute
import com.pitakaview.app.ui.settings.SettingsScreen
import com.pitakaview.app.ui.settings.rememberReminderSettings
import com.pitakaview.app.ui.summary.SummaryRoute
import com.pitakaview.app.ui.theme.PitakaTheme
import com.pitakaview.app.ui.theme.Section
import com.pitakaview.app.ui.utang.LoanDetailRoute
import com.pitakaview.app.ui.utang.LoanEditorRoute
import com.pitakaview.app.ui.utang.UtangRoute
import com.pitakaview.app.ui.wallets.WalletEditorRoute
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
@Serializable data object CategoriesKey : NavKey
@Serializable data class CategoryEditorKey(val categoryId: Long? = null, val kind: CategoryKind = CategoryKind.EXPENSE) : NavKey

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
    /** The screen a tapped reminder asked for; [onReminderOpened] is called once the app is there. */
    reminderToOpen: ReminderOpen? = null,
    onReminderOpened: () -> Unit = {},
) {
    val c = PitakaTheme.colors
    val backStack = rememberNavBackStack(HomeKey)
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
                backStack.add(LoanKey(request.rowId))
            }
        }
        onReminderOpened()
    }

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
                            onAddTransaction = { backStack.add(EntryEditorKey()) },
                            onOpenSettings = { backStack.add(SettingsKey) },
                            onOpenEntry = { backStack.add(EntryEditorKey(transactionId = it)) },
                            onOpenWallets = { backStack.add(WalletsKey) },
                            onOpenBackup = { backStack.add(BackupKey) },
                            onSearch = { backStack.add(SearchKey) },
                            onOpenSummary = { backStack.add(SummaryKey) },
                        )
                    }
                    entry<SummaryKey> {
                        SummaryRoute(onBack = { backStack.removeLastOrNull() })
                    }
                    entry<SearchKey> {
                        SearchRoute(
                            onBack = { backStack.removeLastOrNull() },
                            onOpenEntry = { backStack.add(EntryEditorKey(transactionId = it)) },
                        )
                    }
                    entry<PayScheduleEditorKey> { key ->
                        PayScheduleEditorRoute(key.scheduleId, onDone = { backStack.removeLastOrNull() })
                    }
                    entry<LoanKey> { key ->
                        LoanDetailRoute(
                            key.loanId,
                            onBack = { backStack.removeLastOrNull() },
                            onEdit = { backStack.add(LoanEditorKey(key.loanId)) },
                            onAddPayment = { backStack.add(MoveEditorKey(TxPurpose.LOAN_PAYMENT, key.loanId)) },
                            onOpenEntry = { backStack.add(EntryEditorKey(transactionId = it)) },
                        )
                    }
                    entry<LoanEditorKey> { key ->
                        LoanEditorRoute(key.loanId, onDone = { backStack.removeLastOrNull() })
                    }
                    entry<GoalKey> { key ->
                        GoalDetailRoute(
                            key.goalId,
                            onBack = { backStack.removeLastOrNull() },
                            onEdit = { backStack.add(GoalEditorKey(key.goalId)) },
                            onAdd = { backStack.add(MoveEditorKey(TxPurpose.SAVINGS_DEPOSIT, key.goalId)) },
                            onTakeOut = { backStack.add(MoveEditorKey(TxPurpose.SAVINGS_WITHDRAWAL, key.goalId)) },
                            onOpenEntry = { backStack.add(EntryEditorKey(transactionId = it)) },
                        )
                    }
                    entry<GoalEditorKey> { key ->
                        GoalEditorRoute(key.goalId, onDone = { backStack.removeLastOrNull() })
                    }
                    entry<BaonKey> { key ->
                        BaonDetailRoute(
                            key.baonId,
                            onBack = { backStack.removeLastOrNull() },
                            onEdit = { backStack.add(BaonEditorKey(key.baonId)) },
                            onReceived = { backStack.add(MoveEditorKey(TxPurpose.BAON_RECEIVED, key.baonId)) },
                            onSpend = { backStack.add(EntryEditorKey(startKind = EntryKind.EXPENSE, baonId = key.baonId)) },
                            onGive = { backStack.add(MoveEditorKey(TxPurpose.BAON_GIVEN, key.baonId)) },
                            onOpenEntry = { backStack.add(EntryEditorKey(transactionId = it)) },
                        )
                    }
                    entry<BaonEditorKey> { key ->
                        BaonEditorRoute(key.baonId, onDone = { backStack.removeLastOrNull() })
                    }
                    entry<MoveEditorKey> { key ->
                        MoveEditorRoute(transactionId = null, purpose = key.purpose, linkId = key.linkId, onDone = { backStack.removeLastOrNull() })
                    }
                    entry<BackupKey> {
                        BackupRoute(onBack = { backStack.removeLastOrNull() })
                    }
                    entry<SectionKey>(metadata = tabMetadata) { key ->
                        val openEntry: (Long) -> Unit = { backStack.add(EntryEditorKey(transactionId = it)) }
                        when (key.section) {
                            Section.GASTOS -> GastosRoute(
                                onAdd = { backStack.add(EntryEditorKey(startKind = EntryKind.EXPENSE)) },
                                onOpenEntry = openEntry,
                                onSearch = { backStack.add(SearchKey) },
                            )
                            Section.KITA -> KitaRoute(
                                onAdd = { backStack.add(EntryEditorKey(startKind = EntryKind.INCOME)) },
                                onOpenEntry = openEntry,
                                onSearch = { backStack.add(SearchKey) },
                                onAddPayday = { backStack.add(PayScheduleEditorKey()) },
                                onOpenPayday = { backStack.add(PayScheduleEditorKey(it)) },
                                onReceived = { backStack.add(EntryEditorKey(startKind = EntryKind.INCOME, payScheduleId = it)) },
                            )
                            Section.UTANG -> UtangRoute(
                                onAdd = { backStack.add(LoanEditorKey()) },
                                onOpen = { backStack.add(LoanKey(it)) },
                                onSearch = { backStack.add(SearchKey) },
                            )
                            Section.IPON -> IponRoute(
                                onAdd = { backStack.add(GoalEditorKey()) },
                                onOpen = { backStack.add(GoalKey(it)) },
                                onSearch = { backStack.add(SearchKey) },
                            )
                            Section.BAON -> BaonRoute(
                                onAdd = { backStack.add(BaonEditorKey()) },
                                onOpen = { backStack.add(BaonKey(it)) },
                                onReceived = { backStack.add(MoveEditorKey(TxPurpose.BAON_RECEIVED, it)) },
                                onSpend = { backStack.add(EntryEditorKey(startKind = EntryKind.EXPENSE, baonId = it)) },
                                onGive = { backStack.add(MoveEditorKey(TxPurpose.BAON_GIVEN, it)) },
                                onSearch = { backStack.add(SearchKey) },
                            )
                        }
                    }
                    entry<SettingsKey> {
                        val reminders = rememberReminderSettings()
                        SettingsScreen(
                            appearance, onThemeChange, onDarkModeChange,
                            onBack = { backStack.removeLastOrNull() },
                            onOpenWallets = { backStack.add(WalletsKey) },
                            onOpenCategories = { backStack.add(CategoriesKey) },
                            onOpenBackup = { backStack.add(BackupKey) },
                            reminders = reminders.ui,
                            onRemindersChange = reminders.onChange,
                            onOpenPhoneSettings = reminders.onOpenPhoneSettings,
                        )
                    }
                    entry<EntryEditorKey> { key ->
                        EntryEditorRoute(
                            key.transactionId, key.startKind, key.payScheduleId, key.baonId,
                            onDone = { backStack.removeLastOrNull() },
                            onOpenWallets = { backStack.add(WalletsKey) },
                        )
                    }
                    entry<WalletsKey> {
                        WalletsRoute(
                            onBack = { backStack.removeLastOrNull() },
                            onAdd = { backStack.add(WalletEditorKey()) },
                            onOpen = { backStack.add(WalletEditorKey(it)) },
                        )
                    }
                    entry<WalletEditorKey> { key ->
                        WalletEditorRoute(key.walletId, onDone = { backStack.removeLastOrNull() })
                    }
                    entry<CategoriesKey> {
                        CategoriesRoute(
                            onBack = { backStack.removeLastOrNull() },
                            onAdd = { backStack.add(CategoryEditorKey(kind = it)) },
                            onOpen = { backStack.add(CategoryEditorKey(it.id, it.kind)) },
                        )
                    }
                    entry<CategoryEditorKey> { key ->
                        CategoryEditorRoute(key.categoryId, key.kind, onDone = { backStack.removeLastOrNull() })
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
