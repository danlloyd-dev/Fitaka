package com.pitakaview.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/** Weekdays below were checked against Python's calendar on 2026-11-25. */
class PayCalendarTest {
    private fun d(text: String) = LocalDate.parse(text)

    private val fridayBefore = PayRule(PayFrequency.SEMI_MONTHLY, weekendRule = WeekendRule.PREVIOUS_WEEKDAY)

    @Test
    fun paydayOnAWeekendStaysOnItsDateByDefault() {
        // 2026-11-15 is a Sunday; 2026-11-30 is a Monday. The owner is paid on the date itself (decided 2026-11-26).
        assertEquals(
            listOf(d("2026-11-15"), d("2026-11-30")),
            PayCalendar.upcoming(PayRule.KINSENAS_KATAPUSAN, from = d("2026-11-01"), count = 2),
        )
    }

    @Test
    fun kinsenasOnASundayCanMoveToTheFridayBefore() {
        assertEquals(
            listOf(d("2026-11-13"), d("2026-11-30")),
            PayCalendar.upcoming(fridayBefore, from = d("2026-11-01"), count = 2),
        )
    }

    @Test
    fun saturdayPaydayCanMoveToTheNextWeekday() {
        // 2026-08-15 is a Saturday.
        val rule = PayRule(PayFrequency.SEMI_MONTHLY, 15, PayRule.LAST_DAY, weekendRule = WeekendRule.NEXT_WEEKDAY)
        assertEquals(listOf(d("2026-08-17"), d("2026-08-31")), PayCalendar.upcoming(rule, d("2026-08-01"), 2))
    }

    @Test
    fun katapusanIsTheLastDayOfFebruary() {
        // 2027-02-28 is a Sunday; 2028 is a leap year and 2028-02-29 is a Tuesday.
        val sameDate = PayRule.KINSENAS_KATAPUSAN
        assertEquals(listOf(d("2027-02-15"), d("2027-02-28")), PayCalendar.upcoming(sameDate, d("2027-02-01"), 2))
        assertEquals(listOf(d("2028-02-15"), d("2028-02-29")), PayCalendar.upcoming(sameDate, d("2028-02-01"), 2))
        assertEquals(listOf(d("2027-02-15"), d("2027-02-26")), PayCalendar.upcoming(fridayBefore, d("2027-02-01"), 2))
    }

    @Test
    fun lastMonthsPaydayPushedIntoThisMonthIsIncluded() {
        // 2026-05-31 is a Sunday -> next weekday is Monday 2026-06-01.
        val rule = PayRule(PayFrequency.MONTHLY, firstDay = PayRule.LAST_DAY, weekendRule = WeekendRule.NEXT_WEEKDAY)
        assertEquals(listOf(d("2026-06-01"), d("2026-06-30")), PayCalendar.upcoming(rule, d("2026-06-01"), 2))
    }

    @Test
    fun weeklyPaydayOnSundayCanMoveToFriday() {
        // From Wednesday 2026-11-25: Sundays 11-29 and 12-06 become Fridays 11-27 and 12-04.
        val rule = PayRule(PayFrequency.WEEKLY, dayOfWeek = DayOfWeek.SUNDAY, weekendRule = WeekendRule.PREVIOUS_WEEKDAY)
        assertEquals(listOf(d("2026-11-27"), d("2026-12-04")), PayCalendar.upcoming(rule, d("2026-11-25"), 2))
    }

    @Test
    fun weeklyPaydayOnSundayStaysOnSundayByDefault() {
        val rule = PayRule(PayFrequency.WEEKLY, dayOfWeek = DayOfWeek.SUNDAY)
        assertEquals(listOf(d("2026-11-29"), d("2026-12-06")), PayCalendar.upcoming(rule, d("2026-11-25"), 2))
    }

    @Test
    fun startDateThatIsAPaydayCounts() {
        assertEquals(
            listOf(d("2026-11-30")),
            PayCalendar.upcoming(PayRule.KINSENAS_KATAPUSAN, d("2026-11-30"), 1),
        )
    }
}
