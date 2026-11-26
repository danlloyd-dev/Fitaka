package com.pitakaview.app.domain.importing

import com.pitakaview.app.domain.ImportSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Reading GCash's and MariBank's PDF statements. The pages below have exactly the line structure of
 * the owner's own files (learned on 2026-11-26 with every letter and digit hidden); the names and
 * amounts are made up.
 */
class PdfStatementsTest {
    private fun day(month: Int, day: Int) = LocalDate.of(2026, month, day)

    private fun rows(read: StatementRead) = (read as StatementRead.Rows)

    // ---------- working out the direction from totals ----------

    @Test
    fun theTotalsSettleWhichLinesAreMoneyIn() {
        // 100 + 250 came in, 40 + 15 went out.
        val solved = DirectionSolver.solve(listOf(10_000, 4_000, 25_000, 1_500), listOf(null, null, null, null), StatementTotals(35_000, 5_500))!!
        assertEquals(listOf(true, false, true, false), solved.map { it.moneyIn })
        assertTrue(solved.all { it.sure })
    }

    @Test
    fun aLineThatCouldGoEitherWayIsLeftOpenAndOnlyThatLine() {
        // Two lines of 50: one came in and one went out, and the totals cannot say which is which.
        val solved = DirectionSolver.solve(listOf(5_000, 5_000, 20_000), listOf(null, null, null), StatementTotals(25_000, 5_000))!!
        assertEquals("the 200 is settled", Direction(moneyIn = true, sure = true), solved[2])
        assertFalse(solved[0].sure)
        assertFalse(solved[1].sure)
        assertTrue("the guess itself still adds up", solved[0].moneyIn != solved[1].moneyIn)
        // Told which one came in, the other is settled too.
        val told = DirectionSolver.solve(listOf(5_000, 5_000, 20_000), listOf(true, null, null), StatementTotals(25_000, 5_000))!!
        assertEquals(listOf(Direction(true, true), Direction(false, true), Direction(true, true)), told)
    }

    @Test
    fun totalsThatCannotBeReachedGiveNoAnswer() {
        assertNull("the lines do not even sum to the totals", DirectionSolver.solve(listOf(10_000, 4_000), listOf(null, null), StatementTotals(10_000, 5_000)))
        assertNull("no split makes 70 in", DirectionSolver.solve(listOf(10_000, 4_000), listOf(null, null), StatementTotals(7_000, 7_000)))
        assertNull("a fact that contradicts the totals", DirectionSolver.solve(listOf(10_000, 4_000), listOf(false, null), StatementTotals(10_000, 4_000)))
        assertEquals(emptyList<Direction>(), DirectionSolver.solve(emptyList(), emptyList(), StatementTotals(0, 0)))
    }

    @Test
    fun tooManyOpenLinesAreLeftForTheOwnerInsteadOfTried() {
        val amounts = (1..DirectionSolver.MAX_OPEN + 1).map { 100L * it }
        val solved = DirectionSolver.solve(amounts, amounts.map { null }, StatementTotals(amounts.sum(), 0))!!
        assertTrue(solved.none { it.sure })
    }

    // ---------- GCash ----------

    private val gcashPage = """
        GCash Transaction History
        2026-11-01 to 2026-11-26
        Date and Time Description Reference No. Debit Credit Balance
        STARTING BALANCE 500.00
        2026-11-03 10:15 AM Payment to JOLLIBEE QC, Merchant Transaction Number: A1234567BC 1000000000001 250.00 250.00
        2026-11-04 08:05 PM Transfer from 09171234567 to 09181234567 1000000000002 50.00 200.00
        2026-11-05 09:30 AM
        Received GCash from MariBank with account ending in 1234 and
        invno:20261105MARIBNK1ABCD123456789012345
        1000000000003 1500.00 1700.00
        ENDING BALANCE 1700.00
        Total Debit 300.00
        Total Credit 1500.00
    """.trimIndent()

