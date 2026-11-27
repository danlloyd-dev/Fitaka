package com.pitakaview.app.domain.importing

import com.pitakaview.app.domain.ImportSource
import java.time.DateTimeException
import java.time.LocalDate
import java.time.YearMonth

/** What a statement says about itself: all the money that came in and all that went out over its period. */
data class StatementTotals(val moneyInCentavos: Long, val moneyOutCentavos: Long)

/** Which way one line went, and whether that is settled or only the best guess so far. */
data class Direction(val moneyIn: Boolean, val sure: Boolean)

/**
 * Works out which lines of a statement are money in and which are money out from the statement's own
 * totals, for statements whose text does not say it line by line.
 *
 * A line whose direction is already known is given as a fact. For the others every way of splitting them
 * that reaches the totals exactly is found. A line that goes the same way in all of them is settled; one
 * that could go either way (two lines of the same amount, for instance) is left for the owner.
 */
object DirectionSolver {
    /** More open lines than this are not tried (the number of ways doubles with each one); they are left for the owner. */
    const val MAX_OPEN = 22

    /** Past this many ways to reach the totals, nothing is called settled. */
    const val MAX_WAYS = 64

    /** Null when no split of the lines reaches the totals: the lines or the facts are wrong. */
    fun solve(amounts: List<Long>, facts: List<Boolean?>, totals: StatementTotals): List<Direction>? {
        require(amounts.size == facts.size) { "One fact (or null) per amount" }
        if (amounts.sum() != totals.moneyInCentavos + totals.moneyOutCentavos) return null
        val open = amounts.indices.filter { facts[it] == null }.sortedByDescending { amounts[it] }
        val target = totals.moneyInCentavos - amounts.indices.filter { facts[it] == true }.sumOf { amounts[it] }
        if (target < 0) return null
        if (open.size > MAX_OPEN) return amounts.indices.map { Direction(facts[it] ?: false, sure = facts[it] != null) }

        // What is still to come from position i on; a branch that cannot reach the target any more is dropped.
        val rest = LongArray(open.size + 1)
        for (i in open.indices.reversed()) rest[i] = rest[i + 1] + amounts[open[i]]
        val ways = ArrayList<BooleanArray>()
        val picked = BooleanArray(open.size)
        fun search(i: Int, left: Long) {
            if (ways.size >= MAX_WAYS) return
            if (left == 0L) {
                // Reached: every line after this point is money out (amounts are never zero).
                ways += BooleanArray(open.size) { it < i && picked[it] }
                return
            }
            if (i == open.size || left < 0 || rest[i] < left) return
            picked[i] = true
            search(i + 1, left - amounts[open[i]])
            picked[i] = false
            search(i + 1, left)
        }
        search(0, target)
        if (ways.isEmpty()) return null

        val settled = ways.size < MAX_WAYS
        val position = open.withIndex().associate { (at, index) -> index to at }
        return amounts.indices.map { index ->
            val fact = facts[index]
            if (fact != null) {
                Direction(fact, sure = true)
            } else {
                val at = position.getValue(index)
                Direction(ways[0][at], sure = settled && ways.all { it[at] == ways[0][at] })
            }
        }
    }
}

/**
 * GCash's "Transaction History" PDF, as Android's PDF reader hands it over (layout learned on
 * 2026-11-26 from the owner's own file, with every letter and digit hidden):
 *
 *     Date and Time Description Reference No. Debit Credit Balance
 *     STARTING BALANCE 500.00
 *     2026-11-03 10:15 AM Payment to SOMEONE, Merchant Transaction Number: A1 1000000000001 250.00 250.00
 *     2026-11-05 08:00 PM
 *     Received GCash from a bank with account ending in 1234 and
 *     invno:20261105ABC
 *     1000000000003 1500.00 1750.00
 *     ENDING BALANCE 1750.00
 *     Total Debit 250.00
 *     Total Credit 1500.00
 *
 * Each entry ends with its reference number, its amount and the balance after it; a long description
 * wraps onto lines of its own. The text does not say whether an amount was a debit or a credit, so that
 * is taken from the balance: it went up by the amount, or down by it.
 */
