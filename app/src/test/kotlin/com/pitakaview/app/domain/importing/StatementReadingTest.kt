package com.pitakaview.app.domain.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Reading statement files: CSV text, the dates and amounts banks write, and hiding what a file says. */
class StatementReadingTest {
    private fun rows(text: String) = (CsvStatementReader.read(text) as StatementRead.Rows)

    private fun day(month: Int, day: Int) = LocalDate.of(2026, month, day)

    // ---------- CSV ----------

    @Test
    fun csvCellsMayHoldCommasQuotesAndLineBreaks() {
        val text = "﻿Date,Note,Amount\r\n2026-11-03,\"Lunch, with \"\"Tita\"\"\",250.00\r\n2026-11-04,\"two\nlines\",10\n\n,,\n"
        assertEquals(
            listOf(
                listOf("Date", "Note", "Amount"),
                listOf("2026-11-03", "Lunch, with \"Tita\"", "250.00"),
                listOf("2026-11-04", "two\nlines", "10"),
            ),
            Csv.parse(text),
        )
        assertEquals("a file that does not end in a line break keeps its last line", listOf(listOf("a", "b")), Csv.parse("a,b"))
        assertEquals("old Mac line ends", listOf(listOf("a"), listOf("b")), Csv.parse("a\rb\r"))
    }

    @Test
    fun theSeparatorIsWorkedOutFromTheFirstLine() {
        assertEquals(';', Csv.detectDelimiter("Date;Details;Amount\n1;2;3"))
        assertEquals('\t', Csv.detectDelimiter("Date\tDetails\tAmount"))
        assertEquals(',', Csv.detectDelimiter("Date,Details,Amount"))
        assertEquals(listOf(listOf("Date", "Amount"), listOf("2026-11-03", "1,5")), Csv.parse("Date;Amount\n2026-11-03;1,5"))
    }

    // ---------- amounts and dates ----------

    @Test
    fun amountsAreReadTheWaysStatementsWriteThem() {
        assertEquals(123_456L, StatementAmounts.parse("1,234.56"))
        assertEquals(25_000L, StatementAmounts.parse("₱ 250"))
        assertEquals(2_000L, StatementAmounts.parse("PHP 20.00"))
        assertEquals(2_000L, StatementAmounts.parse("+20"))
        assertEquals(-25_000L, StatementAmounts.parse("-250.00"))
        assertEquals(-25_000L, StatementAmounts.parse("(250.00)"))
        assertEquals(-25_000L, StatementAmounts.parse("−₱250.00"))
        assertNull(StatementAmounts.parse(""))
        assertNull(StatementAmounts.parse("  "))
        assertNull(StatementAmounts.parse("n/a"))
        assertNull("three decimals is not money", StatementAmounts.parse("1.234"))
    }

    @Test
    fun datesAreReadTheWaysStatementsWriteThem() {
        val nov3 = day(11, 3)
        assertEquals(nov3, StatementDates.parse("2026-11-03", dayFirst = false))
        assertEquals("a time after the date is ignored", nov3, StatementDates.parse("2026-11-03 10:15 PM", dayFirst = false))
        assertEquals(nov3, StatementDates.parse("2026/11/3", dayFirst = false))
        assertEquals(nov3, StatementDates.parse("11/03/2026", dayFirst = false))
        assertEquals(nov3, StatementDates.parse("03/11/2026", dayFirst = true))
        assertEquals(nov3, StatementDates.parse("11/3/26", dayFirst = false))
        assertEquals(nov3, StatementDates.parse("Nov 3, 2026", dayFirst = false))
        assertEquals(nov3, StatementDates.parse("November 3 2026 08:00", dayFirst = false))
        assertEquals(nov3, StatementDates.parse("3 Nov 2026", dayFirst = false))
        assertEquals(nov3, StatementDates.parse("03-Nov-2026", dayFirst = false))
        assertNull(StatementDates.parse("Total", dayFirst = false))
        assertNull("no such day", StatementDates.parse("2026-02-30", dayFirst = false))
        assertNull(StatementDates.parse("13/13/2026", dayFirst = false))
    }

    // ---------- whole files ----------

