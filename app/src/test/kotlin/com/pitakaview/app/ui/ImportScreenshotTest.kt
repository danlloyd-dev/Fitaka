package com.pitakaview.app.ui

import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.captureRoboImage
import com.pitakaview.app.data.db.dao.ImportBatchRow
import com.pitakaview.app.data.db.entity.CategoryEntity
import com.pitakaview.app.data.db.entity.WalletEntity
import com.pitakaview.app.domain.CategoryKind
import com.pitakaview.app.domain.DarkModePref
import com.pitakaview.app.domain.ImportSource
import com.pitakaview.app.domain.ThemeId
import com.pitakaview.app.domain.WalletType
import com.pitakaview.app.domain.importing.ImportPlanner
import com.pitakaview.app.domain.importing.LedgerMark
import com.pitakaview.app.domain.importing.StatementRead
import com.pitakaview.app.domain.importing.StatementRow
import com.pitakaview.app.domain.importing.TextMask
import com.pitakaview.app.ui.importing.ImportContent
import com.pitakaview.app.ui.importing.ImportNotice
import com.pitakaview.app.ui.importing.ImportStage
import com.pitakaview.app.ui.importing.ImportText
import com.pitakaview.app.ui.theme.FitakaTheme
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The Import screen in its three states, at the size of the owner's phone, and the words it uses. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h850dp-hdpi")
class ImportScreenshotTest {
    private val manila = ZoneId.of("Asia/Manila")
    private val now = Instant.parse("2026-11-26T04:00:00Z")
    private val wallets = listOf(
        WalletEntity(id = 1, name = "Cash", type = WalletType.CASH, createdAt = now),
        WalletEntity(id = 2, name = "GCash", type = WalletType.E_WALLET, createdAt = now),
    )
    private val expense = listOf("Food", "Transport", "Bills", "Groceries", "Other")
        .mapIndexed { i, name -> CategoryEntity(id = 10L + i, name = name, kind = CategoryKind.EXPENSE, iconKey = "x") }
    private val income = listOf("Salary", "Other").mapIndexed { i, name -> CategoryEntity(id = 30L + i, name = name, kind = CategoryKind.INCOME, iconKey = "x") }

    private fun row(day: Int, description: String, centavos: Long, moneyIn: Boolean = false, reference: String? = null) =
        StatementRow(LocalDate.of(2026, 11, day), description, reference, centavos, moneyIn)

    private val rows = listOf(
        row(3, "Payment to Jollibee", 25_000, reference = "R1"),
        row(4, "Grab ride to Makati", 18_000, reference = "R2"),
        row(5, "Payroll November", 1_500_000, moneyIn = true, reference = "R3"),
        row(6, "Meralco bills payment", 234_567, reference = "R4"),
        row(6, "Meralco bills payment", 234_567, reference = "R4"),
    )

    private val review = ImportStage.Review(
        fileName = "gcash-november.csv",
        read = StatementRead.Rows(rows, unreadLines = 1, movesLeftOut = 0, directionKnown = true, dayFirst = null),
        candidates = ImportPlanner.plan(
            rows, ImportSource.CSV, walletId = 2,
            importedRefs = setOf("CSV:2:R2:out:18000"),
            existing = listOf(LedgerMark(LocalDate.of(2026, 11, 3), 25_000, false)),
            expenseCategories = expense, incomeCategories = income,
        ),
        flipped = false,
    )

    @Composable
    private fun Screen(stage: ImportStage, notice: ImportNotice? = null, batches: List<ImportBatchRow> = emptyList(), openLine: Int? = null) {
        ImportContent(
            stage = stage, notice = notice, wallets = wallets, walletId = 2, batches = batches,
            categories = { if (it) income else expense }, openLine = openLine,
            onWallet = {}, onChooseFile = {}, onToggle = {}, onShowChoices = {}, onCategory = { _, _ -> }, onDirection = { _, _ -> }, onFlip = {},
            onImport = {}, onCancel = {}, onUndo = {}, onUnlockPdf = {}, onCopy = {}, onBack = {}, zone = manila,
        )
    }

    private fun shot(name: String, theme: ThemeId, mode: DarkModePref, content: @Composable () -> Unit) =
        captureRoboImage("build/outputs/roborazzi/screen_$name.png") { FitakaTheme(theme, mode) { content() } }

    @Test
    fun choosing() = shot("import_choose_mango_light", ThemeId.MANGO, DarkModePref.LIGHT) {
        Screen(
            ImportStage.Choose,
            notice = ImportNotice.Imported(2, "GCash"),
            batches = listOf(ImportBatchRow(id = 1, fileName = "gcash-november.csv", importedAt = now, walletName = "GCash", entryCount = 2)),
        )
    }

    @Test
    fun reviewing() = shot("import_review_dagat_light", ThemeId.DAGAT, DarkModePref.LIGHT) { Screen(review, openLine = 2) }

    @Test
    fun pdfLayout() = shot("import_pdf_layout_ube_dark", ThemeId.UBE, DarkModePref.DARK) {
        Screen(
            ImportStage.PdfLayout(
                "statement.pdf", pages = 2,
                sample = TextMask.sample(
                    listOf(
                        "GCash Transaction History\nJuan Dela Cruz 09171234567\nDate and Time Description Reference No. Debit Credit Balance\n" +
                            "2026-11-03 10:15 PM Payment to Jollibee 1000000000001 250.00 4,750.00\n2026-11-05 08:00 AM Cash in 1000000000002 1,500.00 6,250.00",
                        "Total Debit 250.00 Total Credit 1,500.00",
                    ),
                ),
            ),
        )
    }

    @Test
    fun theWordsOfTheReviewAndTheNotices() {
        assertEquals("5 lines read · 2 new · 1 already imported · 1 looks like an entry you already have · 1 twice in this file", ImportText.summary(review))
        assertEquals(2, review.picked)
        assertEquals(listOf("−₱250.00", "−₱180.00", "+₱15,000.00"), review.candidates.take(3).map(ImportText::amount))
        assertEquals("Nov 3, 2026", ImportText.day(review.candidates.first()))
        assertEquals("1 entry", ImportText.entries(1))
        assertEquals("Imported 2 entries into GCash.", ImportText.notice(ImportNotice.Imported(2, "GCash")))
        assertEquals("Removed what a.csv had added.", ImportText.notice(ImportNotice.Undone("a.csv")))
        assertEquals(
            "2 entries · GCash · Nov 26, 2026",
            ImportText.batch(ImportBatchRow(id = 1, fileName = "a.csv", importedAt = now, walletName = "GCash", entryCount = 2), manila),
        )
    }
}
