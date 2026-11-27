package com.pitakaview.app.domain.importing

import com.pitakaview.app.data.db.entity.CategoryEntity
import com.pitakaview.app.data.db.entity.TransactionEntity
import com.pitakaview.app.domain.ImportSource
import com.pitakaview.app.domain.TxPurpose
import java.time.Instant
import java.time.LocalDate

/** Why a line of the file is, or is not, offered for import. */
enum class ImportStatus {
    /** Not in the app yet. Ticked to begin with. */
    NEW,

    /** A line with this bank reference was imported before. It cannot be brought in again. */
    ALREADY_IMPORTED,

    /** The same reference, amount and direction appears earlier in this same file. */
    TWICE_IN_FILE,

    /** The app already has an entry in this wallet on that day for that amount (typed in by hand, most likely). Unticked, but the owner may tick it. */
    LOOKS_LIKE_EXISTING,

    /**
     * The file names a wallet for each line (the app's own export does) and this line's is not the wallet
     * being imported into. Not brought in: until 2026-11-27 every wallet's lines went into that one wallet.
     */
    OTHER_WALLET,
}

/** One line of the file as the review screen shows it. */
data class ImportCandidate(
    val row: StatementRow,
    val status: ImportStatus,
    /** What is stored with the entry so the same line is recognised next time; null when the file gives no reference. */
    val externalRef: String?,
    val categoryId: Long,
    val picked: Boolean,
) {
    val canPick: Boolean get() = status == ImportStatus.NEW || status == ImportStatus.LOOKS_LIKE_EXISTING
}

/** An entry the wallet already has, reduced to what a statement line can be compared with. */
data class LedgerMark(val date: LocalDate, val amountCentavos: Long, val moneyIn: Boolean)

/** Turns the lines read from a file into what to add to the ledger. Pure, so every rule is unit-tested. */
object ImportPlanner {

    /**
     * The key that makes a line recognisable on a later import: where it came from, the wallet, the
     * bank's reference, and the direction and amount (a payment and its fee can share one reference).
     */
    fun refFor(source: ImportSource, walletId: Long, row: StatementRow): String? =
        row.reference?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { "${source.name}:$walletId:$it:${if (row.moneyIn) "in" else "out"}:${row.amountCentavos}" }

    /**
     * [importedRefs]: which of this file's keys the ledger already holds.
     * [existing]: every entry the wallet has in the file's span of dates.
     * Lines without a reference are compared by day, amount and direction, counting: two ₱50 lines on a
     * day against one existing ₱50 entry leaves one of them new.
     */
    fun plan(
        rows: List<StatementRow>,
        source: ImportSource,
        walletId: Long,
        importedRefs: Set<String>,
        existing: List<LedgerMark>,
        expenseCategories: List<CategoryEntity>,
        incomeCategories: List<CategoryEntity>,
        /** The name of the wallet being imported into, to compare with the wallet a line is named for. */
        walletName: String? = null,
    ): List<ImportCandidate> {
        val unmatched = existing.groupingBy { it }.eachCount().toMutableMap()
        val seenRefs = HashSet<String>()
        return rows.map { row ->
            val ref = refFor(source, walletId, row)
            val mark = LedgerMark(row.date, row.amountCentavos, row.moneyIn)
            val status = when {
                // First: a line for another wallet uses up no reference and no existing entry.
                row.walletName != null && walletName != null && !row.walletName.trim().equals(walletName.trim(), ignoreCase = true) ->
                    ImportStatus.OTHER_WALLET
                ref != null && ref in importedRefs -> ImportStatus.ALREADY_IMPORTED
                ref != null && !seenRefs.add(ref) -> ImportStatus.TWICE_IN_FILE
                (unmatched[mark] ?: 0) > 0 -> {
                    unmatched[mark] = unmatched.getValue(mark) - 1
                    ImportStatus.LOOKS_LIKE_EXISTING
                }
                else -> ImportStatus.NEW
            }
            ImportCandidate(
                row = row,
                status = status,
                externalRef = ref,
                categoryId = guessCategory(row.description, if (row.moneyIn) incomeCategories else expenseCategories),
                picked = status == ImportStatus.NEW,
            )
        }
    }

