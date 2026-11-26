package com.pitakaview.app.ui

import androidx.compose.foundation.text.input.TextFieldState
import com.github.takahirom.roborazzi.captureRoboImage
import com.pitakaview.app.data.DefaultData
import com.pitakaview.app.data.backup.BackupInfo
import com.pitakaview.app.ui.backup.BackupContent
import com.pitakaview.app.ui.backup.BackupNotice
import com.pitakaview.app.data.db.entity.CategoryEntity
import com.pitakaview.app.domain.CategoryKind
import com.pitakaview.app.domain.EntryKind
import com.pitakaview.app.domain.EntryProblem
import com.pitakaview.app.domain.PayFrequency
import com.pitakaview.app.domain.WaitingPayday
import com.pitakaview.app.domain.WeekendRule
import com.pitakaview.app.ui.gastos.GastosContent
import com.pitakaview.app.ui.kita.KitaContent
import com.pitakaview.app.ui.kita.PayScheduleEditorContent
import com.pitakaview.app.ui.kita.PayScheduleEditorUi
import com.pitakaview.app.ui.kita.PaydayRow
import com.pitakaview.app.ui.ledger.MonthLedgerBuilder
import com.pitakaview.app.ui.search.SearchContent
import com.pitakaview.app.ui.search.SearchUi
import com.pitakaview.app.ui.entry.EntryEditorContent
import com.pitakaview.app.ui.entry.EntryEditorUi
import com.pitakaview.app.ui.wallets.WalletsContent
import com.pitakaview.app.data.db.dao.RecentTransaction
import com.pitakaview.app.data.db.dao.WalletWithBalance
import com.pitakaview.app.data.db.entity.WalletEntity
import com.pitakaview.app.domain.AppearanceSettings
import com.pitakaview.app.domain.DarkModePref
import com.pitakaview.app.domain.ThemeId
import com.pitakaview.app.domain.TxPurpose
import com.pitakaview.app.domain.WalletType
import com.pitakaview.app.ui.home.BackupNudge
import com.pitakaview.app.ui.home.HomeContent
import com.pitakaview.app.ui.home.HomeState
import com.pitakaview.app.ui.home.UpcomingItem
import com.pitakaview.app.ui.settings.SettingsScreen
import com.pitakaview.app.ui.theme.PitakaTheme
import com.pitakaview.app.ui.theme.Section
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** Real screens with sample data. Bars render solid here (no blur source in tests); blur is checked on the phone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h1500dp-hdpi")
class ScreensScreenshotTest {
    private val today = LocalDate.of(2026, 11, 25)
    private val t0 = Instant.EPOCH

    private val sample = HomeState(
        wallets = listOf(
            WalletWithBalance(WalletEntity(id = 1, name = "Cash", type = WalletType.CASH, createdAt = t0), 85_000),
            WalletWithBalance(WalletEntity(id = 2, name = "GCash", type = WalletType.E_WALLET, createdAt = t0), 275_000),
            WalletWithBalance(WalletEntity(id = 3, name = "MariBank", type = WalletType.BANK, createdAt = t0), 2_200_000),
        ),
        incomeCentavos = 2_000_000,
        spendingCentavos = 876_550,
        savedCentavos = 600_000,
        iOweCentavos = 900_000,
        owedToMeCentavos = 200_000,
        baonLeftCentavos = 38_000,
        upcoming = listOf(
            UpcomingItem(LocalDate.of(2026, 11, 30), "Sahod", "Payday", Section.KITA, 1_500_000),
            UpcomingItem(LocalDate.of(2026, 12, 1), "Tita Baby", "You pay", Section.UTANG, 900_000),
        ),
        recent = listOf(
            RecentTransaction(3, TxPurpose.EXPENSE, 12_500, today, null, "Food", "GCash", null),
            RecentTransaction(2, TxPurpose.TRANSFER, 300_000, today.minusDays(1), null, null, "MariBank", "GCash"),
            RecentTransaction(1, TxPurpose.INCOME, 2_000_000, LocalDate.of(2026, 11, 15), null, "Salary", null, "MariBank"),
        ),
    )

    private fun home(theme: ThemeId, mode: DarkModePref, state: HomeState, name: String) =
        captureRoboImage("build/outputs/roborazzi/screen_$name.png") {
            PitakaTheme(theme, mode) { HomeContent(state, today, "Magandang hapon!", {}, {}, {}) }
        }

