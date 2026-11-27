package com.pitakaview.app.domain.importing

import com.pitakaview.app.domain.ImportSource
import com.pitakaview.app.domain.Peso
import java.time.DateTimeException
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/** One line of a statement, however the file wrote it: when, what, how much, and which way the money went. */
data class StatementRow(
    val date: LocalDate,
    val description: String,
    /** The bank's own number for the transaction, when the file has one. It is what stops a second import of the same line. */
    val reference: String?,
    /** Always positive. */
    val amountCentavos: Long,
    val moneyIn: Boolean,
    /**
     * False when [moneyIn] is only the best guess: the file does not say which way this line went and
     * its totals leave it open. The review screen then asks the owner, and will not import before that.
     */
    val sure: Boolean = true,
    /**
     * The wallet the file itself names for this line: the app's own export has one per line, a bank's file
     * has none. A line named for another wallet than the one being imported into is not brought in.
     */
    val walletName: String? = null,
)

enum class UnreadableReason {
    /** No lines at all. */
    EMPTY,

    /** No row names a date column together with an amount column. */
    NO_HEADER,

    /** Some dates only make sense as day/month and others only as month/day. */
    MIXED_DATES,
}

sealed interface StatementRead {
    data class Rows(
        val rows: List<StatementRow>,
        /** Lines under the header that could not be read as an entry (no date, no amount, or two amounts). Reported, never dropped in silence. */
        val unreadLines: Int,
        /** Lines that are moves between the owner's own wallets in the app's own export; they are not brought in. */
        val movesLeftOut: Int,
        /**
         * False when the file has one unsigned amount column and nothing that says which way the money
         * went. The rows are then read as money out, and the review screen offers to flip them.
         */
        val directionKnown: Boolean,
        /** For dates like 03/04/2026: true = day first, false = month first, null = the file never settles it (read as month first). */
        val dayFirst: Boolean?,
        /** True when the file has dates like 03/04/2026 and nothing in it settles which number is the month: the screen then says how they were read. */
        val datesAssumedMonthFirst: Boolean = false,
        /** The statement's own totals, when it gives them: what the lines must add up to before anything is imported. */
        val totals: StatementTotals? = null,
        /** Which kind of file this was; it is part of what makes a line recognisable on a later import. */
        val source: ImportSource = ImportSource.CSV,
    ) : StatementRead

    data class Unreadable(val reason: UnreadableReason) : StatementRead
}

/** Splits CSV text into rows of cells. */
object Csv {
    /** The separator this file uses: whichever of comma, semicolon and tab is most common in its first line. */
    fun detectDelimiter(text: String): Char {
        val firstLine = text.removePrefix("﻿").lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        return listOf(',', ';', '\t').maxBy { candidate -> firstLine.count { it == candidate } }
    }

    /**
     * Quoted cells may hold the separator, line breaks and doubled quotes. Lines may end in CR LF, LF or
     * CR. A byte-order mark at the start is dropped. Lines with nothing in them are left out.
     */
    fun parse(text: String, delimiter: Char = detectDelimiter(text)): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = if (text.startsWith("﻿")) 1 else 0

        fun endRow() {
            row.add(cell.toString())
            cell.setLength(0)
            if (row.any { it.isNotBlank() }) rows.add(row)
            row = ArrayList()
        }

        while (i < text.length) {
            val ch = text[i]
            when {
                quoted && ch == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                    cell.append('"')
                    i++
                }
                ch == '"' -> quoted = !quoted
                quoted -> cell.append(ch)
                ch == delimiter -> {
                    row.add(cell.toString())
                    cell.setLength(0)
                }
                ch == '\r' -> {
                    if (i + 1 < text.length && text[i + 1] == '\n') i++
                    endRow()
                }
                ch == '\n' -> endRow()
                else -> cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }
}

/** Amounts the way statements write them. */
object StatementAmounts {
    private val CURRENCY = Regex("""(?i)php|₱|\bp\b""")

