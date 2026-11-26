package com.pitakaview.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class DayClockTest {
    @Test
    fun todayMovesOnlyWhenRefreshedAfterMidnight() {
        var time = LocalDateTime.of(2026, 11, 30, 23, 59, 30)
        val clock = DayClock { time }
        assertEquals(LocalDate.of(2026, 11, 30), clock.today.value)
        // 30 s to midnight, plus the 1 s safety margin.
        assertEquals(31_000L, clock.millisUntilTomorrow())

        time = LocalDateTime.of(2026, 12, 1, 0, 0, 1)
        assertEquals("unchanged until someone refreshes", LocalDate.of(2026, 11, 30), clock.today.value)
        clock.refresh()
        assertEquals(LocalDate.of(2026, 12, 1), clock.today.value)
    }
}
