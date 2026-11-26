package com.pitakaview.app.domain

import com.pitakaview.app.data.db.dao.BaonActivityRow
import com.pitakaview.app.data.db.entity.BaonEntity
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.min

/**
 * How much baon is left in the current period.
 * - RECEIVE: budget = baon received this period, used = spending charged to this baon.
 * - GIVE: budget = the planned amount, used = baon given this period.
 */
data class BaonSummary(
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val budgetCentavos: Long,
    val usedCentavos: Long,
) {
    val leftCentavos: Long get() = budgetCentavos - usedCentavos
}

object BaonMath {

    /**
     * The period containing [today]. Periods are anchored on [startDate]: weekly baon restarts on the
     * same weekday it started, monthly baon on the same day of the month (clamped to short months).
     * Before [startDate], the first period is returned.
     */
    fun currentPeriod(period: BaonPeriod, startDate: LocalDate, today: LocalDate): ClosedRange<LocalDate> {
        val day = if (today < startDate) startDate else today
        return when (period) {
            BaonPeriod.DAILY -> day..day
            BaonPeriod.WEEKLY -> {
                val offset = Math.floorMod(day.toEpochDay() - startDate.toEpochDay(), 7L)
                val start = day.minusDays(offset)
                start..start.plusDays(6)
            }
            BaonPeriod.MONTHLY -> {
                fun anchorIn(month: YearMonth) = month.atDay(min(startDate.dayOfMonth, month.lengthOfMonth()))
                val thisMonth = YearMonth.from(day)
                val start = anchorIn(thisMonth).let { if (it > day) anchorIn(thisMonth.minusMonths(1)) else it }
                start..anchorIn(YearMonth.from(start).plusMonths(1)).minusDays(1)
            }
        }
    }

    fun summarize(baon: BaonEntity, activity: List<BaonActivityRow>, today: LocalDate): BaonSummary {
        val period = currentPeriod(baon.period, baon.startDate, today)
        val rows = activity.filter { it.baonId == baon.id && it.occurredOn in period }
        fun sum(purpose: TxPurpose) = rows.filter { it.purpose == purpose }.sumOf { it.amountCentavos }
        return when (baon.direction) {
            BaonDirection.RECEIVE -> BaonSummary(
                periodStart = period.start,
                periodEnd = period.endInclusive,
                budgetCentavos = sum(TxPurpose.BAON_RECEIVED),
                usedCentavos = sum(TxPurpose.EXPENSE),
            )
            BaonDirection.GIVE -> BaonSummary(
                periodStart = period.start,
                periodEnd = period.endInclusive,
                budgetCentavos = baon.amountCentavos,
                usedCentavos = sum(TxPurpose.BAON_GIVEN),
            )
        }
    }
}