    /** Words that usually mean a category, most specific first ("grabfood" before "grab"). Matched against the starter category names. */
    private val HINTS: List<Pair<String, List<String>>> = listOf(
        "Food" to listOf("grabfood", "foodpanda", "jollibee", "mcdo", "mcdonald", "kfc", "chowking", "mang inasal", "greenwich", "starbucks", "coffee", "cafe", "restaurant", "resto", "food", "eatery", "bakery"),
        "Groceries" to listOf("supermarket", "savemore", "puregold", "grocery", "7-eleven", "7 eleven", "alfamart", "landers", "s&r", "palengke", "market"),
        "Health" to listOf("mercury drug", "watsons", "pharmacy", "drugstore", "hospital", "clinic", "dental", "medical"),
        "Load & Internet" to listOf("load", "globe", "smart", "dito", "gomo", "wifi", "internet", "pldt", "converge", "prepaid", "data promo"),
        "Bills" to listOf("meralco", "maynilad", "manila water", "electric", "bills payment", "bill payment", "bills", "sss", "philhealth", "pag-ibig", "pagibig", "insurance"),
        "Rent & Home" to listOf("rent", "landlord", "condo dues", "association dues", "hardware", "furniture"),
        "Transport" to listOf("grab", "angkas", "joyride", "move it", "beep", "lrt", "mrt", "toll", "autosweep", "easytrip", "petron", "shell", "caltex", "gasoline", "fare", "taxi", "parking"),
        "Fun" to listOf("netflix", "spotify", "youtube", "cinema", "steam", "games", "disney"),
        "Salary" to listOf("salary", "payroll", "sahod", "sweldo"),
        "Gift" to listOf("gift", "regalo", "pamasko"),
    )

    /**
     * A first guess the owner can change on the review screen. Falls back to the category called Other,
     * then to the first one: the choice is always one of [categories] (which must not be empty).
     */
    fun guessCategory(description: String, categories: List<CategoryEntity>): Long {
        val text = description.lowercase()
        val byName = categories.associateBy { it.name.lowercase() }
        for ((category, pattern) in HINT_PATTERNS) {
            val match = byName[category.lowercase()] ?: continue
            if (pattern.containsMatchIn(text)) return match.id
        }
        return (byName["other"] ?: categories.first()).id
    }

    /**
     * Each hint as a whole word (with an optional plural or 's), so "rent" does not fire on "current"
     * nor "load" on "download". Built once.
     */
    private val HINT_PATTERNS: List<Pair<String, Regex>> by lazy {
        HINTS.map { (category, words) ->
            category to Regex(words.joinToString("|", prefix = "(?<![a-z0-9])(?:", postfix = ")(?:'s|s)?(?![a-z0-9])") { Regex.escape(it) })
        }
    }

    /** The ledger rows for the ticked lines: money in is income into the wallet, money out is spending from it. */
    fun toTransactions(candidates: List<ImportCandidate>, walletId: Long, batchId: Long, now: Instant): List<TransactionEntity> =
        candidates.filter { it.picked && it.canPick }.map { candidate ->
            val row = candidate.row
            TransactionEntity(
                purpose = if (row.moneyIn) TxPurpose.INCOME else TxPurpose.EXPENSE,
                amountCentavos = row.amountCentavos,
                occurredOn = row.date,
                fromWalletId = if (row.moneyIn) null else walletId,
                toWalletId = if (row.moneyIn) walletId else null,
                categoryId = candidate.categoryId,
                note = row.description.trim().ifEmpty { null },
                importBatchId = batchId,
                externalRef = candidate.externalRef,
                createdAt = now,
            )
        }

    /** Every amount the other way round, for a file that does not say which way its money went. */
    fun flipped(rows: List<StatementRow>): List<StatementRow> = rows.map { it.copy(moneyIn = !it.moneyIn) }

    /**
     * A line the file does not settle ([open]: its direction is a guess, or the file does not say at all
     * which way its money went) may have been brought in before the other way round, after the owner said
     * so. The ledger then knows which way it went:
     *  - by the line's reference, when it has one ([importedRefs] holds its key one way and not the other);
     *  - without a reference, by an entry of that day and amount going the other way while none goes this
     *    way ([existing]; each entry is used for one line).
     * Such a line takes the ledger's direction, so it is recognised. Until 2026-11-27 it was offered
     * again, the first way round, and importing it stored the same money a second time, the wrong way.
     * A reference settles the line; a look-alike entry only turns the guess, and the line stays open.
     */
    fun settledByLedger(
        rows: List<StatementRow>,
        open: (StatementRow) -> Boolean,
        source: ImportSource,
        walletId: Long,
        importedRefs: Set<String>,
        existing: List<LedgerMark>,
    ): List<StatementRow> {
        val left = existing.groupingBy { it }.eachCount().toMutableMap()
        fun take(mark: LedgerMark): Boolean {
            val count = left[mark] ?: 0
            if (count > 0) left[mark] = count - 1
            return count > 0
        }
        return rows.map { row ->
            if (!open(row)) return@map row
            val turned = row.copy(moneyIn = !row.moneyIn)
            val thisWay = refFor(source, walletId, row)
            if (thisWay != null) {
                val here = thisWay in importedRefs
                val there = refFor(source, walletId, turned) in importedRefs
                when {
                    here == there -> row
                    here -> row.copy(sure = true)
                    else -> turned.copy(sure = true)
                }
            } else {
                when {
                    take(LedgerMark(row.date, row.amountCentavos, row.moneyIn)) -> row
                    take(LedgerMark(row.date, row.amountCentavos, !row.moneyIn)) -> turned
                    else -> row
                }
            }
        }
    }
}
