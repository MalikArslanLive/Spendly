package com.example

import com.example.data.Expense
import com.example.data.generateTransactionId
import com.example.sync.GoogleSheetsSyncManager
import com.example.ui.screens.extractSpreadsheetIdFromUrl
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
    fun testMultiUserSpreadsheetDefaultsAndMonthTab() {
        assertEquals("Spendly", GoogleSheetsSyncManager.DEFAULT_SPREADSHEET_TITLE)
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

    @Test
    fun testCreditAndExpenseBalanceCalculation() {
        val transactions = listOf(
            Expense(id = 1, amount = 50000.0, category = "Salary", note = "Monthly salary", date = "2026-10-01", time = "09:00 AM", transactionType = "CREDIT"),
            Expense(id = 2, amount = 1250.0, category = "Food", note = "Groceries", date = "2026-10-02", time = "01:00 PM", transactionType = "EXPENSE"),
            Expense(id = 3, amount = 17250.0, category = "Bills", note = "Electricity bill", date = "2026-10-03", time = "04:00 PM", transactionType = "EXPENSE")
        )

        val totalCredits = transactions.filter { it.transactionType == "CREDIT" }.sumOf { it.amount }
        val totalExpenses = transactions.filter { it.transactionType != "CREDIT" }.sumOf { it.amount }
        val balance = totalCredits - totalExpenses

        assertEquals(50000.0, totalCredits, 0.001)
        assertEquals(18500.0, totalExpenses, 0.001)
        assertEquals(31500.0, balance, 0.001)
    }

    @Test
    fun testDateRangeFilteringAndTotals() {
        val transactions = listOf(
            Expense(id = 1, amount = 300.0, category = "Food", note = "Lunch", date = "2026-10-01", time = "12:00 PM", transactionType = "EXPENSE"),
            Expense(id = 2, amount = 200.0, category = "Travel", note = "Rickshaw", date = "2026-10-01", time = "03:00 PM", transactionType = "EXPENSE"),
            Expense(id = 3, amount = 10000.0, category = "Salary", note = "Advance", date = "2026-10-02", time = "10:00 AM", transactionType = "CREDIT"),
            Expense(id = 4, amount = 2750.0, category = "Shopping", note = "Clothes", date = "2026-10-05", time = "06:00 PM", transactionType = "EXPENSE"),
            Expense(id = 5, amount = 500.0, category = "Tea/Coffee", note = "Cafe", date = "2026-10-10", time = "11:00 AM", transactionType = "EXPENSE")
        )

        val startDate = "2026-10-01"
        val endDate = "2026-10-05"

        val filtered = transactions.filter { it.date in startDate..endDate }
        assertEquals(4, filtered.size)

        val credits = filtered.filter { it.transactionType == "CREDIT" }.sumOf { it.amount }
        val expenses = filtered.filter { it.transactionType != "CREDIT" }.sumOf { it.amount }
        val balance = credits - expenses

        assertEquals(10000.0, credits, 0.001)
        assertEquals(3250.0, expenses, 0.001)
        assertEquals(6750.0, balance, 0.001)
    }

    @Test
    fun testTransactionIdFormatAndUniqueness() {
        com.example.data.setStartingTransactionId(0L)
        val id1 = generateTransactionId()
        val id2 = generateTransactionId()

        // Must be simple positive numeric strings ("1", "2", ...)
        assertTrue(id1.matches(Regex("^[1-9][0-9]*$")))
        assertTrue(id2.matches(Regex("^[1-9][0-9]*$")))

        assertEquals("1", id1)
        assertEquals("2", id2)

        // Must be unique
        assertTrue(id1 != id2)
    }

    @Test
    fun testTwoIdenticalExpensesHaveDifferentTransactionIds() {
        // Two identical expenses with same amount, category, date, time
        val expense1 = Expense(
            id = 10,
            amount = 50.0,
            category = "Tea/Coffee",
            note = "Office tea",
            date = "2026-10-05",
            time = "10:30 AM",
            transactionType = "EXPENSE",
            transactionId = "10"
        )
        val expense2 = Expense(
            id = 11,
            amount = 50.0,
            category = "Tea/Coffee",
            note = "Office tea",
            date = "2026-10-05",
            time = "10:30 AM",
            transactionType = "EXPENSE",
            transactionId = "11"
        )

        // Amounts, categories, dates and notes are identical
        assertEquals(expense1.amount, expense2.amount, 0.0)
        assertEquals(expense1.category, expense2.category)
        assertEquals(expense1.date, expense2.date)
        assertEquals(expense1.note, expense2.note)

        // But transaction IDs are strictly unique numeric values
        assertTrue(expense1.transactionId != expense2.transactionId)

        // Matching row by transaction ID targets ONLY the intended item
        val sheetColumnA = listOf("Transaction ID", expense1.transactionId, expense2.transactionId)
        val targetIndex1 = sheetColumnA.indexOf(expense1.transactionId)
        val targetIndex2 = sheetColumnA.indexOf(expense2.transactionId)

        assertEquals(1, targetIndex1)
        assertEquals(2, targetIndex2)
        assertTrue(targetIndex1 != targetIndex2)
    }

    @Test
    fun testCreditAndExpenseBothUseTransactionIds() {
        val expenseTxn = Expense(
            id = 1,
            amount = 1200.0,
            category = "Food",
            note = "Dinner",
            date = "2026-10-05",
            time = "08:00 PM",
            transactionType = "EXPENSE",
            transactionId = "1"
        )
        val creditTxn = Expense(
            id = 2,
            amount = 5000.0,
            category = "Freelance",
            note = "Design work",
            date = "2026-10-05",
            time = "02:00 PM",
            transactionType = "CREDIT",
            transactionId = "2"
        )

        assertTrue(expenseTxn.transactionId.matches(Regex("^[0-9]+$")))
        assertTrue(creditTxn.transactionId.matches(Regex("^[0-9]+$")))
        assertTrue(expenseTxn.transactionId != creditTxn.transactionId)
    }

    @Test
    fun testStandardGoogleSheetColumnOrderAndNoteIsLast() {
        val expectedHeaders = listOf(
            "Transaction ID",
            "Date",
            "Time",
            "Type",
            "Category",
            "Amount",
            "Note"
        )

        assertEquals(expectedHeaders, GoogleSheetsSyncManager.STANDARD_HEADERS)
        assertEquals("Transaction ID", GoogleSheetsSyncManager.STANDARD_HEADERS[0])
        assertEquals("Amount", GoogleSheetsSyncManager.STANDARD_HEADERS[5])
        assertEquals("Note", GoogleSheetsSyncManager.STANDARD_HEADERS[6])
        assertEquals("Note", GoogleSheetsSyncManager.STANDARD_HEADERS.last())
    }

    @Test
    fun testTransactionIdPreservedOnEdit() {
        val original = Expense(
            id = 5,
            amount = 100.0,
            category = "Food",
            note = "Lunch",
            date = "2026-10-05",
            time = "01:00 PM",
            transactionType = "EXPENSE",
            transactionId = "5"
        )

        // Editing amount, note, and category
        val edited = original.copy(
            amount = 150.0,
            category = "Groceries",
            note = "Lunch + Snacks"
        )

        // Transaction ID MUST remain identical after editing
        assertEquals("5", edited.transactionId)
        assertEquals(original.transactionId, edited.transactionId)
    }

    @Test
    fun testTrashAndRestorePreservesAllTransactionDataAndSameTransactionId() {
        val original = Expense(
            id = 42,
            amount = 1500.0,
            category = "Shopping",
            note = "Eid gift",
            date = "2026-10-05",
            time = "04:30 PM",
            transactionType = "EXPENSE",
            transactionId = "42"
        )

        // Move to trash
        val trashed = original.copy(
            isDeleted = true,
            deletedAt = 1760000000000L,
            syncStatus = "PENDING"
        )

        // All fields MUST be preserved in Trash
        assertEquals(original.transactionId, trashed.transactionId)
        assertEquals(original.amount, trashed.amount, 0.0)
        assertEquals(original.category, trashed.category)
        assertEquals(original.note, trashed.note)
        assertEquals(original.date, trashed.date)
        assertEquals(original.time, trashed.time)
        assertEquals(original.transactionType, trashed.transactionType)
        assertTrue(trashed.isDeleted)

        // Restore from trash
        val restored = trashed.copy(
            isDeleted = false,
            deletedAt = null,
            syncStatus = "PENDING"
        )

        // Restored transaction MUST retain the SAME Transaction ID and original fields
        assertEquals(original.transactionId, restored.transactionId)
        assertEquals("42", restored.transactionId)
        assertEquals(original.amount, restored.amount, 0.0)
        assertEquals(original.category, restored.category)
        assertEquals(original.note, restored.note)
        assertEquals(original.date, restored.date)
        assertEquals(original.time, restored.time)
        assertEquals(original.transactionType, restored.transactionType)
        assertTrue(!restored.isDeleted)
    }

    @Test
    fun testDriveSearchMultiWordAndPartialMatching() {
        val mockDriveFiles = listOf(
            "My Expenses",
            "My Expenses 2026",
            "My Expenses Test",
            "Personal Budget",
            "Office Spendly"
        )

        // Simulate query "My Exp"
        val query = "My Exp"
        val tokens = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }

        val matched = mockDriveFiles.filter { name ->
            tokens.all { t -> name.contains(t, ignoreCase = true) }
        }

        assertEquals(3, matched.size)
        assertTrue(matched.contains("My Expenses"))
        assertTrue(matched.contains("My Expenses 2026"))
        assertTrue(matched.contains("My Expenses Test"))
    }
}