    @Test fun homeMangoLight() = home(ThemeId.MANGO, DarkModePref.LIGHT, sample, "home_mango_light")
    @Test fun homeUbeDark() = home(ThemeId.UBE, DarkModePref.DARK, sample, "home_ube_dark")
    @Test fun homeBackupReminderMangoLight() = home(ThemeId.MANGO, DarkModePref.LIGHT, sample.copy(backupNudge = BackupNudge.NEVER_BACKED_UP), "home_backup_reminder_mango_light")
    @Test fun homeEmptyFirstRun() = home(ThemeId.MANGO, DarkModePref.LIGHT, HomeState(wallets = sample.wallets.take(1).map { it.copy(balanceCentavos = 0) }), "home_empty")

    private val entryCategories = DefaultData.expenseCategories.mapIndexed { i, (name, icon) ->
        CategoryEntity(id = i + 1L, name = name, kind = CategoryKind.EXPENSE, iconKey = icon)
    }

    private fun entry(theme: ThemeId, mode: DarkModePref, ui: EntryEditorUi, amount: String, name: String) =
        captureRoboImage("build/outputs/roborazzi/screen_$name.png") {
            PitakaTheme(theme, mode) {
                EntryEditorContent(
                    ui, TextFieldState(amount), TextFieldState(),
                    onKind = {}, onCategory = {}, onFromWallet = {}, onToWallet = {}, onDate = {},
                    onSave = {}, onDelete = {}, onBack = {}, onOpenWallets = {},
                )
            }
        }

    @Test
    fun entryFilledMangoLight() = entry(
        ThemeId.MANGO, DarkModePref.LIGHT,
        EntryEditorUi(
            isEdit = false, kind = EntryKind.EXPENSE, categories = entryCategories, wallets = sample.wallets.map { it.wallet },
            categoryId = 1, fromWalletId = 2, toWalletId = null, date = today, today = today,
        ),
        amount = "250", name = "entry_mango_light",
    )

    @Test
    fun entryWithProblemsKapeDark() = entry(
        ThemeId.KAPE, DarkModePref.DARK,
        EntryEditorUi(
            isEdit = true, kind = EntryKind.EXPENSE, categories = entryCategories, wallets = sample.wallets.map { it.wallet },
            categoryId = null, fromWalletId = 1, toWalletId = null, date = today.minusDays(3), today = today,
            problems = setOf(EntryProblem.AMOUNT, EntryProblem.CATEGORY),
        ),
        amount = "", name = "entry_problems_kape_dark",
    )

    @Test
    fun walletsCalamansiLight() = captureRoboImage("build/outputs/roborazzi/screen_wallets_calamansi_light.png") {
        PitakaTheme(ThemeId.CALAMANSI, DarkModePref.LIGHT) { WalletsContent(sample.wallets, onBack = {}, onAdd = {}, onOpen = {}) }
    }

    private fun backup(theme: ThemeId, mode: DarkModePref, name: String, lastBackupOn: LocalDate?, notice: BackupNotice?, pending: BackupInfo?) =
        captureRoboImage("build/outputs/roborazzi/screen_$name.png") {
            PitakaTheme(theme, mode) {
                BackupContent(
                    lastBackupOn = lastBackupOn, today = today, notice = notice, pending = pending, busy = false,
                    onBackUp = {}, onPickBackup = {}, onConfirmRestore = {}, onCancelRestore = {}, onBack = {},
                )
            }
        }

    @Test
    fun backupJustSavedMangoLight() = backup(ThemeId.MANGO, DarkModePref.LIGHT, "backup_saved_mango_light", today, BackupNotice.SAVED, null)

    @Test
    fun backupRestoreQuestionUbeDark() = backup(
        ThemeId.UBE, DarkModePref.DARK, "backup_restore_ube_dark", today.minusDays(40), null,
        BackupInfo(schemaVersion = 1, entries = 128, wallets = 3, lastEntryOn = today.minusDays(2)),
    )

    private val november = YearMonth.of(2026, 11)
    private val spending = listOf(
        RecentTransaction(9, TxPurpose.EXPENSE, 12_500, today, "lugaw", "Food", "GCash", null),
        RecentTransaction(8, TxPurpose.EXPENSE, 1_300, today, null, "Transport", "Cash", null),
        RecentTransaction(7, TxPurpose.EXPENSE, 180_000, today.minusDays(1), "Meralco", "Bills", "MariBank", null),
        RecentTransaction(6, TxPurpose.EXPENSE, 45_050, today.minusDays(3), null, "Groceries", "GCash", null),
        RecentTransaction(5, TxPurpose.EXPENSE, 25_000, today.minusDays(3), "lunch with Tita", "Food", "Cash", null),
    )
    private val income = listOf(
        RecentTransaction(12, TxPurpose.INCOME, 250_000, today.minusDays(5), "logo job", "Sideline", null, "GCash"),
        RecentTransaction(11, TxPurpose.INCOME, 1_500_000, LocalDate.of(2026, 11, 15), null, "Salary", null, "MariBank"),
    )

