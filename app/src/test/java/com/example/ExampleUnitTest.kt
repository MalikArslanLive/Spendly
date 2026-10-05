package com.example

import com.example.data.Expense
import com.example.sync.GoogleSheetsSyncManager
import com.example.ui.screens.extractSpreadsheetIdFromUrl
import com.example.util.CsvExporter
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

    @Test
    fun testCsvExportFormat() {
        val expenses = listOf(
            Expense(id = 1, amount = 50.0, category = "Tea/Coffee", note = "Office tea", date = "2026-10-05", time = "10:30"),
            Expense(id = 2, amount = 100.0, category = "Travel", note = "Rickshaw", date = "2026-10-05", time = "14:20")
        )
        val csv = CsvExporter.generateCsvString(expenses)
        val lines = csv.trim().split("\n")
        assertEquals(3, lines.size)
        assertEquals("Date,Time,Amount,Category,Note", lines[0])
        assertEquals("2026-10-05,10:30,50,Tea/Coffee,Office tea", lines[1])
        assertEquals("2026-10-05,14:20,100,Travel,Rickshaw", lines[2])
    }

    @Test
    fun testMultiUserSpreadsheetDefaultsAndMonthTab() {
        assertEquals("My Expenses", GoogleSheetsSyncManager.DEFAULT_SPREADSHEET_TITLE)
        val monthTab = Formatters.formatMonthYear("2026-10")
        assertEquals("October 2026", monthTab)
        val novTab = Formatters.formatMonthYear("2026-11")
        assertEquals("November 2026", novTab)
    }

    @Test
    fun testExtractSpreadsheetIdFromUrl() {
        val fullUrl = "https://docs.google.com/spreadsheets/d/1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms/edit#gid=0"
        assertEquals("1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms", extractSpreadsheetIdFromUrl(fullUrl))

        val simpleUrl = "https://docs.google.com/spreadsheets/d/1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms"
        assertEquals("1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms", extractSpreadsheetIdFromUrl(simpleUrl))

        val rawId = "1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms"
        assertEquals("1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms", extractSpreadsheetIdFromUrl(rawId))
    }
}