    /**
     * Signed centavos, or null for a blank cell or text that is not an amount.
     * "1,234.56", "₱ 250", "PHP 20.00" are money in; "-250.00" and "(250.00)" are money out.
     */
    fun parse(text: String): Long? {
        var cleaned = text.trim().replace(CURRENCY, "").replace(",", "").replace(" ", "")
        if (cleaned.isEmpty()) return null
        var negative = false
        if (cleaned.startsWith("(") && cleaned.endsWith(")")) {
            negative = true
            cleaned = cleaned.substring(1, cleaned.length - 1)
        }
        if (cleaned.startsWith("-") || cleaned.startsWith("−")) {
            negative = !negative
            cleaned = cleaned.substring(1)
        } else if (cleaned.startsWith("+")) {
            cleaned = cleaned.substring(1)
        }
        val centavos = Peso.parse(cleaned) ?: return null
        return if (negative) -centavos else centavos
    }
}

/** Dates the way statements write them. A time after the date is ignored. */
object StatementDates {
    private val YEAR_FIRST = Regex("""^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})(?!\d)""")
    private val NUMERIC = Regex("""^(\d{1,2})[-/.](\d{1,2})[-/.](\d{4}|\d{2})(?!\d)""")
    private val MONTH_NAME_FIRST = Regex("""^([A-Za-z]{3,9})\.?\s+(\d{1,2}),?\s+(\d{4})""")
    private val DAY_NAME_MONTH = Regex("""^(\d{1,2})[\s-]+([A-Za-z]{3,9})\.?,?[\s-]+(\d{4})""")

    private val MONTHS: Map<String, Int> = Month.entries.flatMap { month ->
        listOf(
            month.getDisplayName(TextStyle.FULL, Locale.ENGLISH).lowercase() to month.value,
            month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).lowercase() to month.value,
        )
    }.toMap() + ("sept" to 9)

    /** The two leading numbers of a date like 03/04/2026, or null when the text is not written that way. */
    fun ambiguousParts(text: String): Pair<Int, Int>? =
        NUMERIC.find(text.trim())?.let { it.groupValues[1].toInt() to it.groupValues[2].toInt() }

    fun parse(text: String, dayFirst: Boolean): LocalDate? {
        val trimmed = text.trim()
        YEAR_FIRST.find(trimmed)?.let { m -> return date(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }
        NUMERIC.find(trimmed)?.let { m ->
            val first = m.groupValues[1].toInt()
            val second = m.groupValues[2].toInt()
            val year = m.groupValues[3].toInt().let { if (it < 100) 2000 + it else it }
            return if (dayFirst) date(year, second, first) else date(year, first, second)
        }
        MONTH_NAME_FIRST.find(trimmed)?.let { m ->
            val month = MONTHS[m.groupValues[1].lowercase()] ?: return null
            return date(m.groupValues[3].toInt(), month, m.groupValues[2].toInt())
        }
        DAY_NAME_MONTH.find(trimmed)?.let { m ->
            val month = MONTHS[m.groupValues[2].lowercase()] ?: return null
            return date(m.groupValues[3].toInt(), month, m.groupValues[1].toInt())
        }
        return null
    }

    private fun date(year: Int, month: Int, day: Int): LocalDate? = try {
        LocalDate.of(year, month, day)
    } catch (e: DateTimeException) {
        null
    }
}

/**
 * Reads a statement saved as CSV. It does not know any bank's layout: it finds the header row by the
 * names of its columns (Date, Description, Reference, Amount or Debit and Credit, in the usual
 * wordings), so it reads most banks' CSV files and the app's own spreadsheet export.
 */
