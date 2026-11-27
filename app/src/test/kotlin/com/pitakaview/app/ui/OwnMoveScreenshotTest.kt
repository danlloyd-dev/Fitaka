package com.pitakaview.app.ui

import androidx.compose.foundation.text.input.TextFieldState
import com.github.takahirom.roborazzi.captureRoboImage
import com.pitakaview.app.data.db.entity.CategoryEntity
import com.pitakaview.app.data.db.entity.WalletEntity
import com.pitakaview.app.domain.CategoryKind
import com.pitakaview.app.domain.DarkModePref
import com.pitakaview.app.domain.EntryKind
import com.pitakaview.app.domain.ThemeId
import com.pitakaview.app.domain.WalletType
import com.pitakaview.app.domain.importing.CsvStatementReader
import com.pitakaview.app.domain.importing.ImportPlanner
import com.pitakaview.app.domain.importing.OwnMoveCandidate
import com.pitakaview.app.domain.importing.StatementRead
import com.pitakaview.app.ui.entry.EntryEditorContent
import com.pitakaview.app.ui.entry.EntryEditorUi
import com.pitakaview.app.ui.importing.ImportContent
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

/**
 * What the owner sees of a move between two of their own wallets (made-up amounts), at the size of
 * their phone: the question on the review of the second statement, and the half opened afterwards.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h850dp-hdpi")
class OwnMoveScreenshotTest {
    private val now = Instant.parse("2026-11-26T04:00:00Z")
    private val wallets = listOf(
        WalletEntity(id = 2, name = "GCash", type = WalletType.E_WALLET, createdAt = now),
        WalletEntity(id = 3, name = "MariBank", type = WalletType.BANK, createdAt = now),
    )
    private val expense = listOf("Food", "Bills", "Other").mapIndexed { i, name -> CategoryEntity(id = 10L + i, name = name, kind = CategoryKind.EXPENSE, iconKey = "x") }
    private val income = listOf("Salary", "Other").mapIndexed { i, name -> CategoryEntity(id = 30L + i, name = name, kind = CategoryKind.INCOME, iconKey = "x") }

    private val read = CsvStatementReader.read(
        "Date,Description,Reference No.,Debit,Credit\n" +
            "2026-11-04,Via Other Banks Channel,M1,,5000.00\n" +
            "2026-11-05,Payment to Jollibee,M2,250.00,\n",
    ) as StatementRead.Rows

    /** MariBank's file, with 5,000.00 of plain spending already in GCash on the 3rd. */
    private val review = ImportStage.Review(
        "maribank-november.csv", read,
        ImportPlanner.plan(
            read.rows, read.source, walletId = 3, importedRefs = emptySet(), existing = emptyList(), expenseCategories = expense, incomeCategories = income,
            ownMoves = listOf(OwnMoveCandidate(41, "GCash", LocalDate.of(2026, 11, 3), 500_000, moneyIn = false)),
        ),
        flipped = false,
    )

    private fun reviewScreen(theme: ThemeId, mode: DarkModePref, name: String, stage: ImportStage.Review = review) =
        captureRoboImage("build/outputs/roborazzi/screen_import_own_move_$name.png") {
            FitakaTheme(theme, mode) {
                ImportContent(
                    stage = stage, notice = null, wallets = wallets, walletId = 3, batches = emptyList(),
                    categories = { if (it) income else expense }, openLine = null,
                    onWallet = {}, onChooseFile = {}, onToggle = {}, onShowChoices = {}, onCategory = { _, _ -> }, onDirection = { _, _ -> }, onFlip = {},
                    onImport = {}, onCancel = {}, onUndo = {}, onUnlockPdf = {}, onCopy = {}, onBack = {},
                )
            }
        }

    @Test
    fun theReviewAsksAboutTheLineThatLooksLikeTheOwnersOwnTransfer() = reviewScreen(ThemeId.MANGO, DarkModePref.LIGHT, "mango_light")

    @Test
    fun theSameInTheDark() = reviewScreen(ThemeId.MANGO, DarkModePref.DARK, "mango_dark")

    @Test
    fun afterTheOwnerSaysItIsIncome() = reviewScreen(
        ThemeId.MANGO, DarkModePref.LIGHT, "said_no_mango_light",
        review.copy(candidates = review.candidates.map { it.copy(asOwnMove = false) }),
    )

    @Test
    fun aHalfOpenedLaterSaysWhatItIsAndOffersToTurnItBack() = captureRoboImage("build/outputs/roborazzi/screen_entry_own_move_mango_light.png") {
        FitakaTheme(ThemeId.MANGO, DarkModePref.LIGHT) {
            EntryEditorContent(
                EntryEditorUi(
                    isEdit = true, kind = EntryKind.EXPENSE, categories = expense, wallets = wallets, categoryId = 12, fromWalletId = 2, toWalletId = null,
                    date = LocalDate.of(2026, 11, 3), today = LocalDate.of(2026, 11, 27), kindLocked = true, ownMove = true,
                ),
                TextFieldState("5000"), TextFieldState("Transfer to MariBank"),
                onKind = {}, onCategory = {}, onFromWallet = {}, onToWallet = {}, onDate = {},
                onSave = {}, onDelete = {}, onBack = {}, onOpenWallets = {},
            )
        }
    }

    @Test
    fun saidToBeSpendingTheCategoryIsThereToCheck() = captureRoboImage("build/outputs/roborazzi/screen_entry_own_move_said_no_mango_light.png") {
        FitakaTheme(ThemeId.MANGO, DarkModePref.LIGHT) {
            EntryEditorContent(
                EntryEditorUi(
                    isEdit = true, kind = EntryKind.EXPENSE, categories = expense, wallets = wallets, categoryId = 12, fromWalletId = 2, toWalletId = null,
                    date = LocalDate.of(2026, 11, 3), today = LocalDate.of(2026, 11, 27), kindLocked = true, ownMove = false,
                ),
                TextFieldState("5000"), TextFieldState("Transfer to MariBank"),
                onKind = {}, onCategory = {}, onFromWallet = {}, onToWallet = {}, onDate = {},
                onSave = {}, onDelete = {}, onBack = {}, onOpenWallets = {},
            )
        }
    }

    @Test
    fun theWords() {
        assertEquals("2 lines read · 2 new · 1 moved between your own wallets", ImportText.summary(review))
        assertEquals("₱5,000.00 left GCash on ${ImportText.day(review.candidates[0]).replace("4", "3")}", ImportText.ownMove(review.candidates[0]))
        assertEquals(null, ImportText.ownMove(review.candidates[1]))
        assertEquals("No, it is income", ImportText.notOwnMove(review.candidates[0]))
    }
}
