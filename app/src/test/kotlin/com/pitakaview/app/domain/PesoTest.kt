package com.pitakaview.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PesoTest {
    @Test
    fun formatsCentavosAsPesos() {
        assertEquals("₱0.00", Peso.format(0))
        assertEquals("₱0.05", Peso.format(5))
        assertEquals("₱999.99", Peso.format(99_999))
        assertEquals("₱1,234.56", Peso.format(123_456))
        assertEquals("₱1,000,000.00", Peso.format(100_000_000))
        assertEquals("−₱20.50", Peso.format(-2_050))
    }

    @Test
    fun parsesWhatPeopleType() {
        assertEquals(123_450L, Peso.parse("1,234.5"))
        assertEquals(2_000L, Peso.parse("₱ 20"))
        assertEquals(5L, Peso.parse("0.05"))
        assertEquals(1_500_000L, Peso.parse(" 15000 "))
    }

    @Test
    fun rejectsAnythingThatIsNotAPlainAmount() {
        assertNull(Peso.parse(""))
        assertNull(Peso.parse("abc"))
        assertNull(Peso.parse("1.234"))
        assertNull(Peso.parse("-5"))
        assertNull(Peso.parse("1."))
    }

    @Test
    fun formatThenParseRoundTrips() {
        for (centavos in listOf(0L, 1L, 99L, 100L, 123_456L, 987_654_321L)) {
            assertEquals(centavos, Peso.parse(Peso.format(centavos)))
        }
    }
}