object CsvStatementReader {
    private val DATE = setOf(
        "date", "date and time", "date & time", "date/time", "datetime", "transaction date", "txn date", "trans date",
        "posting date", "posted date", "date posted", "value date",
    )
    private val DESCRIPTION = setOf(
        "description", "details", "transaction details", "transaction description", "particulars", "remarks", "narration",
        "note", "notes", "memo", "merchant",
    )
    private val REFERENCE = setOf(
        "reference", "reference no", "reference number", "reference #", "ref", "ref no", "ref number", "ref #",
        "transaction id", "transaction no", "transaction number", "trace no", "trace number",
    )
    private val AMOUNT = setOf("amount", "transaction amount", "amount (php)", "amount php")
    private val DEBIT = setOf("debit", "debits", "debit amount", "withdrawal", "withdrawals", "money out", "paid out", "out")
    private val CREDIT = setOf("credit", "credits", "credit amount", "deposit", "deposits", "money in", "paid in", "in")
    private val DIRECTION = setOf("in or out", "direction", "debit/credit", "dr/cr")
    private val CATEGORY = setOf("category")
    private val FROM_WALLET = setOf("from wallet")
    private val TO_WALLET = setOf("to wallet")

    private val MEANS_IN = setOf("in", "credit", "cr", "deposit", "money in")
    private val MEANS_OUT = setOf("out", "debit", "dr", "withdrawal", "money out")
    private val MEANS_MOVE = setOf("move", "transfer")

    /** How far down a file its header may sit (banks put a title and an account line above it). */
    private const val HEADER_SEARCH_ROWS = 15

    private fun name(cell: String) = cell.trim().lowercase().removeSuffix(".").replace(Regex("""\s+"""), " ")

    private class Columns(
        val date: Int, val description: Int?, val category: Int?, val reference: Int?,
        val amount: Int?, val debit: Int?, val credit: Int?, val direction: Int?,
        val fromWallet: Int?, val toWallet: Int?,
    )

    private fun columns(row: List<String>): Columns? {
        val names = row.map(::name)
        fun find(wanted: Set<String>) = names.indexOfFirst { it in wanted }.takeIf { it >= 0 }
        val date = find(DATE) ?: return null
        val amount = find(AMOUNT)
        val debit = find(DEBIT)
        val credit = find(CREDIT)
        if (amount == null && debit == null && credit == null) return null
        return Columns(date, find(DESCRIPTION), find(CATEGORY), find(REFERENCE), amount, debit, credit, find(DIRECTION), find(FROM_WALLET), find(TO_WALLET))
    }