    @Test
    fun aGcashStatementIsReadWithItsDirectionsTakenFromTheBalance() {
        val read = rows(PdfStatementReader.read(listOf(gcashPage)))
        assertEquals(ImportSource.GCASH, read.source)
        assertEquals(
            listOf(
                StatementRow(day(11, 3), "Payment to JOLLIBEE QC, Merchant Transaction Number: A1234567BC", "1000000000001", 25_000, moneyIn = false),
                StatementRow(day(11, 4), "Transfer from 09171234567 to 09181234567", "1000000000002", 5_000, moneyIn = false),
                StatementRow(
                    day(11, 5), "Received GCash from MariBank with account ending in 1234 and invno:20261105MARIBNK1ABCD123456789012345",
                    "1000000000003", 150_000, moneyIn = true,
                ),
            ),
            read.rows,
        )
        assertTrue("every direction is settled", read.rows.all { it.sure })
        assertEquals(StatementTotals(moneyInCentavos = 150_000, moneyOutCentavos = 30_000), read.totals)
        assertEquals(0, read.unreadLines)
    }

    @Test
    fun aGcashStatementOverTwoPagesOrNewestFirstReadsTheSame() {
        val lines = gcashPage.lines()
        // The table's heading again at the top of page 2, between two entries.
        val twoPages = listOf(lines.take(6).joinToString("\n"), (listOf(lines[2]) + lines.drop(6)).joinToString("\n"))
        assertEquals(rows(PdfStatementReader.read(listOf(gcashPage))).rows, rows(PdfStatementReader.read(twoPages)).rows)

        val newestFirst = """
            Date and Time Description Reference No. Debit Credit Balance
            2026-11-05 09:30 AM Cash in 1000000000003 1500.00 1700.00
            2026-11-04 08:05 PM Send money 1000000000002 50.00 200.00
            2026-11-03 10:15 AM Payment 1000000000001 250.00 250.00
            STARTING BALANCE 500.00
        """.trimIndent()
        assertEquals(listOf(true, false, false), rows(PdfStatementReader.read(listOf(newestFirst))).rows.map { it.moneyIn })
    }

    @Test
    fun aGcashLineTheBalanceCannotSettleIsSettledByTheTotalsOrLeftOpen() {
        // No starting balance, so the first line has nothing to be compared with; the totals settle it.
        val noStart = gcashPage.lines().filterNot { it.startsWith("STARTING BALANCE") }.joinToString("\n")
        val read = rows(PdfStatementReader.read(listOf(noStart)))
        assertEquals(listOf(false, false, true), read.rows.map { it.moneyIn })
        assertTrue(read.rows.all { it.sure })

        // Neither a starting balance nor totals: the first line is a guess, and says so.
        val bare = noStart.lines().filterNot { it.startsWith("Total ") }.joinToString("\n")
        val open = rows(PdfStatementReader.read(listOf(bare)))
        assertNull(open.totals)
        assertFalse(open.rows[0].sure)
        assertTrue(open.rows[1].sure && open.rows[2].sure)
    }

    @Test
    fun aGcashEntryThatDoesNotEndInItsNumbersIsCountedAsUnread() {
        val broken = gcashPage.replace("1000000000002 50.00 200.00", "torn off here")
        val read = rows(PdfStatementReader.read(listOf(broken)))
        assertEquals(2, read.rows.size)
        assertEquals(1, read.unreadLines)
    }

    // ---------- MariBank ----------

    private val mariHead = "BANK STATEMENT\nS/N E01-123456ABCDEFGH\n31 OCT 2026\n"