object GcashStatementReader {
    private val ROW_START = Regex("""^(\d{4}-\d{2}-\d{2}) \d{1,2}:\d{2} ?[AP]M\b ?(.*)$""", RegexOption.IGNORE_CASE)
    private val TAIL = Regex("""(\d{8,20})\s+(-?[\d,]+\.\d{2})\s+(-?[\d,]+\.\d{2})""")
    private val DATE_RANGE = Regex("""^\d{4}-\d{2}-\d{2} to \d{4}-\d{2}-\d{2}$""", RegexOption.IGNORE_CASE)
    private val LABELLED = Regex("""^(STARTING BALANCE|ENDING BALANCE|Total Debit|Total Credit)\s+(-?[\d,]+\.\d{2})$""", RegexOption.IGNORE_CASE)

    private fun isHeader(line: String) =
        line.contains("Date and Time", ignoreCase = true) && line.contains("Reference No", ignoreCase = true) &&
            line.contains("Debit", ignoreCase = true) && line.contains("Credit", ignoreCase = true) && line.contains("Balance", ignoreCase = true)

    fun matches(pages: List<String>): Boolean = pages.any { page -> page.lineSequence().any { isHeader(it) } }

    private class Entry(val date: LocalDate, val description: String, val reference: String, val amount: Long, val balance: Long)

    fun read(pages: List<String>): StatementRead {
        val lines = pages.flatMap { it.lines() }.map { it.trim() }.filter { it.isNotEmpty() }
        val labelled = HashMap<String, Long>()
        val blocks = ArrayList<Pair<String, StringBuilder>>()
        var current: StringBuilder? = null
        for (line in lines) {
            val start = ROW_START.find(line)
            val label = LABELLED.find(line)
            when {
                start != null -> {
                    current = StringBuilder(start.groupValues[2])
                    blocks += start.groupValues[1] to current
                }
                label != null -> {
                    StatementAmounts.parse(label.groupValues[2])?.let { labelled[label.groupValues[1].uppercase()] = it }
                    current = null
                }
                // Lines that belong to the page, not to an entry: they end whatever entry was being read.
                isHeader(line) || DATE_RANGE.matches(line) || line.endsWith("Transaction History", ignoreCase = true) -> current = null
                else -> current?.append(' ')?.append(line)
            }
        }

        var unread = 0
        val entries = blocks.mapNotNull { (day, text) ->
            // The last "reference amount balance" in the entry's text; a description may hold long numbers of its own.
            val tail = TAIL.findAll(text).lastOrNull()
            val date = StatementDates.parse(day, dayFirst = false)
            val amount = tail?.let { StatementAmounts.parse(it.groupValues[2]) }?.let(Math::abs)?.takeIf { it > 0 }
            val balance = tail?.let { StatementAmounts.parse(it.groupValues[3]) }
            if (tail == null || date == null || amount == null || balance == null) {
                unread++
                null
            } else {
                Entry(date, text.substring(0, tail.range.first).trim(), tail.groupValues[1], amount, balance)
            }
        }
        if (entries.isEmpty() && unread == 0) return StatementRead.Unreadable(UnreadableReason.NO_HEADER)

        val startBalance = labelled["STARTING BALANCE"]
        fun fromBalances(oldestFirst: Boolean): List<Boolean?> = entries.indices.map { i ->
            val before = if (oldestFirst) entries.getOrNull(i - 1)?.balance ?: startBalance.takeIf { i == 0 }
            else entries.getOrNull(i + 1)?.balance ?: startBalance.takeIf { i == entries.lastIndex }
            val entry = entries[i]
            when {
                before == null -> null
                before + entry.amount == entry.balance -> true
                before - entry.amount == entry.balance -> false
                else -> null
            }
        }
        // The file lists the oldest entry first; one that lists the newest first is read the other way round.
        val facts = listOf(fromBalances(oldestFirst = true), fromBalances(oldestFirst = false)).maxBy { way -> way.count { it != null } }

        val debit = labelled["TOTAL DEBIT"]
        val credit = labelled["TOTAL CREDIT"]
        val totals = if (debit != null && credit != null) StatementTotals(moneyInCentavos = credit, moneyOutCentavos = debit) else null
        // Lines the balances could not settle are worked out from the totals; if even that fails they are left for the owner.
        val directions = totals?.let { DirectionSolver.solve(entries.map { it.amount }, facts, it) }
            ?: facts.map { Direction(it ?: false, sure = it != null) }

        return StatementRead.Rows(
            rows = entries.mapIndexed { i, entry ->
                StatementRow(entry.date, entry.description, entry.reference, entry.amount, directions[i].moneyIn, directions[i].sure)
            },
            unreadLines = unread, movesLeftOut = 0, directionKnown = true, dayFirst = null,
            totals = totals, source = ImportSource.GCASH,
        )
    }
}

