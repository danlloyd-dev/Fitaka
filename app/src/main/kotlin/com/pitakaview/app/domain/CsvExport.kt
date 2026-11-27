package com.pitakaview.app.domain

import java.time.LocalDate

/** One entry as the spreadsheet export lists it, with the names its ids stand for. */
data class ExportRow(
    val id: Long,
    val occurredOn: LocalDate,
    val purpose: TxPurpose,
    val amountCentavos: Long,
    val note: String?,
    val categoryName: String?,
    val fromWalletName: String?,
    val toWalletName: String?,
    /** What the entry belongs to, when it belongs to something: the loan's other side, the goal, the baon, the payday. */
    val loanWith: String?,
    val goalName: String?,
    val baonName: String?,
    val paydayName: String?,
)

/**
 * Writes entries as a CSV file that Excel and Google Sheets open as a table. The export is for
 * reading; it is not a backup and cannot be restored from.
 */
object CsvExport {
    val HEADER = listOf("Date", "Type", "Category", "Amount", "In or out", "From wallet", "To wallet", "For", "Note")

    /** Tells Excel the file is UTF-8, so "₱" and "ñ" in notes survive. */
    const val BYTE_ORDER_MARK = "\uFEFF"
    private const val LINE_END = "\r\n"

    /** Writes the header and one line per row to [out]. [typeLabel] names each kind of entry. Returns the row count. */
    fun write(rows: List<ExportRow>, out: Appendable, typeLabel: (TxPurpose) -> String): Int {
        out.append(BYTE_ORDER_MARK)
        out.append(HEADER.joinToString(",")).append(LINE_END)
        for (row in rows) {
            val cells = listOf(
                row.occurredOn.toString(),
                text(typeLabel(row.purpose)),
                text(row.categoryName.orEmpty()),
                amount(row.amountCentavos),
                direction(row),
                text(row.fromWalletName.orEmpty()),
                text(row.toWalletName.orEmpty()),
                text(row.loanWith ?: row.goalName ?: row.baonName ?: row.paydayName ?: ""),
                text(row.note.orEmpty()),
            )
            out.append(cells.joinToString(",")).append(LINE_END)
        }
        return rows.size
    }

    /** Centavos as a plain number a spreadsheet can add up: 123456 -> "1234.56". No peso sign, no commas. */
    fun amount(centavos: Long): String = "${centavos / 100}.${(centavos % 100).toString().padStart(2, '0')}"

    /** Money leaving a wallet is "out", arriving is "in", and going between two of your own wallets is "move". */
    fun direction(row: ExportRow): String = when {
        // A half of a move between the owner's own wallets is a move too, though it names one wallet.
        row.fromWalletName != null && row.toWalletName != null || row.purpose.isOwnMove -> "move"
        row.toWalletName != null -> "in"
        else -> "out"
    }

    /**
     * One cell of typed text.
     * - Text a spreadsheet would run as a formula (starting with = + - or @) gets a leading apostrophe, so
     *   a note such as "=1+1" shows as typed instead of being calculated.
     * - Text holding a comma, a quote or a line break is wrapped in quotes, with quotes doubled.
     */
    fun text(value: String): String {
        val safe = if (value.isNotEmpty() && value[0] in FORMULA_STARTS) "'$value" else value
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"${safe.replace("\"", "\"\"")}\"" else safe
    }

    private val FORMULA_STARTS = setOf('=', '+', '-', '@', '\t', '\r')
}
