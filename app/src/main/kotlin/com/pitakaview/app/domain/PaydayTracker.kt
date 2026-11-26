package com.pitakaview.app.domain

import java.time.LocalDate

/** A payday that has not been marked received. [canReceive] is true for the one being waited for now. */
data class WaitingPayday(val date: LocalDate, val canReceive: Boolean)

/**
 * Works out which paydays are still waiting to be received, from the schedule's rule and the dates of
 * the income entries linked to it. Nothing extra is stored, so deleting such an entry un-receives the payday.
 *
 * Each payday owns a stretch of days: from [earlyDays] before it until the next payday's stretch begins.
 * An income entry linked to the schedule inside that stretch marks the payday received, whether the pay
 * came a little early or late. A payday nobody marked lapses when the next stretch begins, so an old
 * miss does not nag forever.
 */
object PaydayTracker {
    /** How far back paydays and income entries can still matter: one monthly gap plus the early days, with room. */
    const val LOOK_BACK_DAYS = 45L

    /** Pay that arrives up to this many days before a payday counts for that payday. */
    fun earlyDays(frequency: PayFrequency): Long = if (frequency == PayFrequency.WEEKLY) 2 else 5

    /**
     * The next [count] paydays not yet received, starting with the one being waited for now.
     * Paydays before [startedOn] (the day the schedule was set up) are never asked about.
     */
    fun waiting(
        rule: PayRule,
        receivedOn: Collection<LocalDate>,
        today: LocalDate,
        startedOn: LocalDate,
        count: Int,
    ): List<WaitingPayday> {
        if (count <= 0) return emptyList()
        val early = earlyDays(rule.frequency)
        // Weekly pay has up to 7 paydays inside the look-back; the rest covers [count] ahead plus any already received.
        val paydays = PayCalendar.upcoming(rule, from = today.minusDays(LOOK_BACK_DAYS), count = count + 12)
        val current = paydays.indexOfLast { it.minusDays(early) <= today }.coerceAtLeast(0)
        val result = ArrayList<WaitingPayday>(count)
        for (k in current until paydays.size) {
            val opens = paydays[k].minusDays(early)
            val closes = paydays.getOrNull(k + 1)?.minusDays(early)
            val received = receivedOn.any { it >= opens && (closes == null || it < closes) }
            if (!received && paydays[k] >= startedOn) result += WaitingPayday(paydays[k], canReceive = k == current)
            if (result.size == count) break
        }
        return result
    }
}