    private val mariPage1 = mariHead + """
        page 1 of 3
        ACCOUNT SUMMARY
        01 OCT 2026 to 31 OCT 2026
        ACCOUNT STARTING BALANCE
        (PHP)
        TOTAL INCOMING
        (PHP)
        TOTAL OUTGOING
        (PHP)
        ENDING BALANCE
        (PHP)
        SAVINGS 10.00 21,000.45 19,500.00 1,510.45
        TOTAL: 1,510.45
        SAVINGS - TRANSACTION DETAILS
        DATE TRANSACTION OUTGOING (PHP) INCOMING (PHP)
        OCT Interest
        Net Interest
        0.45
        02 OCT Via Other Banks Channel
        Transfer
        20,000.00
        03 OCT Shopee
        Payment
        500.00
        BSP REGULATED ENTITY NOTICES
        CUSTOMER ACCOUNT: 12345678901
        Contact Us
        Call (+632) 1234 5678
        Email help@maribank.com.ph
    """.trimIndent()

    private val mariPage2 = mariHead + """
        page 2 of 3
        DATE TRANSACTION OUTGOING (PHP) INCOMING (PHP)
        05 OCT Via Other Banks Channel
        Transfer
        1,000.00
        09 OCT ATM Cash Withdrawal
        Cash Withdrawal
        19,000.00
        SAVINGS - INTEREST & TAX DETAILS*
        DATE INTEREST END BALANCE
        (PHP)
        GROSS INTEREST
        (PHP)
        WITHHOLDING TAX
        (PHP)
        INTEREST
        (PHP)
        01 OCT 10.00 0.01 0.00 0.01
        02 OCT 20,010.00 0.56 -0.11 0.45
    """.trimIndent()

    private val mariPage3 = mariHead + """
        page 3 of 3
        DATE INTEREST END BALANCE
        (PHP)
        03 OCT 19,510.00 0.00 0.00 0.00
        TOTAL INTEREST(NET): 0.45
        Remarks
        This is a system-generated e-Statement and does not require any signature.
    """.trimIndent()

    @Test
    fun aMariBankStatementIsReadAndItsDirectionsWorkedOutFromItsTotals() {
        val read = rows(PdfStatementReader.read(listOf(mariPage1, mariPage2, mariPage3)))
        assertEquals(ImportSource.MARIBANK, read.source)
        // 0.45 + 20,000 + 1,000 came in; 500 + 19,000 went out.
        assertEquals(StatementTotals(moneyInCentavos = 2_100_045, moneyOutCentavos = 1_950_000), read.totals)
        assertEquals(
            listOf(
                // The month's interest has no day: it is dated at the end of its month.
                StatementRow(day(10, 31), "Interest · Net Interest", "E01-123456ABCDEFGH#1", 45, moneyIn = true),
                StatementRow(day(10, 2), "Via Other Banks Channel · Transfer", "E01-123456ABCDEFGH#2", 2_000_000, moneyIn = true),
                StatementRow(day(10, 3), "Shopee · Payment", "E01-123456ABCDEFGH#3", 50_000, moneyIn = false),
                StatementRow(day(10, 5), "Via Other Banks Channel · Transfer", "E01-123456ABCDEFGH#4", 100_000, moneyIn = true),
                StatementRow(day(10, 9), "ATM Cash Withdrawal · Cash Withdrawal", "E01-123456ABCDEFGH#5", 1_900_000, moneyIn = false),
            ),
            read.rows,
        )
        assertEquals("the page's foot and the daily interest table are not entries", 0, read.unreadLines)
        // "Transfer" says nothing about the direction; the totals settled both of them.
        assertTrue(read.rows.all { it.sure })
    }

    @Test
    fun whichMariBankTotalIsMoneyInIsToldByTheArithmeticNotTheColumnNames() {
        // The same statement with the two totals the other way round in the summary, and names this app does not know.
        val swapped = mariPage1
            .replace("TOTAL INCOMING", "TOTAL PALABAS").replace("TOTAL OUTGOING", "TOTAL PAPASOK")
            .replace("SAVINGS 10.00 21,000.45 19,500.00 1,510.45", "SAVINGS 10.00 19,500.00 21,000.45 1,510.45")
        val read = rows(PdfStatementReader.read(listOf(swapped, mariPage2, mariPage3)))
        assertEquals(StatementTotals(moneyInCentavos = 2_100_045, moneyOutCentavos = 1_950_000), read.totals)
        assertEquals(listOf(true, true, false, true, false), read.rows.map { it.moneyIn })
    }

