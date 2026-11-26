package com.pitakaview.app.ui

import com.github.takahirom.roborazzi.captureRoboImage
import com.pitakaview.app.data.db.dao.RecentTransaction
import com.pitakaview.app.data.db.dao.WalletWithBalance
import com.pitakaview.app.data.db.entity.WalletEntity
import com.pitakaview.app.domain.AppearanceSettings
import com.pitakaview.app.domain.DarkModePref
import com.pitakaview.app.domain.ThemeId
import com.pitakaview.app.domain.TxPurpose
import com.pitakaview.app.domain.WalletType
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
import java.time.LocalDate

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
    @Test fun homeEmptyFirstRun() = home(ThemeId.MANGO, DarkModePref.LIGHT, HomeState(wallets = sample.wallets.take(1).map { it.copy(balanceCentavos = 0) }), "home_empty")

    @Test
    fun settingsDagatLight() = captureRoboImage("build/outputs/roborazzi/screen_settings_dagat_light.png") {
        PitakaTheme(ThemeId.DAGAT, DarkModePref.LIGHT) {
            SettingsScreen(AppearanceSettings(ThemeId.DAGAT, DarkModePref.LIGHT), {}, {}, onBack = {})
        }
    }
}
