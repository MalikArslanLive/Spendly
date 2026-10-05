package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ExpenseRepository(
    private val expenseDao: ExpenseDao,
    private val context: Context? = null
) {

    private val idMutex = Mutex()

    val allExpenses: Flow<List<Expense>> = expenseDao.getAllExpenses()

    val trashExpenses: Flow<List<Expense>> = expenseDao.getTrashExpenses()

    val trashCount: Flow<Int> = expenseDao.getTrashCount()

    fun getExpensesByDate(date: String): Flow<List<Expense>> {
        return expenseDao.getExpensesByDate(date)
    }

    fun getExpensesByMonth(monthPrefix: String): Flow<List<Expense>> {
        return expenseDao.getExpensesByMonth(monthPrefix)
    }

    fun getExpensesByDateRange(startDate: String, endDate: String): Flow<List<Expense>> {
        return expenseDao.getExpensesByDateRange(startDate, endDate)
    }

    suspend fun getExpenseById(id: Long): Expense? {
        return expenseDao.getExpenseById(id)
    }

    suspend fun getExpenseByTransactionId(txnId: String): Expense? {
        return expenseDao.getExpenseByTransactionId(txnId)
    }

    suspend fun insertExpense(expense: Expense): Long {
        return expenseDao.insertExpense(expense)
    }

    suspend fun updateExpense(expense: Expense) {
        expenseDao.updateExpense(expense)
    }

    suspend fun moveToTrash(id: Long) {
        expenseDao.moveToTrash(id, System.currentTimeMillis())
    }

    suspend fun moveAllToTrash() {
        expenseDao.moveAllToTrash(System.currentTimeMillis())
    }

    suspend fun restoreFromTrash(id: Long) {
        expenseDao.restoreFromTrash(id)
    }

    suspend fun deletePermanently(id: Long) {
        expenseDao.deletePermanently(id)
    }

    suspend fun emptyTrash() {
        expenseDao.emptyTrash()
    }

    suspend fun getAllActiveExpensesList(): List<Expense> {
        return expenseDao.getAllActiveExpensesList()
    }

    suspend fun getPendingSyncExpenses(): List<Expense> {
        return expenseDao.getPendingSyncExpenses()
    }

    suspend fun markExpensesSynced(ids: List<Long>) {
        expenseDao.markExpensesSynced(ids)
    }

    suspend fun updateSyncStatus(id: Long, status: String) {
        expenseDao.updateSyncStatus(id, status, System.currentTimeMillis())
    }

    suspend fun getActiveCount(): Int {
        return expenseDao.getActiveExpenseCount()
    }

    suspend fun getAllExpensesRaw(): List<Expense> {
        return expenseDao.getAllExpensesRaw()
    }

    suspend fun getMaxNumericTransactionId(): Long? {
        return expenseDao.getMaxNumericTransactionId()
    }

    suspend fun getNextTransactionId(passedContext: Context? = null): String = idMutex.withLock {
        val targetContext = passedContext ?: context
        val prefs = targetContext?.getSharedPreferences("transaction_id_prefs", Context.MODE_PRIVATE)
        val storedMax = prefs?.getLong("max_numeric_id", 0L) ?: 0L
        val dbMax = expenseDao.getMaxNumericTransactionId() ?: 0L
        val next = maxOf(storedMax, dbMax) + 1L
        prefs?.edit()?.putLong("max_numeric_id", next)?.apply()
        setStartingTransactionId(next)
        next.toString()
    }

    suspend fun migrateLegacyTransactionIds(passedContext: Context? = null) {
        val targetContext = passedContext ?: context
        val prefs = targetContext?.getSharedPreferences("transaction_id_prefs", Context.MODE_PRIVATE)
        val all = expenseDao.getAllExpensesRaw()
        if (all.isEmpty()) return

        val existingNumericIds = all.mapNotNull { it.transactionId.toLongOrNull() }
            .filter { it > 0L }
            .toMutableSet()

        val needsMigration = all.any { exp ->
            exp.transactionId.isBlank() || exp.transactionId.toLongOrNull() == null || exp.transactionId.toLong() <= 0L
        }

        if (needsMigration) {
            var nextId = 1L
            for (exp in all) {
                val currentNumeric = exp.transactionId.toLongOrNull()
                if (currentNumeric != null && currentNumeric > 0L) {
                    continue
                }
                while (existingNumericIds.contains(nextId)) {
                    nextId++
                }
                val assignedId = nextId.toString()
                existingNumericIds.add(nextId)
                nextId++

                val updated = exp.copy(transactionId = assignedId, syncStatus = "PENDING")
                expenseDao.updateExpense(updated)
            }
        }

        val storedMax = prefs?.getLong("max_numeric_id", 0L) ?: 0L
        val currentMax = existingNumericIds.maxOrNull() ?: 0L
        val finalMax = maxOf(storedMax, currentMax)
        prefs?.edit()?.putLong("max_numeric_id", finalMax)?.apply()
        setStartingTransactionId(finalMax)
    }
}
