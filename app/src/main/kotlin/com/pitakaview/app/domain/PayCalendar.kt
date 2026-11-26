package com.pitakaview.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import kotlin.math.min

/** When a pay schedule pays out. Day 31 ([LAST_DAY]) always means the last day of the month. */
data class PayRule(
    val frequency: PayFrequency,
    val firstDay: Int = 15,
    val secondDay: Int = LAST_DAY,
    val dayOfWeek: DayOfWeek = DayOfWeek.FRIDAY,
    val weekendRule: WeekendRule = WeekendRule.KEEP,
) {
    init {
        require(firstDay in 1..LAST_DAY && secondDay in 1..LAST_DAY) { "Pay days must be 1..31" }
    }

    companion object {
        const val LAST_DAY = 31

        /** Kinsenas (15th) and katapusan (last day), paid on the date itself even when it is a weekend. */
        val KINSENAS_KATAPUSAN = PayRule(PayFrequency.SEMI_MONTHLY, firstDay = 15, secondDay = LAST_DAY)
    }
}

object PayCalendar {

    /** The next [count] paydays on or after [from], in order. */
    fun upcoming(rule: PayRule, from: LocalDate, count: Int): List<LocalDate> {
        require(count >= 0)
        if (count == 0) return emptyList()
        val raw: Sequence<LocalDate> = when (rule.frequency) {
            // Start a week early: a Sunday payday moved back to Friday can fall before the next Sunday.
            PayFrequency.WEEKLY ->
                generateSequence(from.with(TemporalAdjusters.nextOrSame(rule.dayOfWeek)).minusWeeks(1)) {
                    it.plusWeeks(1)
                }

            PayFrequency.SEMI_MONTHLY, PayFrequency.MONTHLY -> {
                val days = if (rule.frequency == PayFrequency.SEMI_MONTHLY) {
                    listOf(rule.firstDay, rule.secondDay)
                } else {
                    listOf(rule.firstDay)
                }
                // Start a month early: last month's payday can be pushed into this month by the weekend rule.
                generateSequence(YearMonth.from(from).minusMonths(1)) { it.plusMonths(1) }
                    .flatMap { month ->
                        days.map { month.atDay(min(it, month.lengthOfMonth())) }.distinct().sorted().asSequence()
                    }
            }
        }
        return raw.map { adjustForWeekend(it, rule.weekendRule) }
            .filter { it >= from }
            .distinct()
            .take(count)
            .toList()
    }

    fun adjustForWeekend(date: LocalDate, rule: WeekendRule): LocalDate = when (rule) {
        WeekendRule.KEEP -> date
        WeekendRule.PREVIOUS_WEEKDAY -> when (date.dayOfWeek) {
            DayOfWeek.SATURDAY -> date.minusDays(1)
            DayOfWeek.SUNDAY -> date.minusDays(2)
            else -> date
        }
        WeekendRule.NEXT_WEEKDAY -> when (date.dayOfWeek) {
            DayOfWeek.SATURDAY -> date.plusDays(2)
            DayOfWeek.SUNDAY -> date.plusDays(1)
            else -> date
        }
    }
}
