package com.pitakaview.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/** November 2026: the 15th is a Sunday, the 30th a Monday. Early pay counts up to 5 days before (2 for weekly). */
class PaydayTrackerTest {
    private val rule = PayRule.KINSENAS_KATAPUSAN
    private val longAgo = LocalDate.of(2026, 1, 1)
    private fun nov(day: Int) = LocalDate.of(2026, 11, day)
    private fun dec(day: Int) = LocalDate.of(2026, 12, day)

    private fun waiting(today: LocalDate, vararg receivedOn: LocalDate, startedOn: LocalDate = longAgo, count: Int = 2, rule: PayRule = this.rule) =
        PaydayTracker.waiting(rule, receivedOn.toList(), today, startedOn, count)

    @Test
    fun aPaydayWaitsFromFiveDaysBeforeUntilTheNextOnesDaysBegin() {
        // Nov 24: still inside the days that belong to Nov 15, which nobody marked.
        assertEquals(listOf(WaitingPayday(nov(15), canReceive = true), WaitingPayday(nov(30), canReceive = false)), waiting(nov(24)))
        // Nov 25: Nov 30's days begin, so the missed Nov 15 lapses instead of nagging forever.
        assertEquals(listOf(WaitingPayday(nov(30), canReceive = true), WaitingPayday(dec(15), canReceive = false)), waiting(nov(25)))
        // On and after the payday it keeps waiting.
        assertEquals(WaitingPayday(nov(30), canReceive = true), waiting(dec(9)).first())
        assertEquals(WaitingPayday(dec(15), canReceive = true), waiting(dec(10)).first())
    }

    @Test
    fun payThatCameEarlyOrLateCountsForItsPayday() {
        // Three days early.
        assertEquals(listOf(WaitingPayday(dec(15), false), WaitingPayday(dec(31), false)), waiting(nov(30), nov(27)))
        // Three days late.
        assertEquals(listOf(WaitingPayday(dec(15), false), WaitingPayday(dec(31), false)), waiting(dec(4), dec(3)))
        // On the day.
        assertEquals(WaitingPayday(dec(15), false), waiting(nov(30), nov(30)).first())
    }

    @Test
    fun payReceivedForThePaydayBeforeDoesNotCountForThisOne() {
        assertEquals(WaitingPayday(nov(30), canReceive = true), waiting(nov(30), nov(15)).first())
        // Nov 24 is one day before Nov 30's days begin, so it belongs to Nov 15.
        assertEquals(WaitingPayday(nov(30), canReceive = true), waiting(nov(26), nov(24)).first())
    }

    @Test
    fun takingTheEntryAwayMakesThePaydayWaitAgain() {
        assertEquals(WaitingPayday(dec(15), false), waiting(nov(30), nov(30)).first())
        assertEquals(WaitingPayday(nov(30), true), waiting(nov(30)).first())
    }

    @Test
    fun aPaydayBeforeTheScheduleWasSetUpIsNeverAskedAbout() {
        // Set up on Nov 20: the Nov 15 payday was before that, so the first one asked about is Nov 30.
        assertEquals(listOf(WaitingPayday(nov(30), canReceive = false)), waiting(nov(20), startedOn = nov(20), count = 1))
        // Set up on the payday itself: that payday counts.
        assertEquals(WaitingPayday(nov(15), canReceive = true), waiting(nov(15), startedOn = nov(15)).first())
    }

    @Test
    fun weeklyPayUsesTwoEarlyDays() {
        val fridays = PayRule(PayFrequency.WEEKLY, dayOfWeek = DayOfWeek.FRIDAY)
        // Wed Nov 25 is two days before Fri Nov 27.
        assertEquals(listOf(WaitingPayday(nov(27), true), WaitingPayday(dec(4), false)), waiting(nov(25), rule = fridays))
        // Tue Nov 24 still belongs to last Friday.
        assertEquals(WaitingPayday(nov(20), true), waiting(nov(24), rule = fridays).first())
        // Last Friday's pay does not count for this Friday; pay on Thursday does.
        assertEquals(WaitingPayday(nov(27), true), waiting(nov(25), nov(20), rule = fridays).first())
        assertEquals(listOf(WaitingPayday(dec(4), false), WaitingPayday(dec(11), false)), waiting(nov(27), nov(26), rule = fridays))
    }

    @Test
    fun monthlyPayOnTheLastDay() {
        val monthEnd = PayRule(PayFrequency.MONTHLY, firstDay = PayRule.LAST_DAY)
        assertEquals(listOf(WaitingPayday(nov(30), true), WaitingPayday(dec(31), false)), waiting(dec(2), rule = monthEnd))
        assertEquals(listOf(WaitingPayday(dec(31), false)), waiting(dec(2), dec(1), count = 1, rule = monthEnd))
    }

    @Test
    fun askingForNoneGivesNone() {
        assertTrue(waiting(nov(25), count = 0).isEmpty())
    }
}