    fun read(text: String): StatementRead {
        val table = Csv.parse(text)
        if (table.isEmpty()) return StatementRead.Unreadable(UnreadableReason.EMPTY)
        val headerAt = table.take(HEADER_SEARCH_ROWS).indexOfFirst { columns(it) != null }
        if (headerAt < 0) return StatementRead.Unreadable(UnreadableReason.NO_HEADER)
        val cols = requireNotNull(columns(table[headerAt]))
        val lines = table.drop(headerAt + 1)

        // Settle day-first or month-first once for the whole file, from the dates that can only be one of them.
        val parts = lines.mapNotNull { StatementDates.ambiguousParts(it.getOrNull(cols.date).orEmpty()) }
        val mustBeDayFirst = parts.any { it.first > 12 }
        val mustBeMonthFirst = parts.any { it.second > 12 }
        if (mustBeDayFirst && mustBeMonthFirst) return StatementRead.Unreadable(UnreadableReason.MIXED_DATES)
        val dayFirst: Boolean? = when {
            mustBeDayFirst -> true
            mustBeMonthFirst -> false
            else -> null
        }

        val twoColumns = cols.debit != null || cols.credit != null
        // One amount column with a minus (or brackets) on some lines is a signed column: plain numbers are money in.
        val signedColumn = !twoColumns && cols.direction == null &&
            lines.any { (StatementAmounts.parse(it.getOrNull(cols.amount ?: -1).orEmpty()) ?: 0) < 0 }
        val directionKnown = twoColumns || cols.direction != null || signedColumn
        var unread = 0
        var moves = 0
        val rows = ArrayList<StatementRow>()
        for (line in lines) {
            fun cell(index: Int?) = index?.let { line.getOrNull(it) }.orEmpty().trim()
            val date = StatementDates.parse(cell(cols.date), dayFirst ?: false)
            if (date == null) {
                unread++
                continue
            }
            val signed: Long? = if (twoColumns) {
                val out = StatementAmounts.parse(cell(cols.debit))?.let(Math::abs)?.takeIf { it > 0 }
                val into = StatementAmounts.parse(cell(cols.credit))?.let(Math::abs)?.takeIf { it > 0 }
                when {
                    out != null && into == null -> -out
                    into != null && out == null -> into
                    else -> null
                }
            } else {
                val amount = StatementAmounts.parse(cell(cols.amount))?.takeIf { it != 0L }
                val word = name(cell(cols.direction))
                when {
                    amount == null -> null
                    // With nothing in the file to say which way, a number is read as money out; the review
                    // screen says so and offers to flip it.
                    cols.direction == null -> if (signedColumn) amount else -Math.abs(amount)
                    word in MEANS_MOVE -> {
                        moves++
                        continue
                    }
                    word in MEANS_IN -> Math.abs(amount)
                    word in MEANS_OUT -> -Math.abs(amount)
                    else -> null
                }
            }
            if (signed == null) {
                unread++
                continue
            }
            rows += StatementRow(
                date = date,
                description = cell(cols.description).ifEmpty { cell(cols.category) },
                reference = cell(cols.reference).ifEmpty { null },
                amountCentavos = Math.abs(signed),
                moneyIn = signed > 0,
                // Money in sits in the wallet it went to, money out in the wallet it came from.
                walletName = cell(if (signed > 0) cols.toWallet else cols.fromWallet).ifEmpty { null },
            )
        }
        return StatementRead.Rows(
            rows, unreadLines = unread, movesLeftOut = moves, directionKnown = directionKnown, dayFirst = dayFirst,
            datesAssumedMonthFirst = dayFirst == null && parts.isNotEmpty(),
        )
    }
}

/**
 * Hides what a statement says while keeping how it is laid out: every letter becomes x (X for a
 * capital) and every digit 9. Column words that every statement uses (Date, Debit, Balance …) are kept,
 * since they are the layout and say nothing about the owner. The result can be shown to someone else
 * so they can teach the app to read that bank's files, without showing them a single name or amount.
 */
object TextMask {
    private val KEPT = setOf(
        "date", "time", "and", "description", "details", "particulars", "reference", "ref", "no", "number", "id",
        "debit", "credit", "balance", "amount", "total", "page", "of", "transaction", "transactions", "history",
        "statement", "account", "starting", "ending", "beginning", "opening", "closing", "php", "type", "status",
    )
    private val WORD = Regex("""[A-Za-z]+""")

    const val MAX_LINES = 80
    const val MAX_LINE_LENGTH = 160

    fun mask(text: String): String = WORD
        .replace(text) { m -> if (m.value.lowercase() in KEPT) m.value else m.value.map { if (it.isUpperCase()) 'X' else 'x' }.joinToString("") }
        .map { if (it.isDigit()) '9' else it }
        .joinToString("")

    /** The masked start of each page: enough to see the header and the first rows, never the whole file. */
    fun sample(pages: List<String>): String = pages.withIndex().joinToString("\n\n") { (index, page) ->
        val lines = page.lines().filter { it.isNotBlank() }
        val shown = lines.take(MAX_LINES).joinToString("\n") { mask(it.take(MAX_LINE_LENGTH)) }
        val more = if (lines.size > MAX_LINES) "\n… ${lines.size - MAX_LINES} more lines" else ""
        "— Page ${index + 1} (${if (lines.size == 1) "1 line" else "${lines.size} lines"}) —\n$shown$more"
    }
}