/**
 * MariBank's monthly statement PDF, as Android's PDF reader hands it over (layout learned on 2026-11-26
 * from the owner's own file, with every letter and digit hidden). Every page starts with four lines
 * (title, statement number, date, "page 1 of 4"). Page 1 has a summary:
 *
 *     01 OCT 2026 to 31 OCT 2026
 *     ACCOUNT STARTING BALANCE / (PHP) / TOTAL … / (PHP) / TOTAL … / (PHP) / ENDING BALANCE / (PHP)
 *     SAVINGS 10.00 12,000.00 11,500.00 510.00
 *
 * then "SAVINGS - TRANSACTION DETAILS", running over the pages, each entry on three lines:
 *
 *     05 OCT Some Transfer Title
 *     Transfer
 *     1,000.00
 *
 * (the month's interest has only the month where the day would be), and after it a table of daily
 * interest that is not a list of entries. The two amount columns (out and in) come through as one bare
 * amount, so which way a line went is not in the text. It is worked out from the summary's totals by
 * [DirectionSolver], with the words of the line as a first hint; what the totals leave open is left
 * for the owner, and the review screen will not import until the lines add up to the statement's totals.
 */
object MariBankStatementReader {
    private val MONTHS = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
    private val PERIOD = Regex("""(\d{1,2}) ([A-Za-z]{3}) (\d{4}) to (\d{1,2}) ([A-Za-z]{3}) (\d{4})""", RegexOption.IGNORE_CASE)
    private val PAGE_OF = Regex("""^page \d+ of \d+$""", RegexOption.IGNORE_CASE)
    private val AMOUNT_ONLY = Regex("""^-?[\d,]+\.\d{2}$""")
    private val FOUR_AMOUNTS = Regex("""^(\D.*?)\s+(-?[\d,]+\.\d{2})\s+(-?[\d,]+\.\d{2})\s+(-?[\d,]+\.\d{2})\s+(-?[\d,]+\.\d{2})$""")
    private val DAY_ROW = Regex("""^(\d{1,2}) ([A-Za-z]{3}) (.+)$""")
    private val MONTH_ROW = Regex("""^([A-Za-z]{3}) (.+)$""")
    private val ENDS_WITH_AMOUNT = Regex("""^(.*\S)\s+(-?[\d,]+\.\d{2})$""")
    private val SERIAL = Regex("""^[A-Za-z]/[A-Za-z] (\S+)$""")
    private const val DETAILS = "- TRANSACTION DETAILS"

    /** Words that usually say which way a line went. Only a first hint: the statement's totals decide. */
    private val IN_WORDS = listOf("interest", "incoming", "received", "receive", "cash in", "cash-in", "deposit", "refund", "reversal", "cashback")
    private val OUT_WORDS = listOf("outgoing", "payment", "withdrawal", "withdraw", "sent", "send", "purchase", "fee", "charge", "tax", "bills")

    fun matches(pages: List<String>): Boolean {
        val lines = pages.flatMap { it.lines() }.map { it.trim() }
        return lines.any { it.endsWith(DETAILS, ignoreCase = true) } && lines.any { it.startsWith("DATE TRANSACTION", ignoreCase = true) }
    }

    private fun month(text: String): Int? = MONTHS.indexOf(text.uppercase()).takeIf { it >= 0 }?.plus(1)

    private fun date(year: Int, month: Int, day: Int): LocalDate? = try {
        LocalDate.of(year, month, day)
    } catch (e: DateTimeException) {
        null
    }

    /**
     * Each word as a whole word (with an optional plural), never as letters inside another word. Until
     * 2026-11-27 they were looked for anywhere: "send" was found in "Rosendo", "fee" in "coffee" and "tax"
     * in "taxi", and a name decided which way a line went.
     */
    private fun wholeWords(words: List<String>) =
        Regex(words.joinToString("|", prefix = "(?<![a-z0-9])(?:", postfix = ")s?(?![a-z0-9])") { Regex.escape(it) })
    private val IN_PATTERN = wholeWords(IN_WORDS)
    private val OUT_PATTERN = wholeWords(OUT_WORDS)

