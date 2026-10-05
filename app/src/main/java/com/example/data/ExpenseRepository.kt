package com.example.data

import kotlinx.coroutines.flow.Flow

class ExpenseRepository(private val expenseDao: ExpenseDao) {

    val allExpenses: Flow<List<Expense>> = expenseDao.getAllExpenses()

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

    suspend fun deleteExpense(expense: Expense) {
        expenseDao.deleteExpense(expense)
    }

    suspend fun deleteExpenseById(id: Long) {
        expenseDao.deleteExpenseById(id)
    }

    suspend fun insertAll(expenses: List<Expense>) {
        expenseDao.insertAll(expenses)
    }

    suspend fun deleteAll() {
        expenseDao.deleteAllExpenses()
    }

    suspend fun getCount(): Int {
        return expenseDao.getExpenseCount()
    }
}
