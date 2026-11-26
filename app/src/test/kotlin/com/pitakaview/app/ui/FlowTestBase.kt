package com.pitakaview.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.pitakaview.app.PitakaApp
import com.pitakaview.app.data.db.entity.PayScheduleEntity
import com.pitakaview.app.data.db.entity.TransactionEntity
import com.pitakaview.app.di.AppContainer
import com.pitakaview.app.di.LocalAppContainer
import com.pitakaview.app.domain.AppearanceSettings
import com.pitakaview.app.domain.CategoryKind
import com.pitakaview.app.domain.DarkModePref
import com.pitakaview.app.domain.PayFrequency
import com.pitakaview.app.domain.ThemeId
import com.pitakaview.app.domain.TxPurpose
import com.pitakaview.app.ui.navigation.PitakaRoot
import com.pitakaview.app.ui.theme.PitakaTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import java.time.Instant
import java.time.LocalDate

/**
 * Shared set-up for tests that drive the real app shell with simulated taps and typing (Robolectric):
 * real navigation, real database, real frosted bars. Subclasses choose the screen size with @Config.
 */
abstract class FlowTestBase {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    protected val container: AppContainer get() = (rule.activity.application as PitakaApp).container

    /** Lets a subclass put something around the app, such as a stand-in for Android's file picker. */
    @Composable
    protected open fun Surroundings(content: @Composable () -> Unit) = content()

    @Composable
    private fun App() {
        val appContainer = (LocalContext.current.applicationContext as PitakaApp).container
        Surroundings {
            CompositionLocalProvider(LocalAppContainer provides appContainer) {
                PitakaTheme(ThemeId.MANGO, DarkModePref.LIGHT) {
                    PitakaRoot(AppearanceSettings(), onThemeChange = {}, onDarkModeChange = {})
                }
            }
        }
    }

    /** Opens the app and waits for the first-run data (Cash wallet, starter categories). */
    protected fun start() {
        rule.setContent { App() }
        waitFor("Cash")
    }

    protected fun shows(text: String): Boolean =
        rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    protected fun waitFor(text: String) = rule.waitUntil(10_000) { shows(text) }

    /** Taps the one clickable thing carrying exactly this text. */
    protected fun tapButton(text: String): SemanticsNodeInteraction = rule.onNode(hasText(text) and hasClickAction()).performClick()

    /** The Nth text box on screen, top to bottom. */
    protected fun field(index: Int): SemanticsNodeInteraction = rule.onAllNodes(hasSetTextAction())[index]

    protected fun back(): SemanticsNodeInteraction = rule.onNodeWithContentDescription("Back").performClick()

    protected fun entries() = runBlocking { container.database.transactionDao().observeRecent(10).first() }

    protected fun wallets() = runBlocking { container.database.walletDao().observeAllWithBalance().first() }

    protected fun today(): LocalDate = container.dayClock.today.value

    /** A bottom-bar tab (the Home tiles carry the same words but are buttons, not tabs). */
    protected fun tapTab(name: String): SemanticsNodeInteraction = rule.onNode(hasText(name) and isSelectable()).performClick()

    /** An icon button, by what a screen reader calls it. */
    protected fun tapDescribed(description: String): SemanticsNodeInteraction = rule.onNodeWithContentDescription(description).performClick()

    /** Saves an expense from Cash straight into the ledger, for tests that are not about typing it in. */
    protected fun addExpense(centavos: Long, category: String = "Food", note: String? = null, on: LocalDate = today()) = runBlocking {
        val cash = wallets().first { it.wallet.name == "Cash" }.wallet.id
        val categoryId = container.database.categoryDao().observeActive(CategoryKind.EXPENSE).first().first { it.name == category }.id
        container.ledger.add(
            TransactionEntity(
                purpose = TxPurpose.EXPENSE, amountCentavos = centavos, occurredOn = on,
                fromWalletId = cash, categoryId = categoryId, note = note, createdAt = Instant.now(),
            ),
        )
    }

    protected fun paydays() = runBlocking { container.database.payScheduleDao().observeActive().first() }

    /** A weekly payday on today's weekday, set up today: it is due today whatever day the tests run. */
    protected fun addPaydayDueToday(name: String = "Sahod", centavos: Long = 1_500_000): Long = runBlocking {
        val cash = wallets().first { it.wallet.name == "Cash" }.wallet.id
        val salary = container.database.categoryDao().observeActive(CategoryKind.INCOME).first().first { it.name == "Salary" }.id
        container.database.payScheduleDao().insert(
            PayScheduleEntity(
                name = name, expectedAmountCentavos = centavos, walletId = cash, categoryId = salary,
                frequency = PayFrequency.WEEKLY, dayOfWeek = today().dayOfWeek, createdAt = Instant.now(),
            ),
        )
    }
}