    @Test
    fun gastosMangoLight() = captureRoboImage("build/outputs/roborazzi/screen_gastos_mango_light.png") {
        PitakaTheme(ThemeId.MANGO, DarkModePref.LIGHT) {
            GastosContent(MonthLedgerBuilder.build(november, true, spending), today, {}, {}, {}, {}, {}, {})
        }
    }

    @Test
    fun gastosEmptyOtherMonthKapeDark() = captureRoboImage("build/outputs/roborazzi/screen_gastos_empty_kape_dark.png") {
        PitakaTheme(ThemeId.KAPE, DarkModePref.DARK) {
            GastosContent(MonthLedgerBuilder.build(YearMonth.of(2026, 10), false, emptyList()), today, {}, {}, {}, {}, {}, {})
        }
    }

    @Test
    fun kitaWithPaydaysDagatDark() = captureRoboImage("build/outputs/roborazzi/screen_kita_dagat_dark.png") {
        PitakaTheme(ThemeId.DAGAT, DarkModePref.DARK) {
            KitaContent(
                ledger = MonthLedgerBuilder.build(november, true, income),
                paydays = listOf(
                    PaydayRow(1, "Sahod", 1_500_000, WaitingPayday(LocalDate.of(2026, 11, 30), canReceive = true)),
                    PaydayRow(2, "Sideline", 300_000, WaitingPayday(LocalDate.of(2026, 12, 4), canReceive = false)),
                ),
                today = today,
                onPreviousMonth = {}, onNextMonth = {}, onThisMonth = {}, onAdd = {}, onOpenEntry = {}, onSearch = {},
                onAddPayday = {}, onOpenPayday = {}, onReceived = {},
            )
        }
    }

    @Test
    fun paydayEditorCalamansiLight() = captureRoboImage("build/outputs/roborazzi/screen_payday_editor_calamansi_light.png") {
        PitakaTheme(ThemeId.CALAMANSI, DarkModePref.LIGHT) {
            PayScheduleEditorContent(
                ui = PayScheduleEditorUi(
                    isEdit = true, frequency = PayFrequency.SEMI_MONTHLY, dayOfWeek = DayOfWeek.FRIDAY, weekendRule = WeekendRule.KEEP,
                    wallets = sample.wallets.map { it.wallet },
                    categories = DefaultData.incomeCategories.mapIndexed { i, (name, icon) -> CategoryEntity(id = i + 1L, name = name, kind = CategoryKind.INCOME, iconKey = icon) },
                    walletId = 3, categoryId = 1, today = today,
                    nextPaydays = listOf(LocalDate.of(2026, 11, 30), LocalDate.of(2026, 12, 15), LocalDate.of(2026, 12, 31)),
                ),
                name = TextFieldState("Sahod"), amount = TextFieldState("15000"), firstDay = TextFieldState("15"), secondDay = TextFieldState("31"),
                onFrequency = {}, onDayOfWeek = {}, onWeekendRule = {}, onWallet = {}, onCategory = {}, onSave = {}, onRemove = {}, onBack = {},
            )
        }
    }

    @Test
    fun searchResultsUbeLight() = captureRoboImage("build/outputs/roborazzi/screen_search_ube_light.png") {
        PitakaTheme(ThemeId.UBE, DarkModePref.LIGHT) {
            val found = spending.filter { it.categoryName == "Food" }
            SearchContent(TextFieldState("food"), SearchUi(asked = true, count = found.size, days = MonthLedgerBuilder.groupByDay(found)), today, {}, {})
        }
    }

    @Test
    fun settingsDagatLight() = captureRoboImage("build/outputs/roborazzi/screen_settings_dagat_light.png") {
        PitakaTheme(ThemeId.DAGAT, DarkModePref.LIGHT) {
            SettingsScreen(AppearanceSettings(ThemeId.DAGAT, DarkModePref.LIGHT), {}, {}, onBack = {})
        }
    }
}
