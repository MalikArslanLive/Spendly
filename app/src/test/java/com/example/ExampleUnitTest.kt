package com.example

import com.example.util.Formatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testPkrFormatting() {
        assertEquals("Rs. 50", Formatters.formatPkr(50.0))
        assertEquals("Rs. 100", Formatters.formatPkr(100.0))
        assertEquals("Rs. 1,250", Formatters.formatPkr(1250.0))
        assertEquals("Rs. 18,450", Formatters.formatPkr(18450.0))
        assertEquals("Rs. 0", Formatters.formatPkr(0.0))
    }

    @Test
    fun testMonthNavigation() {
        val next = Formatters.getNextMonth("2026-10")
        assertEquals("2026-11", next)

        val prev = Formatters.getPreviousMonth("2026-10")
        assertEquals("2026-09", prev)

        val nextYear = Formatters.getNextMonth("2026-12")
        assertEquals("2027-01", nextYear)
    }

    @Test
    fun testCalendarDaysGeneration() {
        val days = Formatters.generateCalendarDays("2026-10")
        assertTrue(days.isNotEmpty())
        assertTrue(days.size == 35 || days.size == 42)
        val octDays = days.filter { it.isCurrentMonth }
        assertEquals(31, octDays.size)
    }
}
