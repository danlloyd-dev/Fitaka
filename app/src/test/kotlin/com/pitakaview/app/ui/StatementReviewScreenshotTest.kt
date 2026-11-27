package com.pitakaview.app.ui

import com.github.takahirom.roborazzi.captureRoboImage
import com.pitakaview.app.data.db.entity.CategoryEntity
import com.pitakaview.app.data.db.entity.WalletEntity
import com.pitakaview.app.domain.CategoryKind
import com.pitakaview.app.domain.DarkModePref
import com.pitakaview.app.domain.ThemeId
import com.pitakaview.app.domain.WalletType
import com.pitakaview.app.domain.importing.ImportPlanner
import com.pitakaview.app.domain.importing.PdfStatementReader
import com.pitakaview.app.domain.importing.StatementRead
import com.pitakaview.app.ui.importing.ImportContent
import com.pitakaview.app.ui.importing.ImportStage
import com.pitakaview.app.ui.importing.ImportText
import com.pitakaview.app.ui.theme.FitakaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/**
 * The review of a bank statement (made-up, in MariBank's layout) where the file cannot say which way two
 * lines went: what the owner sees, at the size of their phone, and the words that go with it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h850dp-hdpi")
class StatementReviewScreenshotTest {
    private val now = Instant.parse("2026-11-26T04:00:00Z")
    private val wallets = listOf(WalletEntity(id = 3, name = "MariBank", type = WalletType.BANK, createdAt = now))
    private val expense = listOf("Food", "Bills", "Other").mapIndexed { i, name -> CategoryEntity(id = 10L + i, name = name, kind = CategoryKind.EXPENSE, iconKey = "x") }
    private val income = listOf("Salary", "Other").mapIndexed { i, name -> CategoryEntity(id = 30L + i, name = name, kind = CategoryKind.INCOME, iconKey = "x") }

    private val pages = listOf(
        """
        BANK STATEMENT
        S/N E01-123456ABCDEFGH
        31 OCT 2026
        page 1 of 1
        01 OCT 2026 to 31 OCT 2026
        SAVINGS 10.00 21,000.00 1,500.00 19,510.00
        SAVINGS - TRANSACTION DETAILS
        DATE TRANSACTION OUTGOING (PHP) INCOMING (PHP)
        05 OCT Tita Baby
        Transfer
        1,000.00
        06 OCT Kuya Jun
        Transfer
        1,000.00
        02 OCT Via Other Banks Channel
        Transfer
        20,000.00
        03 OCT Shopee
        Payment
        500.00
        """.trimIndent(),
    )

    private val read = PdfStatementReader.read(pages) as StatementRead.Rows
    private val review = ImportStage.Review(
        "maribank-october.pdf", read,
        ImportPlanner.plan(read.rows, read.source, walletId = 3, importedRefs = emptySet(), existing = emptyList(), expenseCategories = expense, incomeCategories = income),
        flipped = false,
    )

    @Test
    fun aStatementWithTwoLinesLeftOpen() = captureRoboImage("build/outputs/roborazzi/screen_import_statement_open_lines_mango_light.png") {
        FitakaTheme(ThemeId.MANGO, DarkModePref.LIGHT) {
            ImportContent(
                stage = review, notice = null, wallets = wallets, walletId = 3, batches = emptyList(),
                categories = { if (it) income else expense }, openLine = 0,
                onWallet = {}, onChooseFile = {}, onToggle = {}, onShowChoices = {}, onCategory = { _, _ -> }, onDirection = { _, _ -> }, onFlip = {},
                onImport = {}, onCancel = {}, onUndo = {}, onUnlockPdf = {}, onCopy = {}, onBack = {},
            )
        }
    }

    @Test
    fun theWordsAboutTotalsAndOpenLines() {
        assertEquals(2, review.unsure)
        assertFalse("nothing is imported while a line is open", review.canImport)
        // The guess for the two open lines is one each way, so the sums already match; the open lines are what holds it.
        assertEquals(true, review.addsUp)
        assertEquals("Adds up to the statement's own totals: ₱21,000.00 in, ₱1,500.00 out.", ImportText.totals(review))
        assertTrue(ImportText.unsure(review)!!.startsWith("2 lines could have gone either way"))
        assertTrue("it says where to answer: on the lines, not behind another tap", ImportText.unsure(review)!!.contains("On each line marked below, tap Money in or Money out."))

        // A plus or minus is a claim about which way the money went; an open line makes none.
        val open = review.candidates.first { !it.row.sure }
        val settled = review.candidates.first { it.row.sure && it.row.moneyIn }
        assertEquals("₱1,000.00", ImportText.amount(open))
        assertEquals("+₱20,000.00", ImportText.amount(settled))
        assertEquals("−₱500.00", ImportText.amount(review.candidates.first { it.row.sure && !it.row.moneyIn }))

        // A file with no totals of its own (a CSV) says nothing about totals and is not held back.
        val csv = review.copy(read = read.copy(totals = null), candidates = review.candidates.map { it.copy(row = it.row.copy(sure = true)) })
        assertEquals(null, ImportText.totals(csv))
        assertEquals(null, ImportText.unsure(csv))
        assertTrue(csv.canImport)
    }
}