    private fun hint(words: String): Boolean? {
        val text = words.lowercase()
        val into = IN_PATTERN.containsMatchIn(text)
        val out = OUT_PATTERN.containsMatchIn(text)
        return if (into == out) null else into
    }

    /**
     * A line's words may suggest which way it went; they may not settle it against a line of the same
     * amount that the words say nothing about. With the words taken as facts, the totals push that other
     * line the opposite way and both came out marked sure, though the two could change places and reach
     * the same totals: had the word been wrong, both were stored the wrong way round and the review said
     * the lines add up. Such lines keep their direction as a first guess and are left for the owner.
     */
    private fun tiesLeftOpen(amounts: List<Long>, hints: List<Boolean?>, solved: List<Direction>): List<Direction> {
        fun amountsOf(fromWords: Boolean, moneyIn: Boolean) =
            amounts.indices.filter { (hints[it] != null) == fromWords && solved[it].moneyIn == moneyIn }.mapTo(HashSet()) { amounts[it] }
        val wordsIn = amountsOf(fromWords = true, moneyIn = true)
        val wordsOut = amountsOf(fromWords = true, moneyIn = false)
        val silentIn = amountsOf(fromWords = false, moneyIn = true)
        val silentOut = amountsOf(fromWords = false, moneyIn = false)
        return solved.mapIndexed { i, direction ->
            val acrossFromIt = when {
                hints[i] != null -> if (direction.moneyIn) silentOut else silentIn
                else -> if (direction.moneyIn) wordsOut else wordsIn
            }
            if (amounts[i] in acrossFromIt) direction.copy(sure = false) else direction
        }
    }

    private class Entry(val date: LocalDate, val description: String, val amount: Long)

