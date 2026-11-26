package com.pitakaview.app.ui

import android.app.Activity
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.core.app.ActivityOptionsCompat
import com.pitakaview.app.data.db.entity.TransactionEntity
import com.pitakaview.app.domain.CategoryKind
import com.pitakaview.app.domain.TxPurpose
import com.pitakaview.app.importing.PdfText
import com.pitakaview.app.importing.PdfTextReader
import com.pitakaview.app.ui.importing.ImportViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.Instant
import java.time.LocalDate

/**
 * Importing a file through the real screens, on a phone-sized screen. Android's "pick a file" screen
 * belongs to the system, so a stand-in answers it with [picked]; Android's PDF reader is a stand-in too.
 * Reading the CSV, working out what is new, the write and the undo are all the real code.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h850dp-xhdpi")
class ImportFlowTest : FlowTestBase() {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var picked: File

    private val filePicker = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            dispatchResult(requestCode, Activity.RESULT_OK, Intent().setData(Uri.fromFile(picked)))
        }
    }

    @Composable
    override fun Surroundings(content: @Composable () -> Unit) {
        val owner = remember {
            object : ActivityResultRegistryOwner {
                override val activityResultRegistry: ActivityResultRegistry = filePicker
            }
        }
        CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner, content = content)
    }

    /** A locked statement, as Android's reader would report it. */
    private class LockedPdf(private val password: String, private val pages: List<String>) : PdfTextReader {
        val tried = mutableListOf<String?>()
        override suspend fun read(bytes: ByteArray, password: String?): PdfText {
            tried += password
            return when (password) {
                null -> PdfText.NeedsPassword
                this.password -> PdfText.Pages(pages)
                else -> PdfText.WrongPassword
            }
        }
    }

    private fun file(name: String, text: String) = File(tmp.root, name).apply { writeText(text) }

    private fun scrollTo(text: String) = rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text, substring = true))

    private fun line(text: String): SemanticsNodeInteraction = rule.onNode(hasText(text, substring = true) and isToggleable())

    private fun openImport() {
        rule.onNodeWithContentDescription("Settings").performClick()
        waitFor("Money set-up")
        tapButton("Import")
        waitFor("Which wallet is the file for")
    }

    private val statement =
        "My statement\n\n" +
            "Date,Description,Reference No.,Debit,Credit\n" +
            "2026-11-03,Payment to Jollibee,R1,250.00,\n" +
            "2026-11-04,Grab ride,R2,180.00,\n" +
            "2026-11-05,Payroll November,R3,,\"15,000.00\"\n" +
            "Ending balance,,,,\n"

    @Test
    fun aFileIsReviewedImportedRecognisedTheNextTimeAndTakenBackOut() {
        picked = file("november.csv", statement)
        start()
        openImport()
        tapButton("Choose a file")
        waitFor("november.csv")

        // What was read, before anything is stored.
        assertTrue(shows("3 lines read · 3 new"))
        assertTrue("the line that is not an entry is mentioned", shows("1 line could not be read as an entry and is left out."))
        assertEquals("nothing is added by looking", 0, entries().size)
        scrollTo("Payment to Jollibee")
        line("Payment to Jollibee").assertIsOn()
        assertTrue(shows("−₱250.00"))
        scrollTo("Payroll November")
        assertTrue(shows("+₱15,000.00"))
        assertTrue("income got an income category", shows("Category: Salary"))

        // Leave the ride out, and correct a category.
        scrollTo("Grab ride")
        line("Grab ride").performClick()
        line("Grab ride").assertIsOff()
        scrollTo("Category: Food")
        rule.onNode(hasText("Category: Food") and hasClickAction()).performClick()
        rule.onNode(hasText("Groceries") and isSelectable()).performClick()
        waitFor("Category: Groceries")

        // The button is in the footer: on screen wherever the list is scrolled.
        rule.onNode(hasText("Import 2 entries") and hasClickAction()).assertIsDisplayed().performClick()
        waitFor("Imported 2 entries into Cash.")
        val added = entries()
        assertEquals(2, added.size)
        val lunch = added.first { it.purpose == TxPurpose.EXPENSE }
        assertEquals(25_000L, lunch.amountCentavos)
        assertEquals("Groceries", lunch.categoryName)
        assertEquals("Payment to Jollibee", lunch.note)
        assertEquals(LocalDate.of(2026, 11, 3), lunch.occurredOn)
        assertEquals(1_500_000L - 25_000L, cashBalance())

        // The import is listed, and the same file again offers only the line that was left out.
        scrollTo("Earlier imports")
        assertTrue(shows("2 entries · Cash"))
        scrollTo("Choose a file")
        tapButton("Choose a file")
        waitFor("3 lines read · 1 new · 2 already imported")
        scrollTo("Payment to Jollibee")
        line("Payment to Jollibee").assertIsOff().assertIsNotEnabled()
        rule.onNode(hasText("Import 1 entry") and hasClickAction()).assertIsDisplayed().assertIsEnabled()
        tapButton("Cancel")
        waitFor("Earlier imports")
        assertEquals(2, entries().size)

        // Taking it back out asks first, in the footer, and removes exactly what it added.
        scrollTo("Take it back out")
        tapButton("Take it back out")
        rule.onNode(hasText("Yes, remove") and hasClickAction()).assertIsDisplayed()
        assertTrue(shows("The 2 entries it added will be removed."))
        tapButton("Yes, remove")
        waitFor("Removed what november.csv had added.")
        assertEquals(0, entries().size)
        assertEquals(0L, cashBalance())
        assertFalse(shows("Earlier imports"))
    }

    @Test
    fun aLineThatMatchesAnEntryTypedByHandIsNotTickedAndTheOwnerDecides() {
        picked = file("november.csv", statement)
        start()
        addExpense(25_000, note = "Lunch", on = LocalDate.of(2026, 11, 3))
        openImport()
        tapButton("Choose a file")
        waitFor("3 lines read · 2 new · 1 looks like an entry you already have")

        scrollTo("Payment to Jollibee")
        line("Payment to Jollibee").assertIsOff().assertIsEnabled()
        assertTrue(shows("Looks like an entry you already have"))
        rule.onNode(hasText("Import 2 entries") and hasClickAction()).assertIsDisplayed().performClick()
        waitFor("Imported 2 entries into Cash.")
        assertEquals("the lunch is there once", 3, entries().size)
    }

    @Test
    fun ifTheWriteFailsNothingOfTheFileIsAddedAndTheScreenSaysSo() {
        picked = file("november.csv", statement)
        start()
        openImport()
        tapButton("Choose a file")
        waitFor("3 lines read · 3 new")

        // Between looking and tapping Import, the first line reaches the ledger another way (the same
        // file imported twice at once would do this). The database refuses a second copy of it.
        runBlocking {
            val food = container.database.categoryDao().observeActive(CategoryKind.EXPENSE).first().first().id
            container.ledger.add(
                TransactionEntity(
                    purpose = TxPurpose.EXPENSE, amountCentavos = 25_000, occurredOn = LocalDate.of(2026, 11, 3), fromWalletId = cashId(),
                    categoryId = food, externalRef = "CSV:${cashId()}:R1:out:25000", createdAt = Instant.now(),
                ),
            )
        }
        tapButton("Import 3 entries")
        waitFor("The import could not be saved. Nothing of that file was added.")

        assertEquals("only the entry that came the other way", 1, entries().size)
        assertEquals(-25_000L, cashBalance())
        assertFalse(shows("Imported 3 entries"))
        rule.onNode(hasText("Import 3 entries") and hasClickAction()).assertIsDisplayed()
    }

    @Test
    fun aFileThatDoesNotSayWhichWayTheMoneyWentAsksAndCanBeFlipped() {
        picked = file("list.csv", "Date,Description,Amount\n2026-11-03,Sideline pay,500\n")
        start()
        openImport()
        tapButton("Choose a file")
        waitFor("list.csv")
        assertTrue(shows("−₱500.00"))
        assertTrue(shows("The file does not say which way the money went."))

        rule.onNode(hasText("These amounts are money coming in") and isToggleable()).assertIsOff().performClick()
        waitFor("+₱500.00")
        rule.onNode(hasText("These amounts are money coming in") and isToggleable()).assertIsOn()
        tapButton("Import 1 entry")
        waitFor("Imported 1 entry into Cash.")
        assertEquals(TxPurpose.INCOME, entries().single().purpose)
        assertEquals(50_000L, cashBalance())
    }

    @Test
    fun aFileThatIsNotATableChangesNothingAndSaysWhy() {
        picked = file("notes.txt", "Remember to buy rice\nand eggs\n")
        start()
        openImport()
        tapButton("Choose a file")
        waitFor("No table was found in that file.")
        assertEquals(0, entries().size)
        assertTrue("still on the first step", shows("Choose a file"))

        picked = file("empty.csv", "")
        tapButton("Choose a file")
        waitFor("That file is empty, or could not be opened.")

        // Far bigger than any statement: refused without being read as a table.
        picked = File(tmp.root, "huge.csv").apply { writeBytes(ByteArray(ImportViewModel.MAX_BYTES + 4_096) { 'a'.code.toByte() }) }
        tapButton("Choose a file")
        waitFor("That file is too big to be a statement")

        picked = file("mixed.csv", "Date,Amount\n25/11/2026,-10\n11/25/2026,-20\n")
        tapButton("Choose a file")
        waitFor("The dates in that file cannot be read for sure")
        assertEquals(0, entries().size)
    }

    /** A statement from a bank whose layout this app does not know. */
    private val unknownBank = listOf("Statement of Account\nTransaction Date Details Amount Balance\n11/03/2026 Sent to Juan Dela Cruz 1,250.00 4,750.00")

    private val gcashPages = listOf(
        """
        GCash Transaction History
        2026-11-01 to 2026-11-26
        Date and Time Description Reference No. Debit Credit Balance
        STARTING BALANCE 500.00
        2026-11-03 10:15 AM Payment to JOLLIBEE QC, Merchant Transaction Number: A1234567BC 1000000000001 250.00 250.00
        2026-11-05 09:30 AM
        Received GCash from MariBank with account ending in 1234 and
        invno:20261105MARIBNK1ABCD123456789012345
        1000000000003 1500.00 1750.00
        ENDING BALANCE 1750.00
        Total Debit 250.00
        Total Credit 1500.00
        """.trimIndent(),
    )

    /** Two transfers of 1,000, one in and one out: nothing in the file says which is which. */
    private val mariBankPages = listOf(
        """
        BANK STATEMENT
        S/N E01-123456ABCDEFGH
        31 OCT 2026
        page 1 of 1
        ACCOUNT SUMMARY
        01 OCT 2026 to 31 OCT 2026
        SAVINGS 10.00 21,000.00 1,500.00 19,510.00
        SAVINGS - TRANSACTION DETAILS
        DATE TRANSACTION OUTGOING (PHP) INCOMING (PHP)
        02 OCT Via Other Banks Channel
        Transfer
        20,000.00
        03 OCT Shopee
        Payment
        500.00
        05 OCT Tita Baby
        Transfer
        1,000.00
        06 OCT Kuya Jun
        Transfer
        1,000.00
        SAVINGS - INTEREST & TAX DETAILS*
        01 OCT 10.00 0.00 0.00 0.00
        """.trimIndent(),
    )

    private class OpenPdf(private val pages: List<String>) : PdfTextReader {
        override suspend fun read(bytes: ByteArray, password: String?): PdfText = PdfText.Pages(pages)
    }

    @Test
    fun aGcashPdfIsReadCheckedAgainstItsOwnTotalsAndImported() {
        picked = file("gcash.pdf", "%PDF-1.7 stand-in")
        start()
        container.pdfTextReader = OpenPdf(gcashPages)
        openImport()
        tapButton("Choose a file")
        waitFor("gcash.pdf")

        assertTrue(shows("2 lines read · 2 new"))
        assertTrue(shows("Adds up to the statement's own totals: ₱1,500.00 in, ₱250.00 out."))
        scrollTo("Payment to JOLLIBEE QC")
        assertTrue(shows("−₱250.00"))
        scrollTo("Received GCash from MariBank")
        assertTrue(shows("+₱1,500.00"))
        rule.onNode(hasText("Import 2 entries") and hasClickAction()).assertIsDisplayed().assertIsEnabled().performClick()
        waitFor("Imported 2 entries into Cash.")
        assertEquals(150_000L - 25_000L, cashBalance())
        assertEquals(setOf(TxPurpose.EXPENSE, TxPurpose.INCOME), entries().map { it.purpose }.toSet())
        assertEquals(
            "stored under GCash's own reference number, as a GCash line",
            setOf("GCASH:${cashId()}:1000000000001:out:25000"),
            runBlocking { container.ledger.knownRefs(listOf("GCASH:${cashId()}:1000000000001:out:25000", "CSV:${cashId()}:1000000000001:out:25000")) },
        )

        // The same statement again: both lines are known by their GCash reference numbers.
        scrollTo("Choose a file")
        tapButton("Choose a file")
        waitFor("2 lines read · 0 new · 2 already imported")
        rule.onNode(hasText("Import 0 entries") and hasClickAction()).assertIsNotEnabled()
    }

    @Test
    fun aMariBankPdfWaitsForTheOwnerWhereTheStatementCannotSayWhichWayALineWent() {
        picked = file("maribank.pdf", "%PDF-1.7 stand-in")
        start()
        container.pdfTextReader = OpenPdf(mariBankPages)
        openImport()
        tapButton("Choose a file")
        waitFor("maribank.pdf")

        // The 20,000 and the Shopee payment are settled by the totals; the two transfers of 1,000 are not.
        assertTrue(shows("4 lines read · 4 new"))
        assertTrue(shows("2 lines could have gone either way"))
        rule.onNode(hasText("Import 4 entries") and hasClickAction()).assertIsDisplayed().assertIsNotEnabled()
        assertEquals("nothing is added while a line is open", 0, entries().size)

        // The owner knows: Tita Baby sent that 1,000. Saying so settles the other one too.
        scrollTo("Tita Baby")
        assertTrue(shows("Check which way this went"))
        // (Several lines read "Category: Other"; the one wanted is the one in Tita Baby's card.)
        rule.onNode(hasText("Category: Other") and hasClickAction() and hasAnySibling(hasText("Tita Baby", substring = true))).performClick()
        rule.onNode(hasText("Money in") and isSelectable()).performClick()
        rule.waitUntil(10_000) { !shows("could have gone either way") }
        assertTrue(shows("Adds up to the statement's own totals: ₱21,000.00 in, ₱1,500.00 out."))
        assertFalse(shows("Check which way this went"))

        // A direction that cannot be right (the Shopee payment as money in) breaks the totals: Import goes
        // off and the screen says by how much, until it is put right.
        rule.onNode(hasText("Category: Other") and hasClickAction() and hasAnySibling(hasText("Shopee", substring = true))).performClick()
        rule.onNode(hasText("Money in") and isSelectable()).performClick()
        waitFor("do not add up to the statement's totals yet")
        assertTrue(shows("Money in is ₱21,500.00 of ₱21,000.00; money out is ₱1,000.00 of ₱1,500.00."))
        rule.onNode(hasText("Import 4 entries") and hasClickAction()).assertIsNotEnabled()
        rule.onNode(hasText("Money out") and isSelectable()).performClick()
        waitFor("Adds up to the statement's own totals")

        rule.onNode(hasText("Import 4 entries") and hasClickAction()).assertIsEnabled().performClick()
        waitFor("Imported 4 entries into Cash.")
        assertEquals(2_100_000L - 150_000L, cashBalance())
        val notes = entries().associate { it.note to it.purpose }
        assertEquals(TxPurpose.INCOME, notes["Tita Baby · Transfer"])
        assertEquals(TxPurpose.EXPENSE, notes["Kuya Jun · Transfer"])
    }

    @Test
    fun aLockedPdfStatementIsOpenedWithItsPasswordAndShownWithEverythingHidden() {
        val pdf = LockedPdf(password = "delacruz1234", pages = unknownBank)
        picked = file("statement.pdf", "%PDF-1.7 not really a pdf")
        start()
        container.pdfTextReader = pdf
        openImport()
        tapButton("Choose a file")
        waitFor("This file is locked. Type its password to open it.")
        assertTrue(shows("your last name in small letters"))

        field(0).performTextInput("wrong")
        tapButton("Open")
        waitFor("That password did not open the file")

        field(0).performTextInput("x")   // still wrong: "wrongx"
        tapButton("Open")
        rule.waitUntil(10_000) { pdf.tried.size == 3 }
        assertEquals(listOf(null, "wrong", "wrongx"), pdf.tried)

        // The stand-in accepts whatever the test now says is the password.
        val opened = LockedPdf(password = "wrongx", pages = unknownBank)
        container.pdfTextReader = opened
        rule.onNodeWithContentDescription("Back").performClick()
        waitFor("Money set-up")
        tapButton("Import")
        waitFor("Which wallet is the file for")
        tapButton("Choose a file")
        waitFor("This file is locked.")
        field(0).performTextInput("wrongx")
        tapButton("Open")

        waitFor("statement.pdf · 1 page")
        assertTrue(shows("cannot bring in entries from this PDF yet"))
        scrollTo("Transaction Date Details")
        assertTrue("the column words are kept", shows("Transaction Date Details Amount Balance"))
        assertTrue("everything else is hidden", shows("99/99/9999 Xxxx xx Xxxx Xxxx Xxxx 9,999.99 9,999.99"))
        assertFalse(shows("Juan"))
        assertFalse(shows("1,250.00"))

        scrollTo("Copy the layout")
        tapButton("Copy the layout")
        val copied = rule.activity.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString()
        assertTrue(copied.startsWith("— Page 1 (3 lines) —"))
        assertFalse("nothing private leaves the app", "Juan" in copied || "1,250" in copied || "4,750" in copied)
        assertEquals("a PDF adds nothing", 0, entries().size)
    }
}
