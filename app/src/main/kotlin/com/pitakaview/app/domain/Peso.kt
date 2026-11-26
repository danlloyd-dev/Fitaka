package com.pitakaview.app.domain

import kotlin.math.abs

/**
 * Peso amounts are stored and added up as whole centavos (₱1.00 = 100), never as floating point,
 * so totals cannot drift. This object turns centavos into text and back.
 */
object Peso {
    const val SIGN = "₱"
    private const val MINUS = "−"
    private val INPUT = Regex("""^(\d{1,13})(?:\.(\d{1,2}))?$""")

    /** 123456 -> "₱1,234.56", -2050 -> "−₱20.50". */
    fun format(centavos: Long): String {
        require(centavos != Long.MIN_VALUE) { "Amount out of range" }
        val magnitude = abs(centavos)
        val pesos = (magnitude / 100).toString().reversed().chunked(3).joinToString(",").reversed()
        val cents = (magnitude % 100).toString().padStart(2, '0')
        val text = "$SIGN$pesos.$cents"
        return if (centavos < 0) "$MINUS$text" else text
    }

    /**
     * Reads what a person typed ("1,234.5", "₱ 20", "0.05") as centavos.
     * Returns null for anything that is not a plain non-negative amount with at most 2 decimals.
     */
    fun parse(input: String): Long? {
        val cleaned = input.trim().removePrefix(SIGN).replace(",", "").replace(" ", "")
        val match = INPUT.matchEntire(cleaned) ?: return null
        val pesos = match.groupValues[1].toLong()
        val cents = match.groupValues[2].padEnd(2, '0').toLong()
        return pesos * 100 + cents
    }
}