    @Test
    fun aStatementWithDebitAndCreditColumnsIsReadWithItsReferences() {
        val read = rows(
            """
            My Bank Statement
            Account: 0917-xxx-1234

            Date and Time,Description,Reference No.,Debit,Credit,Balance
            2026-11-03 10:15,Payment to Jollibee,1000000000001,250.00,,"4,750.00"
            2026-11-05 08:00,Cash in from bank,1000000000002,,"1,500.00","6,250.00"
            Ending balance,,,,,"6,250.00"
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                StatementRow(day(11, 3), "Payment to Jollibee", "1000000000001", 25_000, moneyIn = false),
                StatementRow(day(11, 5), "Cash in from bank", "1000000000002", 150_000, moneyIn = true),
            ),
            read.rows,
        )
        assertEquals("the line with no date is counted, not dropped in silence", 1, read.unreadLines)
        assertTrue(read.directionKnown)
        assertNull("no date in this file needs settling", read.dayFirst)
    }

    @Test
    fun aSignedAmountColumnGivesTheDirection() {
        val read = rows("Date,Details,Amount\n11/03/2026,Grab,-180.00\n11/04/2026,Refund,180.00\n11/05/2026,Fee,(15.00)\n")
        assertEquals(listOf(false, true, false), read.rows.map { it.moneyIn })
        assertEquals(listOf(18_000L, 18_000L, 1_500L), read.rows.map { it.amountCentavos })
        assertTrue(read.directionKnown)
        assertNull("no reference column", read.rows.first().reference)
    }

    @Test
    fun anUnsignedAmountColumnIsReadAsMoneyOutAndSaysItDoesNotKnow() {
        val read = rows("Date,Description,Amount\n2026-11-03,Lunch,120\n2026-11-04,Fare,25.50\n")
        assertFalse(read.directionKnown)
        assertEquals(listOf(false, false), read.rows.map { it.moneyIn })
        assertEquals(listOf(true, true), ImportPlanner.flipped(read.rows).map { it.moneyIn })
    }

    @Test
    fun dayFirstOrMonthFirstIsSettledOnceForTheWholeFile() {
        // 25/11 can only be day first, so 03/11 in the same file is the 3rd of November.
        val dayFirst = rows("Date,Amount\n03/11/2026,-10\n25/11/2026,-20\n")
        assertEquals(true, dayFirst.dayFirst)
        assertEquals(listOf(day(11, 3), day(11, 25)), dayFirst.rows.map { it.date })

        val monthFirst = rows("Date,Amount\n11/03/2026,-10\n11/25/2026,-20\n")
        assertEquals(false, monthFirst.dayFirst)
        assertEquals(listOf(day(11, 3), day(11, 25)), monthFirst.rows.map { it.date })

        // Nothing settles it: read month first (the usual way here), and the screen says so.
        val unknown = rows("Date,Amount\n03/04/2026,-10\n")
        assertNull(unknown.dayFirst)
        assertEquals(day(3, 4), unknown.rows.single().date)

        assertEquals(
            StatementRead.Unreadable(UnreadableReason.MIXED_DATES),
            CsvStatementReader.read("Date,Amount\n25/11/2026,-10\n11/25/2026,-20\n"),
        )
    }

    @Test
    fun theAppsOwnSpreadsheetExportReadsBackWithoutItsMoves() {
        val read = rows(
            "Date,Type,Category,Amount,In or out,From wallet,To wallet,For,Note\r\n" +
                "2026-11-03,Spending,Food,250.00,out,Cash,,,Lunch\r\n" +
                "2026-11-04,Income,Salary,15000.00,in,,GCash,,\r\n" +
                "2026-11-05,Move between wallets,,500.00,move,Cash,GCash,,\r\n",
        )
        assertEquals(
            listOf(
                StatementRow(day(11, 3), "Lunch", null, 25_000, moneyIn = false),
                StatementRow(day(11, 4), "Salary", null, 1_500_000, moneyIn = true),
            ),
            read.rows,
        )
        assertEquals("a move between the owner's own wallets is not income or spending", 1, read.movesLeftOut)
        assertEquals(0, read.unreadLines)
    }

    @Test
    fun linesThatCannotBeAnEntryAreCounted() {
        val read = rows(
            "Date,Description,Debit,Credit\n" +
                "2026-11-03,Both filled,10,10\n" +
                "2026-11-03,Neither filled,,\n" +
                "not a date,Bad date,10,\n" +
                "2026-11-03,Fine,10,\n",
        )
        assertEquals(1, read.rows.size)
        assertEquals(3, read.unreadLines)
    }

    @Test
    fun aFileWithNoTableIsNotGuessedAt() {
        assertEquals(StatementRead.Unreadable(UnreadableReason.EMPTY), CsvStatementReader.read(""))
        assertEquals(StatementRead.Unreadable(UnreadableReason.EMPTY), CsvStatementReader.read("\n\n ,, \n"))
        assertEquals(StatementRead.Unreadable(UnreadableReason.NO_HEADER), CsvStatementReader.read("Hello,World\n1,2\n"))
        assertEquals("a date column alone is not enough", StatementRead.Unreadable(UnreadableReason.NO_HEADER), CsvStatementReader.read("Date,Note\n2026-11-03,x\n"))
    }

    // ---------- hiding what a file says ----------

    @Test
    fun maskingHidesEveryNameAndNumberButKeepsTheLayout() {
        val line = "2026-11-03 10:15 PM  Payment to Juan Dela Cruz  Reference No. 1000000000001  Debit 1,250.00  Balance 4,750.00"
        val masked = TextMask.mask(line)
        assertEquals("9999-99-99 99:99 XX  Xxxxxxx xx Xxxx Xxxx Xxxx  Reference No. 9999999999999  Debit 9,999.99  Balance 9,999.99", masked)
        assertEquals("same length, so columns still line up", line.length, masked.length)
        assertFalse(masked.any { it.isDigit() && it != '9' })
        listOf("Juan", "Dela", "Cruz", "1250", "4,750").forEach { assertFalse(it, it in masked) }
    }

    @Test
    fun theSampleShowsOnlyTheStartOfEachPage() {
        val page = (1..200).joinToString("\n") { "Line $it Juan" }
        val sample = TextMask.sample(listOf(page, "Date Balance\n\nJuan 5"))
        assertTrue(sample.startsWith("— Page 1 (200 lines) —\nXxxx 9 Xxxx"))
        assertTrue("… ${200 - TextMask.MAX_LINES} more lines" in sample)
        assertTrue("— Page 2 (2 lines) —\nDate Balance\nXxxx 9" in sample)
        assertFalse("Juan" in sample)
        // The page's title line, the first MAX_LINES lines, and the "… more lines" line.
        assertEquals(1 + TextMask.MAX_LINES + 1, sample.substringBefore("\n\n— Page 2").lines().size)
    }
}
