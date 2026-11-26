package com.pitakaview.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CsvExportTest {
    private val day = LocalDate.of(2026, 11, 26)

    private fun row(
        purpose: TxPurpose = TxPurpose.EXPENSE,
        amount: Long = 25_000,
        note: String? = null,
        category: String? = "Food",
        from: String? = "Cash",
        to: String? = null,
        loan: String? = null,
        goal: String? = null,
        baon: String? = null,
        payday: String? = null,
    ) = ExportRow(1, day, purpose, amount, note, category, from, to, loan, goal, baon, payday)

    private fun write(vararg rows: ExportRow): String = StringBuilder().also { CsvExport.write(rows.toList(), it) { purpose -> purpose.name } }.toString()

    private fun lines(vararg rows: ExportRow): List<String> = write(*rows).removePrefix(CsvExport.BYTE_ORDER_MARK).split("\r\n")

    @Test
    fun theFileOpensWithTheMarkAndTheHeaderAndEveryLineEndsTheSpreadsheetWay() {
        val text = write(row(), row())
        assertTrue("the mark that tells Excel the text is UTF-8", text.startsWith("﻿"))
        val lines = text.removePrefix("﻿").split("\r\n")
        assertEquals("Date,Type,Category,Amount,In or out,From wallet,To wallet,For,Note", lines[0])
        assertEquals("a header, two rows, and the empty piece after the last line end", 4, lines.size)
        assertEquals("", lines.last())
        assertEquals(2, CsvExport.write(listOf(row(), row()), StringBuilder()) { it.name })
    }

    @Test
    fun spendingIncomeAndAMoveEachSayWhichWayTheMoneyWent() {
        val out = lines(
            row(),
            row(TxPurpose.INCOME, 1_500_000, note = "kinsenas", category = "Salary", from = null, to = "GCash", payday = "Sahod"),
            row(TxPurpose.TRANSFER, 30_000, category = null, from = "MariBank", to = "GCash"),
        )
        assertEquals("2026-11-26,EXPENSE,Food,250.00,out,Cash,,,", out[1])
        assertEquals("2026-11-26,INCOME,Salary,15000.00,in,,GCash,Sahod,kinsenas", out[2])
        assertEquals("2026-11-26,TRANSFER,,300.00,move,MariBank,GCash,,", out[3])
    }

    @Test
    fun whatAnEntryBelongsToIsNamedInTheForColumn() {
        fun forColumn(r: ExportRow) = lines(r)[1].split(",")[7]
        assertEquals("Tita Baby", forColumn(row(TxPurpose.LOAN_PAYMENT, category = null, loan = "Tita Baby")))
        assertEquals("Laptop", forColumn(row(TxPurpose.SAVINGS_DEPOSIT, category = null, to = "Cash", goal = "Laptop")))
        assertEquals("My baon", forColumn(row(baon = "My baon")))
        assertEquals("Sahod", forColumn(row(TxPurpose.INCOME, from = null, to = "Cash", payday = "Sahod")))
        assertEquals("", forColumn(row()))
    }

    @Test
    fun amountsArePlainNumbersASpreadsheetCanAddUp() {
        assertEquals("0.05", CsvExport.amount(5))
        assertEquals("1.00", CsvExport.amount(100))
        assertEquals("1234.56", CsvExport.amount(123_456))
        assertEquals("no thousands commas, which would split the cell", "1500000.00", CsvExport.amount(150_000_000))
    }

    @Test
    fun textWithACommaAQuoteOrALineBreakIsWrappedSoItStaysOneCell() {
        assertEquals("plain", CsvExport.text("plain"))
        assertEquals("\"lunch, with Tita\"", CsvExport.text("lunch, with Tita"))
        assertEquals("\"she said \"\"libre\"\"\"", CsvExport.text("she said \"libre\""))
        assertEquals("\"two\nlines\"", CsvExport.text("two\nlines"))
        assertEquals("2026-11-26,EXPENSE,Food,250.00,out,Cash,,,\"lunch, with Tita\"", lines(row(note = "lunch, with Tita"))[1])
    }

    @Test
    fun textASpreadsheetWouldRunAsAFormulaIsShownAsTyped() {
        assertEquals("'=1+1", CsvExport.text("=1+1"))
        assertEquals("'+639171234567", CsvExport.text("+639171234567"))
        assertEquals("'-5 discount", CsvExport.text("-5 discount"))
        assertEquals("'@home", CsvExport.text("@home"))
        assertEquals("only the first character matters", "a=b", CsvExport.text("a=b"))
        assertEquals("defused first, then quoted for its comma", "\"'=SUM(A1,B1)\"", CsvExport.text("=SUM(A1,B1)"))
        assertEquals("", CsvExport.text(""))
    }
}