    @Test
    fun wordsThatMisleadDoNotBeatTheTotals() {
        // A payment that was really a refund coming in: the word says out, the totals say in.
        // With the Shopee 500 coming in: 21,500.45 in and 19,000.00 out.
        val page1 = mariPage1.replace("SAVINGS 10.00 21,000.45 19,500.00 1,510.45", "SAVINGS 10.00 21,500.45 19,000.00 2,510.45")
        val read = rows(PdfStatementReader.read(listOf(page1, mariPage2, mariPage3)))
        assertEquals(StatementTotals(2_150_045, 1_900_000), read.totals)
        val inSum = read.rows.filter { it.moneyIn }.sumOf { it.amountCentavos }
        val outSum = read.rows.filterNot { it.moneyIn }.sumOf { it.amountCentavos }
        assertEquals("whatever the words suggested, the lines add up to the statement's totals", 2_150_045L to 1_900_000L, inSum to outSum)
        assertTrue("the Shopee line came in", read.rows[2].moneyIn)
    }

    @Test
    fun twoMariBankLinesOfTheSameAmountAreLeftForTheOwner() {
        // 1,000 in on the 5th and 1,000 out on the 6th: nothing in the file says which is which.
        val page2 = mariPage2.replace("09 OCT ATM Cash Withdrawal", "06 OCT Via Other Banks Channel\nTransfer\n1,000.00\n09 OCT ATM Cash Withdrawal")
        // One more 1,000 going out: 21,000.45 in and 20,500.00 out.
        val page1 = mariPage1.replace("SAVINGS 10.00 21,000.45 19,500.00 1,510.45", "SAVINGS 10.00 21,000.45 20,500.00 510.45")
        val read = rows(PdfStatementReader.read(listOf(page1, page2, mariPage3)))
        assertEquals(6, read.rows.size)
        assertEquals("only the two equal lines are open", listOf(3, 4), read.rows.indices.filter { !read.rows[it].sure })
    }

    @Test
    fun aMariBankStatementWithoutASummaryStillReadsButNothingIsSettled() {
        val noSummary = mariPage1.replace("SAVINGS 10.00 21,000.45 19,500.00 1,510.45\n", "")
        val read = rows(PdfStatementReader.read(listOf(noSummary, mariPage2, mariPage3)))
        assertNull(read.totals)
        assertEquals(5, read.rows.size)
        assertTrue("with nothing to check against, every line waits for the owner", read.rows.none { it.sure })
    }

    @Test
    fun aDecemberToJanuaryStatementPutsEachEntryInItsOwnYear() {
        val page = mariHead + """
            page 1 of 1
            15 DEC 2026 to 14 JAN 2027
            SAVINGS 0.00 300.00 0.00 300.00
            SAVINGS - TRANSACTION DETAILS
            DATE TRANSACTION OUTGOING (PHP) INCOMING (PHP)
            20 DEC Cash In
            Deposit
            100.00
            05 JAN Cash In
            Deposit
            200.00
        """.trimIndent()
        assertEquals(listOf(LocalDate.of(2026, 12, 20), LocalDate.of(2027, 1, 5)), rows(PdfStatementReader.read(listOf(page))).rows.map { it.date })
    }

    // ---------- anything else ----------

    @Test
    fun pagesThatAreNotAKnownStatementAreNotGuessedAt() {
        assertEquals(StatementRead.Unreadable(UnreadableReason.NO_HEADER), PdfStatementReader.read(listOf("Hello\nThis is a letter, not a statement.")))
        assertEquals(StatementRead.Unreadable(UnreadableReason.NO_HEADER), PdfStatementReader.read(emptyList()))
    }
}
