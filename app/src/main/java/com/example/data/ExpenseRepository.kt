package com.example.data

import kotlinx.coroutines.flow.Flow

class ExpenseRepository(private val expenseDao: ExpenseDao) {

    val allExpenses: Flow<List<Expense>> = expenseDao.getAllExpenses()

    val trashExpenses: Flow<List<Expense>> = expenseDao.getTrashExpenses()

    val trashCount: Flow<Int> = expenseDao.getTrashCount()

    fun getExpensesByDate(date: String): Flow<List<Expense>> {
        return expenseDao.getExpensesByDate(date)
    }

    fun getExpensesByMonth(monthPrefix: String): Flow<List<Expense>> {
        return expenseDao.getExpensesByMonth(monthPrefix)
    }

    suspend fun getExpenseById(id: Long): Expense? {
        return expenseDao.getExpenseById(id)
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
}