    fun read(pages: List<String>): StatementRead {
        // Each page without its four heading lines (which end with "page 1 of 4" and include a bare date).
        val body = pages.flatMap { page ->
            val lines = page.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val headEnds = lines.take(6).indexOfFirst { PAGE_OF.matches(it) }
            lines.drop(headEnds + 1)
        }
        val serial = pages.firstNotNullOfOrNull { page -> page.lineSequence().take(6).firstNotNullOfOrNull { SERIAL.find(it.trim())?.groupValues?.get(1) } }
        val period = body.firstNotNullOfOrNull { PERIOD.find(it) } ?: return StatementRead.Unreadable(UnreadableReason.NO_HEADER)
        val from = month(period.groupValues[2])?.let { date(period.groupValues[3].toInt(), it, period.groupValues[1].toInt()) }
        val to = month(period.groupValues[5])?.let { date(period.groupValues[6].toInt(), it, period.groupValues[4].toInt()) }
        if (from == null || to == null || to < from) return StatementRead.Unreadable(UnreadableReason.NO_HEADER)

        val detailsAt = body.indexOfFirst { it.endsWith(DETAILS, ignoreCase = true) }
        if (detailsAt < 0) return StatementRead.Unreadable(UnreadableReason.NO_HEADER)
        // The entries stop where the next table begins ("… - INTEREST & TAX DETAILS*").
        val afterDetails = body.drop(detailsAt + 1)
        val nextTable = afterDetails.indexOfFirst { it.contains(" - ") && it.trimEnd('*').endsWith("DETAILS", ignoreCase = true) }
        val section = (if (nextTable >= 0) afterDetails.take(nextTable) else afterDetails).filterNot { it.startsWith("DATE TRANSACTION", ignoreCase = true) }

        /** The day and month of an entry, in the year of the statement's period that it falls into. */
        fun inPeriod(month: Int, day: Int?): LocalDate? {
            for (year in setOf(from.year, to.year)) {
                val candidate = if (day != null) date(year, month, day) else YearMonth.of(year, month).atEndOfMonth().let { if (it > to) to else it }
                if (candidate != null && candidate >= from.withDayOfMonth(1) && candidate <= to) return candidate
            }
            return if (day != null) date(to.year, month, day) else null
        }

        var unread = 0
        val entries = ArrayList<Entry>()
        var pendingDate: LocalDate? = null
        val pending = ArrayList<String>()
        fun finish(amountText: String) {
            val amount = StatementAmounts.parse(amountText)?.let(Math::abs)?.takeIf { it > 0 }
            val date = pendingDate
            if (date != null && amount != null) entries += Entry(date, pending.joinToString(" · "), amount) else unread++
            pendingDate = null
            pending.clear()
        }
        for (line in section) {
            val day = DAY_ROW.find(line)?.takeIf { month(it.groupValues[2]) != null && !it.groupValues[3].matches(Regex("""\d{4}""")) }
            // A line that starts with a month's name begins an entry only between entries: inside one it is just
            // part of the description ("May Santos", say).
            val monthOnly = if (day == null && pendingDate == null) MONTH_ROW.find(line)?.takeIf { month(it.groupValues[1]) != null } else null
            when {
                day != null || monthOnly != null -> {
                    // An entry that never got its amount is counted, not dropped in silence.
                    if (pendingDate != null) unread++
                    pending.clear()
                    val rest = day?.groupValues?.get(3) ?: monthOnly!!.groupValues[2]
                    pendingDate = if (day != null) inPeriod(month(day.groupValues[2])!!, day.groupValues[1].toInt()) else inPeriod(month(monthOnly!!.groupValues[1])!!, null)
                    if (pendingDate == null) {
                        unread++
                        continue
                    }
                    val oneLine = ENDS_WITH_AMOUNT.find(rest)
                    if (oneLine != null) {
                        pending += oneLine.groupValues[1]
                        finish(oneLine.groupValues[2])
                    } else {
                        pending += rest
                    }
                }
                pendingDate == null -> Unit // between entries: the page's foot, or anything else that is not an entry
                AMOUNT_ONLY.matches(line) -> finish(line)
                // A real entry is a few short lines; anything longer was not one.
                pending.size >= 4 -> {
                    unread++
                    pendingDate = null
                    pending.clear()
                }
                else -> pending += line
            }
        }
        if (pendingDate != null) unread++
        if (entries.isEmpty() && unread == 0) return StatementRead.Unreadable(UnreadableReason.NO_HEADER)

        val totals = totals(body.take(detailsAt))
        val amounts = entries.map { it.amount }
        val hints = entries.map { hint(it.description) }
        // The words are tried as facts first; if the totals cannot be reached that way, the totals alone decide.
        val fromWords = totals?.let { DirectionSolver.solve(amounts, hints, it) }
        val directions = when {
            fromWords != null -> tiesLeftOpen(amounts, hints, fromWords)
            else -> totals?.let { DirectionSolver.solve(amounts, hints.map { null }, it) } ?: hints.map { Direction(it ?: false, sure = false) }
        }

        return StatementRead.Rows(
            rows = entries.mapIndexed { i, entry ->
                // The file has no number per entry, so the statement's own number and the entry's place in it stand in for one.
                StatementRow(entry.date, entry.description, serial?.let { "$it#${i + 1}" }, entry.amount, directions[i].moneyIn, directions[i].sure)
            },
            unreadLines = unread, movesLeftOut = 0, directionKnown = true, dayFirst = null,
            totals = totals, source = ImportSource.MARIBANK,
        )
    }

    /**
     * The summary line: starting balance, two totals, ending balance. Which total is money in is told by
     * the arithmetic (start + in − out = end), not by the column names.
     */
    private fun totals(summary: List<String>): StatementTotals? {
        val line = summary.firstNotNullOfOrNull { FOUR_AMOUNTS.find(it) } ?: return null
        val (start, first, second, end) = (2..5).map { StatementAmounts.parse(line.groupValues[it]) ?: return null }
        return when {
            start + first - second == end -> StatementTotals(moneyInCentavos = first, moneyOutCentavos = second)
            start + second - first == end -> StatementTotals(moneyInCentavos = second, moneyOutCentavos = first)
            else -> null
        }
    }
}

/** Reads a bank statement from the text of its PDF pages, for the banks whose layout is known. */
object PdfStatementReader {
    /** [StatementRead.Unreadable] with NO_HEADER when the pages are not a statement this app knows how to read. */
    fun read(pages: List<String>): StatementRead = when {
        GcashStatementReader.matches(pages) -> GcashStatementReader.read(pages)
        MariBankStatementReader.matches(pages) -> MariBankStatementReader.read(pages)
        else -> StatementRead.Unreadable(UnreadableReason.NO_HEADER)
    }
}
