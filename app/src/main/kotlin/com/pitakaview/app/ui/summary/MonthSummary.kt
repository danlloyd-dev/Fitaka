package com.pitakaview.app.ui.summary

import com.pitakaview.app.data.db.dao.CategoryTotal
import com.pitakaview.app.data.db.dao.MonthPurposeTotal
import com.pitakaview.app.domain.Peso
import com.pitakaview.app.domain.TxPurpose
import com.pitakaview.app.ui.ledger.CategoryShare
import com.pitakaview.app.ui.ledger.monthName
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong

/** One month on the chart: what came in and what went out. */
data class MonthBar(val month: YearMonth, val inCentavos: Long, val outCentavos: Long)

/**
 * One month summed up. "In" is income plus baon received; "out" is spending plus baon given. Moves
 * between your own wallets, savings and loans are neither: they do not make you richer or poorer.
 */
data class MonthSummary(
    val month: YearMonth,
    val isThisMonth: Boolean,
    val incomeCentavos: Long = 0,
    val baonReceivedCentavos: Long = 0,
    val spendingCentavos: Long = 0,
    val baonGivenCentavos: Long = 0,
    /** Put into savings goals minus taken out, this month. */
    val setAsideCentavos: Long = 0,
    /** The six months ending with this one, oldest first. */
    val bars: List<MonthBar> = emptyList(),
    val topCategories: List<CategoryShare> = emptyList(),
    /** False until the database has answered. */
    val loaded: Boolean = false,
) {
    val inCentavos: Long get() = incomeCentavos + baonReceivedCentavos
    val outCentavos: Long get() = spendingCentavos + baonGivenCentavos
    val leftCentavos: Long get() = inCentavos - outCentavos
    val hasChart: Boolean get() = bars.any { it.inCentavos != 0L || it.outCentavos != 0L }
}

object MonthSummaryBuilder {
    const val MONTHS = 6
    const val TOP_CATEGORIES = 5
    const val EVERYTHING_ELSE = "Everything else"

    /**
     * [totals] are the per-month, per-kind sums for the six months ending with [month] (one query);
     * [categories] are this month's spending by category, biggest first (a second query).
     */
    fun build(month: YearMonth, isThisMonth: Boolean, totals: List<MonthPurposeTotal>, categories: List<CategoryTotal>): MonthSummary {
        val byMonth = totals.groupBy({ YearMonth.parse(it.month) }, { it.purpose to it.totalCentavos })
        fun sum(of: YearMonth, purpose: TxPurpose) = byMonth[of].orEmpty().filter { it.first == purpose }.sumOf { it.second }
        val bars = (MONTHS - 1 downTo 0).map { back ->
            val m = month.minusMonths(back.toLong())
            MonthBar(m, sum(m, TxPurpose.INCOME) + sum(m, TxPurpose.BAON_RECEIVED), sum(m, TxPurpose.EXPENSE) + sum(m, TxPurpose.BAON_GIVEN))
        }
        val top = categories.take(TOP_CATEGORIES).map { CategoryShare(it.name, it.totalCentavos, it.iconKey) }
        val rest = categories.drop(TOP_CATEGORIES).sumOf { it.totalCentavos }
        return MonthSummary(
            month = month,
            isThisMonth = isThisMonth,
            incomeCentavos = sum(month, TxPurpose.INCOME),
            baonReceivedCentavos = sum(month, TxPurpose.BAON_RECEIVED),
            spendingCentavos = sum(month, TxPurpose.EXPENSE),
            baonGivenCentavos = sum(month, TxPurpose.BAON_GIVEN),
            setAsideCentavos = sum(month, TxPurpose.SAVINGS_DEPOSIT) - sum(month, TxPurpose.SAVINGS_WITHDRAWAL),
            bars = bars,
            topCategories = if (rest > 0) top + CategoryShare(EVERYTHING_ELSE, rest) else top,
            loaded = true,
        )
    }

    /**
     * How this month's money out compares with the month before: "₱1,200.00 more went out than in October".
     * Null when there is nothing in the month before to compare with. For a month still running the screen
     * adds "So far this month." on its own line.
     */
    fun comparison(summary: MonthSummary): String? {
        val before = summary.bars.getOrNull(summary.bars.size - 2) ?: return null
        if (before.outCentavos == 0L) return null
        val difference = summary.outCentavos - before.outCentavos
        val then = monthName(before.month)
        return when {
            difference > 0 -> "${Peso.format(difference)} more went out than in $then"
            difference < 0 -> "${Peso.format(-difference)} less went out than in $then"
            else -> "The same went out as in $then"
        }
    }

    /** A peso amount short enough for a chart's axis: "₱0", "₱850", "₱1.5k", "₱20k", "₱1.2M". */
    fun shortPeso(pesos: Double): String {
        val size = abs(pesos)
        val centavos = (size * 100).roundToLong()
        val text = when {
            size >= 1_000_000 -> oneDecimal(size / 1_000_000) + "M"
            size >= 1_000 -> oneDecimal(size / 1_000) + "k"
            centavos % 100 == 0L -> "${centavos / 100}"
            // Below a thousand a label is never rounded to a whole peso it does not stand for.
            else -> "${centavos / 100}.${(centavos % 100).toString().padStart(2, '0')}"
        }
        return (if (pesos < 0) "−" else "") + Peso.SIGN + text
    }

    /**
     * The gap between the chart's amount lines for a tallest bar of [maxPesos]: a round amount (1, 2 or 5
     * times a power of ten) that gives about four lines. The lines are chosen here, not left to the chart
     * library, so each line sits exactly on the amount its label says.
     */
    fun axisStep(maxPesos: Double): Double {
        if (maxPesos <= 0) return 1.0
        val raw = maxPesos / 4
        val magnitude = 10.0.pow(floor(log10(raw)))
        val nice = when {
            raw <= magnitude -> 1.0
            raw <= 2 * magnitude -> 2.0
            raw <= 5 * magnitude -> 5.0
            else -> 10.0
        }
        return nice * magnitude
    }

    /** The top of the chart: the first line at or above the tallest bar. */
    fun axisTop(maxPesos: Double): Double {
        val step = axisStep(maxPesos)
        // The tiny allowance keeps a bar that ends exactly on a line from being pushed a whole step higher.
        return ceil(maxPesos / step - 1e-9).coerceAtLeast(1.0) * step
    }

    /** The amounts the chart's lines stand on, from zero to the top. */
    fun axisLines(maxPesos: Double): List<Double> {
        val step = axisStep(maxPesos)
        val count = (axisTop(maxPesos) / step).roundToLong().toInt()
        return (0..count).map { it * step }
    }

    private fun oneDecimal(value: Double): String {
        val tenths = (value * 10).roundToLong()
        return if (tenths % 10 == 0L) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
    }
}
